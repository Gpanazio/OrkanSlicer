package ru.ytkab0bp.slicebeam.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef.ConfigOptionType
import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Reads Orca/Bambu JSON and Prusa/Orca INI settings locally, without importing saved presets. */
object ThreeMfProjectSettingsReader {
    private const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 24 * 1024 * 1024
    private const val MAX_SETTINGS_ENTRIES = 256

    fun read(
        file: File,
        definitions: Map<String, ConfigOptionDef>,
        processKeys: Set<String>,
        filamentKeys: Set<String>,
        installedProcesses: Map<String, Map<String, String>> = emptyMap(),
        installedFilaments: Map<String, Map<String, String>> = emptyMap(),
        preferredExtruderVariant: String? = null,
    ): ThreeMfProjectSettings {
        fun allowed(keys: Set<String>) = keys.map { ConfigObject.migrateKey(it) }.filter {
            !it.startsWith("compatible_") && it != "inherits" && it != "name" && !it.endsWith("_settings_id")
                && !it.startsWith("machine_") && it !in setOf("print_host", "host_type", "printhost_apikey", "printhost_webui",
                    "printhost_port", "nozzle_diameter", "printer_model", "printer_variant", "printer_technology",
                    "printable_area", "printable_height", "gcode_flavor", "z_offset")
        }.toSet()
        // Plate type is a temporary print-job choice, not an imported printer preset.
        val processAllowed = allowed(processKeys + "curr_bed_type")
        val filamentAllowed = allowed(filamentKeys)
        val flat = LinkedHashMap<String, List<String>>()
        val processProfiles = LinkedHashMap<String, Map<String, List<String>>>()
        val filamentProfiles = LinkedHashMap<String, Map<String, List<String>>>()
        var processId = ""
        var filamentIds = emptyList<String>()
        var ignored = 0
        val invalidValues = ArrayList<ProfileConfigLoadWarning.InvalidValue>()
        val missingProfiles = LinkedHashSet<String>()
        var totalBytes = 0L

        ZipFile(file).use { zip ->
            val entries = ArrayList<ZipEntry>()
            val names = HashSet<String>()
            for (entry in zip.entries().asSequence()) {
                if (entry.isDirectory || !isSettingsEntry(entry.name)) continue
                if (entry.size > MAX_ENTRY_BYTES) throw IOException("3MF settings entry exceeds the size limit")
                if (!names.add(entry.name)) throw IOException("3MF contains duplicate settings entries")
                entries.add(entry)
                if (entries.size > MAX_SETTINGS_ENTRIES) throw IOException("3MF contains too many settings entries")
            }
            for (entry in entries.sortedBy { it.name }) {
                val name = entry.name
                val flatEntry = name in setOf("Metadata/project_settings.config", "Metadata/Slic3r_PE.config", "Metadata/OrcaSlicer.config")
                val processEntry = name.startsWith("Metadata/process_settings_") || name.startsWith("Metadata/print_setting_")
                val filamentEntry = name.startsWith("Metadata/filament_settings_")
                val bytes = zip.getInputStream(entry).use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= MAX_ENTRY_BYTES) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
                totalBytes += bytes.size
                if (bytes.size > MAX_ENTRY_BYTES || totalBytes > MAX_TOTAL_BYTES) throw IOException("3MF settings exceed the size limit")
                try {
                    val text = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\r', '\n', '\t')
                    val values = if (text.startsWith('{')) jsonValues(JsonParser.parseString(text).asJsonObject)
                        else iniValues(text, definitions)
                    if (flatEntry) {
                        // The final project values are authoritative over inherited preset defaults.
                        flat.putAll(values)
                        values["print_settings_id"]?.firstOrNull()?.let { processId = it }
                        values["filament_settings_id"]?.let { filamentIds = it }
                    } else {
                        val id = values["name"]?.firstOrNull()
                            ?: values[if (processEntry) "print_settings_id" else "filament_settings_id"]?.firstOrNull()
                            ?: name.substringAfterLast('/').removeSuffix(".config")
                        if (processEntry) processProfiles[id] = values else filamentProfiles[id] = values
                    }
                } catch (_: RuntimeException) { ignored++ }
            }
        }

        fun resolve(id: String, profiles: Map<String, Map<String, List<String>>>, installed: Map<String, Map<String, String>>, seen: Set<String> = emptySet()): Map<String, List<String>> {
            if (id in seen) return emptyMap()
            val local = profiles[id]
            if (local == null) {
                val saved = installed[id]
                if (saved == null && id.isNotBlank()) missingProfiles.add(id)
                return saved?.mapValues { (key, value) -> TemporaryProjectSettings.splitValue(value, definitions[ConfigObject.migrateKey(key)]?.type) }.orEmpty()
            }
            val parent = local["inherits"]?.firstOrNull().orEmpty()
            return (if (parent.isNotEmpty()) resolve(parent, profiles, installed, seen + id) else emptyMap()) + local
        }

        val process = LinkedHashMap<String, String>()
        val selectedProcess = processId.ifBlank { processProfiles.keys.firstOrNull().orEmpty() }
        val processValues = resolve(selectedProcess, processProfiles, installedProcesses) + flat
        for ((rawKey, values) in processValues) {
            val key = ConfigObject.migrateKey(rawKey)
            if (key !in processAllowed || key in filamentAllowed || definitions[key] == null || values.isEmpty()) continue
            val config = ConfigObject().apply { put(rawKey, serialize(values, definitions[key]!!.type)) }
            process[key] = ThreeMfValueCompatibility.normalize(key, config[key] ?: continue)
        }

        val ids = filamentIds.ifEmpty { filamentProfiles.keys.sortedWith(compareBy { it.substringAfterLast('_').toIntOrNull() ?: Int.MAX_VALUE }) }
        val fileFilamentValues = flat.filter { ConfigObject.migrateKey(it.key) in filamentAllowed }
        val paletteCount = listOf("filament_colour", "filament_type", "filament_diameter")
            .maxOf { flat[it]?.size ?: 0 }
        // Nozzle-specific H2C vectors may be 6/9 entries for only three logical colors.
        val count = maxOf(ids.size, paletteCount).takeIf { it > 0 }
            ?: (fileFilamentValues.values.maxOfOrNull { it.size } ?: 0)
        val logicalCount = count.coerceAtMost(16)
        val selfIndices = flat["filament_self_index"].orEmpty()
        val variants = flat["filament_extruder_variant"].orEmpty()
        val filaments = (0 until logicalCount).map { index ->
            val inherited = ids.getOrNull(index)?.let { resolve(it, filamentProfiles, installedFilaments) }.orEmpty()
            val row = LinkedHashMap<String, String>()
            for ((rawKey, values) in inherited + fileFilamentValues) {
                val key = ConfigObject.migrateKey(rawKey)
                if (key !in filamentAllowed || definitions[key] == null || values.isEmpty()) continue
                val value = if (rawKey in fileFilamentValues) ThreeMfValueCompatibility.filamentValue(
                    values, index, logicalCount, selfIndices, variants, preferredExtruderVariant,
                ) else values.first()
                val config = ConfigObject().apply { put(rawKey, value) }
                row[key] = ThreeMfValueCompatibility.normalize(key, config[key] ?: continue)
            }
            row
        }
        val types = filamentAllowed.mapNotNull { key -> definitions[key]?.let { key to it.type } }.toMap()

        // Invalid values are omitted; geometry and remaining valid project settings can still load.
        fun sanitize(values: MutableMap<String, String>, filament: Boolean) {
            val config = ConfigObject()
            values.forEach { (key, value) -> config.put(key, if (filament && types[key] == ConfigOptionType.STRINGS) ProfileStringVectorCodec.serialize(listOf(value)) else value) }
            val invalid = ProfileConfigImportValidator.validate(listOf(config), definitions)
            invalidValues.addAll(invalid)
            invalid.forEach { values.remove(it.key) }
            ignored += invalid.size
        }
        sanitize(process, false)
        filaments.forEach { sanitize(it, true) }
        // Legacy projects and the app expose one general bed temperature. Supply it for
        // missing surface fields so the active printer's plate cannot use an old preset value.
        // Explicit surface temperatures present in the 3MF remain intact.
        filaments.forEach { row ->
            for (suffix in listOf("", "_initial_layer")) {
                val general = row["hot_plate_temp$suffix"] ?: continue
                for (surface in listOf("cool_plate", "eng_plate", "textured_plate", "textured_cool_plate", "supertack_plate")) {
                    val key = "${surface}_temp$suffix"
                    if (key in types && key !in row) row[key] = general
                }
            }
        }
        return ThreeMfProjectSettings(file.name, process, filaments, types, selectedProcess, ids, ignored, invalidValues,
            missingProfiles.toList())
    }

    private fun isSettingsEntry(name: String): Boolean = name.endsWith(".config") && (
        name in setOf("Metadata/project_settings.config", "Metadata/Slic3r_PE.config", "Metadata/OrcaSlicer.config") ||
            name.startsWith("Metadata/process_settings_") || name.startsWith("Metadata/print_setting_") ||
            name.startsWith("Metadata/filament_settings_")
        )

    private fun jsonValues(obj: JsonObject): Map<String, List<String>> = obj.entrySet().mapNotNull { (rawKey, element) ->
        val values = when {
            element.isJsonArray -> element.asJsonArray.mapNotNull(::primitive)
            element.isJsonPrimitive -> listOfNotNull(primitive(element))
            else -> emptyList()
        }
        if (values.isEmpty()) null else rawKey to values
    }.toMap()

    private fun primitive(value: JsonElement): String? = if (!value.isJsonPrimitive) null
        else if (value.asJsonPrimitive.isBoolean) if (value.asBoolean) "1" else "0" else value.asString

    private fun iniValues(text: String, definitions: Map<String, ConfigOptionDef>): Map<String, List<String>> {
        val result = LinkedHashMap<String, List<String>>()
        for (line in text.lineSequence()) {
            if (line.trimStart().startsWith('#') || line.trimStart().startsWith(';')) continue
            val separator = line.indexOf('=')
            if (separator < 1) continue
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            val type = definitions[ConfigObject.migrateKey(key)]?.type
            result[key] = TemporaryProjectSettings.splitValue(value, type)
        }
        return result
    }

    private fun serialize(values: List<String>, type: ConfigOptionType): String = when {
        type == ConfigOptionType.STRINGS -> ProfileStringVectorCodec.serialize(values)
        type.list || type == ConfigOptionType.ENUMS -> values.joinToString(",")
        else -> values.first()
    }
}
