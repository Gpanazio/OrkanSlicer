package ru.ytkab0bp.slicebeam.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef.ConfigOptionType
import ru.ytkab0bp.slicebeam.utils.FilamentSlot

data class ThreeMfProjectSettings(
    val sourceName: String,
    val process: Map<String, String>,
    val filaments: List<Map<String, String>>,
    val filamentTypes: Map<String, ConfigOptionType>,
    val processTitle: String = "",
    val filamentTitles: List<String> = emptyList(),
    val ignoredValues: Int = 0,
    val invalidValues: List<ProfileConfigLoadWarning.InvalidValue> = emptyList(),
    val missingProfileIds: List<String> = emptyList(),
) {
    val hasSettings: Boolean get() = process.isNotEmpty() || filaments.any { it.isNotEmpty() }
}

data class TemporaryProjectState(
    val sourceName: String = "",
    val selectedFilament: Int = 0,
    val filamentCount: Int = 0,
    val revision: Long = 0,
    val generation: Long = 0,
) { val active: Boolean get() = sourceName.isNotEmpty() }

/** Project-local overrides. No file, preset object or SharedPreferences is ever written here. */
object TemporaryProjectSettings {
    @Volatile private var data: ThreeMfProjectSettings? = null
    private val processEdits = LinkedHashMap<String, String>()
    private val filamentEdits = ArrayList<MutableMap<String, String>>()
    private var palette: List<FilamentSlot>? = null
    private val gateChoices = HashMap<String, IntArray>()
    var syncedPrinter: String? = null
        private set
    private val state = MutableStateFlow(TemporaryProjectState())
    val uiState = state.asStateFlow()
    var onChanged: ((Boolean) -> Unit)? = null
    @Volatile var sliceRevision: Long = 0
        private set
    val active: Boolean get() = data != null

    fun owns(profileType: Int): Boolean = active && profileType in listOf(
        ConfigObject.PROFILE_LIST_PRINT, ConfigObject.PROFILE_LIST_FILAMENT,
    )
    fun ownsKey(profileType: Int, key: String): Boolean = owns(profileType) ||
        (active && profileType == ConfigObject.PROFILE_LIST_PRINTER && key == "curr_bed_type")

    @Synchronized fun activate(settings: ThreeMfProjectSettings?, ownTemperatureProfiles: List<ConfigObject?> = emptyList()) {
        data = settings?.takeIf { it.hasSettings }?.let { source -> source.copy(
            process = LinkedHashMap(source.process), filaments = source.filaments.mapIndexed { index, imported ->
                LinkedHashMap(imported).apply {
                    ownTemperatureProfiles.getOrNull(index)?.values?.forEach { (key, raw) ->
                        if (key in source.filamentTypes && OwnFilamentTemperatureProfiles.isOwnFilamentOverrideKey(key)) {
                            val scalar = splitValue(raw, source.filamentTypes[key]).firstOrNull()
                            if (!scalar.isNullOrBlank()) put(key, scalar)
                        }
                    }
                }
            },
            filamentTypes = LinkedHashMap(source.filamentTypes), filamentTitles = source.filamentTitles.toList(),
        ) }
        processEdits.clear()
        filamentEdits.clear()
        data?.filaments?.forEach { _ -> filamentEdits.add(LinkedHashMap()) }
        palette = data?.filaments?.takeIf { it.isNotEmpty() }?.map { values ->
            FilamentSlot(parseColor(values["filament_colour"]), values["filament_type"] ?: "PLA")
        }
        gateChoices.clear()
        syncedPrinter = null
        state.value = TemporaryProjectState(data?.sourceName.orEmpty(), 0, palette?.size ?: 0,
            state.value.revision + 1, state.value.generation + 1)
        changed()
    }

    fun clear() { if (active) activate(null) }

    private fun changed(settingsChanged: Boolean = true) {
        if (settingsChanged) sliceRevision++
        state.value = state.value.copy(revision = state.value.revision + 1, filamentCount = palette?.size ?: 0)
        onChanged?.invoke(settingsChanged)
    }

    @Synchronized fun selectFilament(index: Int) {
        if (index !in 0 until (palette?.size ?: 0) || index == state.value.selectedFilament) return
        state.value = state.value.copy(selectedFilament = index)
        changed(false)
    }

    @Synchronized fun slots(): MutableList<FilamentSlot>? = palette?.map { FilamentSlot(it.color, it.type) }?.toMutableList()

    @Synchronized fun setSlots(slots: List<FilamentSlot>) {
        if (!active || slots.isEmpty()) return
        palette = slots.take(16).map { FilamentSlot(it.color, it.type) }
        while (filamentEdits.size < palette!!.size) filamentEdits.add(LinkedHashMap())
        while (filamentEdits.size > palette!!.size) filamentEdits.removeAt(filamentEdits.lastIndex)
        palette!!.forEachIndexed { index, slot ->
            filamentEdits[index]["filament_colour"] = colorText(slot.color)
            filamentEdits[index]["filament_type"] = slot.type ?: "PLA"
        }
        state.value = state.value.copy(selectedFilament = state.value.selectedFilament.coerceAtMost(palette!!.lastIndex))
        changed()
    }

    @Synchronized fun valueFor(profileType: Int, key: String, includeEdits: Boolean): String? {
        val project = data ?: return null
        return when (profileType) {
            ConfigObject.PROFILE_LIST_PRINTER -> if (key == "curr_bed_type")
                (if (includeEdits) processEdits[key] ?: project.process[key] else project.process[key]) else null
            ConfigObject.PROFILE_LIST_PRINT -> if (includeEdits) processEdits[key] ?: project.process[key] else project.process[key]
            ConfigObject.PROFILE_LIST_FILAMENT -> {
                val index = state.value.selectedFilament
                val value = if (includeEdits) filamentEdits.getOrNull(index)?.get(key) ?: project.filaments.getOrNull(index)?.get(key)
                    else project.filaments.getOrNull(index)?.get(key)
                value
            }
            else -> null
        }
    }

    @Synchronized fun setValue(profileType: Int, key: String, edited: String) {
        val project = data ?: return
        if (profileType == ConfigObject.PROFILE_LIST_PRINT ||
            (profileType == ConfigObject.PROFILE_LIST_PRINTER && key == "curr_bed_type")) processEdits[key] = edited
        else if (profileType == ConfigObject.PROFILE_LIST_FILAMENT) {
            val index = state.value.selectedFilament
            while (filamentEdits.size <= index) filamentEdits.add(LinkedHashMap())
            val value = edited
            filamentEdits[index][key] = value
            if (key == "pressure_advance" && value.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true) {
                filamentEdits[index]["enable_pressure_advance"] = "1"
            }
            if (key == "filament_colour" || key == "filament_type") {
                val slots = slots() ?: mutableListOf(FilamentSlot(0x1AC5A2, "PLA"))
                val slot = slots[index.coerceAtMost(slots.lastIndex)]
                if (key == "filament_colour") slot.color = parseColor(value) else slot.type = value
                palette = slots
            }
        }
        changed()
    }

    @Synchronized fun edits(profileType: Int): Map<String, String> = when (profileType) {
        ConfigObject.PROFILE_LIST_PRINT -> LinkedHashMap(processEdits)
        ConfigObject.PROFILE_LIST_FILAMENT -> LinkedHashMap(filamentEdits.getOrNull(state.value.selectedFilament).orEmpty())
        else -> emptyMap()
    }

    @Synchronized fun resetEdits(profileType: Int) {
        if (profileType == ConfigObject.PROFILE_LIST_PRINT) processEdits.clear()
        if (profileType == ConfigObject.PROFILE_LIST_FILAMENT) {
            filamentEdits.getOrNull(state.value.selectedFilament)?.clear()
            palette = data?.filaments?.mapIndexed { index, values ->
                val changes = filamentEdits.getOrNull(index).orEmpty()
                FilamentSlot(parseColor(changes["filament_colour"] ?: values["filament_colour"]),
                    changes["filament_type"] ?: values["filament_type"] ?: "PLA")
            }?.takeIf { it.isNotEmpty() }
        }
        changed()
    }

    /** Picking an existing preset uses a copy for this project; saved selections stay intact. */
    @Synchronized fun useProfile(profileType: Int, profile: ConfigObject) {
        val project = data ?: return
        if (profileType == ConfigObject.PROFILE_LIST_PRINT) {
            data = project.copy(process = LinkedHashMap(profile.values + project.process.filterKeys { it == "curr_bed_type" }), processTitle = profile.title.orEmpty())
            processEdits.clear()
        } else if (profileType == ConfigObject.PROFILE_LIST_FILAMENT) {
            val index = state.value.selectedFilament
            val rows = project.filaments.map { LinkedHashMap(it) }.toMutableList()
            while (rows.size <= index) rows.add(LinkedHashMap())
            rows[index] = LinkedHashMap(profile.values.filterKeys { it in project.filamentTypes }.mapValues { (key, value) ->
                splitValue(value, project.filamentTypes[key]).firstOrNull().orEmpty()
            })
            data = project.copy(filaments = rows)
            filamentEdits.getOrNull(index)?.clear()
            palette = palette?.mapIndexed { slot, previous -> if (slot == index)
                FilamentSlot(rows[index]["filament_colour"]?.let(::parseColor) ?: previous.color,
                    rows[index]["filament_type"] ?: previous.type) else previous }
        }
        changed()
    }

    fun physicalPrinterChanged() { if (active) changed() }

    @Synchronized fun scalarForUi(key: String, value: String?): String? {
        if (value == null) return null
        val parts = splitValue(value, data?.filamentTypes?.get(key))
        return parts.getOrNull(state.value.selectedFilament) ?: parts.lastOrNull()
    }

    fun scalarDefinition(definition: ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef): ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef {
        val copy = ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef()
        ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef::class.java.fields.forEach { field ->
            if (!java.lang.reflect.Modifier.isStatic(field.modifiers)) field.set(copy, field.get(definition))
        }
        copy.type = when (definition.type) {
            ConfigOptionType.FLOATS -> ConfigOptionType.FLOAT
            ConfigOptionType.INTS -> ConfigOptionType.INT
            ConfigOptionType.BOOLS -> if (definition.nullable) ConfigOptionType.ENUM else ConfigOptionType.BOOL
            ConfigOptionType.STRINGS -> ConfigOptionType.STRING
            ConfigOptionType.PERCENTS -> ConfigOptionType.PERCENT
            ConfigOptionType.FLOATS_OR_PERCENTS -> ConfigOptionType.FLOAT_OR_PERCENT
            ConfigOptionType.POINTS -> ConfigOptionType.POINT
            ConfigOptionType.ENUMS -> ConfigOptionType.ENUM
            else -> definition.type
        }
        copy.defaultValue = scalarForUi(definition.key, definition.defaultValue)
        if (definition.type == ConfigOptionType.BOOLS && definition.nullable) {
            copy.enumValues = arrayOf("nil", "0", "1")
            copy.enumLabels = arrayOf("Use printer settings", "Disabled", "Enabled")
        }
        return copy
    }

    @Synchronized fun applyTo(target: ConfigObject, activeFilamentCount: Int = -1) {
        val project = data ?: return
        project.process.forEach(target::put)
        processEdits.forEach(target::put)
        val count = if (activeFilamentCount > 0) activeFilamentCount.coerceIn(1, 16)
            else (palette?.size ?: project.filaments.size).coerceIn(1, 16)
        val keys = project.filaments.flatMap { it.keys }.toSet() + filamentEdits.flatMap { it.keys }
        for (key in keys) {
            val type = project.filamentTypes[key]
            val fallback = splitValue(target[key].orEmpty(), type)
            val values = (0 until count).map { index ->
                filamentEdits.getOrNull(index)?.get(key) ?: project.filaments.getOrNull(index)?.get(key)
                    ?: fallback.getOrNull(index) ?: fallback.lastOrNull().orEmpty()
            }
            val serialized = when {
                type == ConfigOptionType.STRINGS -> ProfileStringVectorCodec.serialize(values)
                type?.list == true || type == ConfigOptionType.ENUMS -> values.joinToString(",")
                else -> values.first()
            }
            target.put(key, serialized)
        }
        palette?.let { slots ->
            target.put("filament_colour", ProfileStringVectorCodec.serialize((0 until count).map { colorText(slots.getOrElse(it) { slots.last() }.color) }))
            target.put("filament_type", ProfileStringVectorCodec.serialize((0 until count).map { slots.getOrElse(it) { slots.last() }.type ?: "PLA" }))
        }
    }

    @Synchronized fun applyProfileTo(profileType: Int, target: ConfigObject) {
        val project = data ?: return
        if (profileType == ConfigObject.PROFILE_LIST_PRINT) {
            project.process.forEach(target::put)
            processEdits.forEach(target::put)
        } else if (profileType == ConfigObject.PROFILE_LIST_FILAMENT) {
            val index = state.value.selectedFilament
            (project.filaments.getOrNull(index).orEmpty() + filamentEdits.getOrNull(index).orEmpty()).keys.forEach { key ->
                valueFor(profileType, key, true)?.let { target.put(key, it) }
            }
        }
    }

    @Synchronized fun selectedGates(printer: String, count: Int): IntArray? = gateChoices[printer]?.takeIf { it.size >= count }?.copyOf(count)
    @Synchronized fun setSelectedGates(printer: String, gates: IntArray) { gateChoices[printer] = gates.copyOf() }
    @Synchronized fun removeSelectedGate(index: Int) {
        gateChoices.keys.toList().forEach { printer ->
            gateChoices[printer] = gateChoices.getValue(printer).filterIndexed { tool, _ -> tool != index }.toIntArray()
        }
    }
    fun setSyncedPrinter(printer: String?) { syncedPrinter = printer }

    @Synchronized fun removeFilament(index: Int) {
        val project = data ?: return
        val rows = project.filaments.toMutableList()
        if (index in rows.indices) rows.removeAt(index)
        if (index in filamentEdits.indices) filamentEdits.removeAt(index)
        palette = palette?.filterIndexed { slot, _ -> slot != index }
        data = project.copy(filaments = rows)
        state.value = state.value.copy(selectedFilament = state.value.selectedFilament.coerceAtMost((palette?.lastIndex ?: 0).coerceAtLeast(0)))
        changed()
    }

    internal fun splitValue(value: String, type: ConfigOptionType?): List<String> = when {
        type == ConfigOptionType.STRINGS -> ProfileStringVectorCodec.parse(value) ?: listOf(value)
        type?.list == true || type == ConfigOptionType.ENUMS -> value.split(',').map(String::trim)
        else -> listOf(value)
    }
    internal fun colorText(color: Int): String = "#%06X".format(java.util.Locale.ROOT, color and 0xFFFFFF)
    internal fun parseColor(raw: String?): Int = raw?.trim()?.removePrefix("#")?.take(6)?.toIntOrNull(16)
        ?.or(0xFF000000.toInt()) ?: 0xFF1AC5A2.toInt()
}
