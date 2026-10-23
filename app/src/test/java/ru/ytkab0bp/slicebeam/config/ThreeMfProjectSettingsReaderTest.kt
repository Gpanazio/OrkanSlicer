package ru.ytkab0bp.slicebeam.config

import org.junit.Assert.*
import org.junit.Test
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef.ConfigOptionType.*
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ThreeMfProjectSettingsReaderTest {
    private val definitions = listOf(
        option("layer_height", FLOAT, .01f, 1f), option("wall_loops", INT, 0f, 100f),
        option("sparse_infill_density", PERCENT, 0f, 100f), option("enable_support", BOOL),
        option("nozzle_temperature", INTS, 0f, 400f), option("hot_plate_temp", INTS, 0f, 150f),
        option("cool_plate_temp", INTS, 0f, 150f), option("textured_plate_temp", INTS, 0f, 150f),
        option("pressure_advance", FLOATS, 0f, 2f), option("enable_pressure_advance", BOOLS),
        option("filament_colour", STRINGS), option("filament_type", STRINGS), option("filament_start_gcode", STRINGS),
        option("print_host", STRING), option("host_type", STRING), option("printhost_apikey", STRING), option("printhost_webui", STRING), option("nozzle_diameter", FLOATS), option("z_offset", FLOAT),
        option("machine_start_gcode", STRING), option("compatible_printers", STRINGS),
    ).associateBy { it.key }
    private val filamentKeys = setOf("nozzle_temperature", "hot_plate_temp", "cool_plate_temp", "textured_plate_temp", "pressure_advance", "enable_pressure_advance",
        "filament_colour", "filament_type", "filament_start_gcode", "compatible_printers")
    private val processKeys = definitions.keys - filamentKeys

    private fun read(entries: Map<String, String>, processes: Map<String, Map<String, String>> = emptyMap(),
                     filaments: Map<String, Map<String, String>> = emptyMap()): ThreeMfProjectSettings {
        val file = File.createTempFile("project-", ".3mf")
        try {
            ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (path, text) ->
                zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
            } }
            return ThreeMfProjectSettingsReader.read(file, definitions, processKeys, filamentKeys, processes, filaments)
        } finally { file.delete() }
    }

    private fun fixture(moduleRelative: String, repoRelative: String): File =
        File(moduleRelative).takeIf(File::isFile) ?: File(repoRelative).also {
            check(it.isFile) { "Missing 3MF test fixture: $repoRelative" }
        }

    @Test fun bundledTwoColorProjectKeepsMaterialsButNotMachineOverrides() {
        val project = ThreeMfProjectSettingsReader.read(
            fixture("src/androidTest/assets/models/two-color-project.3mf",
                "app/src/androidTest/assets/models/two-color-project.3mf"),
            definitions, processKeys, filamentKeys,
        )
        assertEquals("0.24", project.process["layer_height"])
        assertTrue(project.missingProfileIds.isEmpty())
        assertEquals(listOf("#112233", "#FFEEDD"), project.filaments.map { it["filament_colour"] })
        assertEquals(listOf("PLA", "PETG"), project.filaments.map { it["filament_type"] })
        assertEquals(listOf("211", "236"), project.filaments.map { it["nozzle_temperature"] })
        assertFalse(project.process.keys.any { it in setOf("print_host", "z_offset", "nozzle_diameter", "machine_start_gcode") })
    }

    @Test fun realOrcaBenchyProjectKeepsThreeLogicalFilaments() {
        val project = ThreeMfProjectSettingsReader.read(
            fixture("../test_3mf/3DBenchy.3mf", "test_3mf/3DBenchy.3mf"),
            definitions, processKeys, filamentKeys,
        )
        assertEquals("0.2", project.process["layer_height"])
        assertTrue(project.missingProfileIds.isEmpty())
        assertEquals(listOf("#26A69A", "#A4C41E", "#00C1AE"),
            project.filaments.map { it["filament_colour"] })
        assertEquals(listOf("PLA", "PLA", "PLA"), project.filaments.map { it["filament_type"] })
        assertFalse(project.process.containsKey("z_offset"))
    }

    @Test fun threeColorsAndSixNozzleVariantsDoNotCreateExtraFilaments() {
        val result = read(mapOf("Metadata/project_settings.config" to """{
            "filament_colour":["#112233","#445566","#778899"],"filament_type":["PLA","PLA","PLA"],
            "filament_settings_id":["Same preset","Same preset","Same preset"],
            "filament_self_index":["1","1","2","2","3","3"],
            "nozzle_temperature":["210","220","230","240","250","260"] }"""))
        assertEquals(3, result.filaments.size)
        assertEquals(listOf("210","230","250"), result.filaments.map { it["nozzle_temperature"] })
    }

    @Test fun modernJsonKeepsDifferentValuesForTwoFilamentsAndIgnoresPrinterData() {
        val result = read(mapOf("Metadata/project_settings.config" to """{
            "layer_height":"0.24", "wall_loops":"3", "sparse_infill_density":"25%", "enable_support":true,
            "filament_colour":["#112233","#FFEEDD"], "filament_type":["PLA","PETG"],
            "nozzle_temperature":["210","235"], "pressure_advance":["0.04","0.06"],
            "enable_pressure_advance":[true,false], "print_host":"untrusted.local", "z_offset":"4",
            "nozzle_diameter":["0.8"], "machine_start_gcode":"BAD",
            "compatible_printers":["A","B","C","D"] }"""))
        assertEquals("0.24", result.process["layer_height"])
        assertEquals("1", result.process["enable_support"])
        assertEquals(2, result.filaments.size)
        assertEquals("235", result.filaments[1]["nozzle_temperature"])
        assertEquals("0.04", result.filaments[0]["pressure_advance"])
        assertEquals("0", result.filaments[1]["enable_pressure_advance"])
        assertFalse(result.process.keys.any { it in setOf("print_host", "z_offset", "nozzle_diameter", "machine_start_gcode") })
        assertEquals(0, result.ignoredValues)
    }

    @Test fun legacyIniMigratesAliasesAndNativeVectors() {
        val result = read(mapOf("Metadata/Slic3r_PE.config" to """
            # old Prusa/Orca project
            perimeters = 4
            fill_density = 35%
            temperature = 211,236
            bed_temperature = 61,72
            filament_colour = #123456;#ABCDEF
            filament_type = PLA;PETG
            pressure_advance = 0.03,0.07
            start_gcode = BAD
        """.trimIndent()))
        assertEquals("4", result.process["wall_loops"])
        assertEquals("35%", result.process["sparse_infill_density"])
        assertEquals("236", result.filaments[1]["nozzle_temperature"])
        assertEquals("72", result.filaments[1]["hot_plate_temp"])
        assertNull(result.process["machine_start_gcode"])
    }

    @Test fun generalBedTemperaturesFillMissingSurfacesAndPreserveExplicitProjectValues() {
        val result = read(mapOf("Metadata/project_settings.config" to """{
            "filament_type":["PLA","PETG"], "hot_plate_temp":["61","72"], "textured_plate_temp":["64","75"] }"""))
        assertEquals("61", result.filaments[0]["cool_plate_temp"])
        assertEquals("72", result.filaments[1]["cool_plate_temp"])
        assertEquals("64", result.filaments[0]["textured_plate_temp"])
        assertEquals("75", result.filaments[1]["textured_plate_temp"])
    }

    @Test fun stringMacrosPreserveSemicolonsQuotesAndNewlines() {
        val macros = listOf("; first\nM118 \"hello\"", "; second\nM118 world")
        val result = read(mapOf("Metadata/Slic3r_PE.config" to
            "filament_start_gcode = ${ProfileStringVectorCodec.serialize(macros)}\nfilament_type = PLA;PETG"))
        assertEquals(macros, result.filaments.map { it["filament_start_gcode"] })
    }

    @Test fun inheritedPresetsAreResolvedLocallyAndProjectValuesWin() {
        val result = read(mapOf(
            "Metadata/process_settings_1.config" to """{"name":"Child","inherits":"Parent","wall_loops":"4"}""",
            "Metadata/filament_settings_1.config" to """{"name":"Blue","inherits":"Base PLA","nozzle_temperature":["220"]}""",
            "Metadata/filament_settings_2.config" to """{"name":"Red","inherits":"Base PLA","nozzle_temperature":["230"]}""",
            "Metadata/project_settings.config" to """{"print_settings_id":"Child","filament_settings_id":["Blue","Red"],"layer_height":"0.28"}""",
        ), mapOf("Parent" to mapOf("layer_height" to "0.16", "wall_loops" to "2")),
            mapOf("Base PLA" to mapOf("filament_type" to "PLA", "filament_colour" to "#000000", "pressure_advance" to "0.02")))
        assertEquals("0.28", result.process["layer_height"])
        assertEquals("4", result.process["wall_loops"])
        assertEquals(listOf("220", "230"), result.filaments.map { it["nozzle_temperature"] })
        assertEquals("0.02", result.filaments[1]["pressure_advance"])
        assertTrue(result.missingProfileIds.isEmpty())
    }

    @Test fun inheritanceCyclesAndAbsentParentsStillKeepLocalValues() {
        val result = read(mapOf(
            "Metadata/process_settings_1.config" to """{"name":"A","inherits":"B","layer_height":"0.2"}""",
            "Metadata/process_settings_2.config" to """{"name":"B","inherits":"A","wall_loops":"3"}""",
        ))
        assertEquals("0.2", result.process["layer_height"])
        assertEquals("3", result.process["wall_loops"])
        assertTrue(result.missingProfileIds.isEmpty())
    }

    @Test fun embeddedOverridesDoNotWarnAboutUnavailableBaseProfiles() {
        val result = read(mapOf(
            "Metadata/process_settings_1.config" to """{"name":"Child","inherits":"Absent process","wall_loops":"4"}""",
            "Metadata/filament_settings_1.config" to """{"name":"Blue","inherits":"Absent PLA","nozzle_temperature":["220"]}""",
            "Metadata/project_settings.config" to """{"print_settings_id":"Child","filament_settings_id":["Blue"],"layer_height":"0.28"}""",
        ))
        assertTrue(result.missingProfileIds.isEmpty())
        assertEquals("4", result.process["wall_loops"])
        assertEquals("220", result.filaments.single()["nozzle_temperature"])
    }

    @Test fun flattenedBambuOrcaSettingsDoNotRequireInstalledPresetNames() {
        val result = read(mapOf("Metadata/project_settings.config" to """{
            "print_settings_id":"0.20mm Standard @BBL X1C", "layer_height":"0.2",
            "filament_settings_id":["Bambu PLA Basic @BBL X1C"], "nozzle_temperature":["220"] }"""))
        assertTrue(result.missingProfileIds.isEmpty())
        assertEquals("0.2", result.process["layer_height"])
        assertEquals("220", result.filaments.single()["nozzle_temperature"])
    }

    @Test fun trulyMissingBaseWithNoEmbeddedSettingsStillWarns() {
        val result = read(mapOf(
            "Metadata/project_settings.config" to """{"print_settings_id":"Child"}""",
            "Metadata/process_settings_1.config" to """{"name":"Child","inherits":"Missing base"}""",
        ))
        assertEquals(listOf("Missing base"), result.missingProfileIds)
        assertFalse(result.hasSettings)
    }

    @Test fun plateCountFailureDoesNotSkipProjectSettingsImport() {
        val xml = "<config>" + "<plate />".repeat(37) + "</config>"
        val result = ru.ytkab0bp.slicebeam.ThreeMfImportStages.read(
            countPlates = { ru.ytkab0bp.slicebeam.ThreeMfPlateCountDetector.detect(xml, emptyList()) },
            readSettings = { read(mapOf(
                "Metadata/model_settings.config" to xml,
                "Metadata/project_settings.config" to """{"layer_height":"0.28","nozzle_temperature":["220"]}""",
            )) },
        )
        assertEquals(1, result.plateCount)
        assertNotNull(result.plateCountError)
        assertNull(result.settingsError)
        assertEquals("0.28", result.settings!!.process["layer_height"])
        assertEquals("220", result.settings.filaments.single()["nozzle_temperature"])
    }

    @Test fun settingsFailureDoesNotChangeValidPlateCount() {
        val failure = IOException("bad settings")
        val result = ru.ytkab0bp.slicebeam.ThreeMfImportStages.read(
            countPlates = { 36 }, readSettings = { throw failure },
        )
        assertEquals(36, result.plateCount)
        assertNull(result.plateCountError)
        assertSame(failure, result.settingsError)
    }

    @Test fun missingOnlyProfileReferenceIsStillReported() {
        val result = read(mapOf("Metadata/project_settings.config" to
            """{"print_settings_id":"Unavailable preset"}"""))
        assertFalse(result.hasSettings)
        assertEquals(listOf("Unavailable preset"), result.missingProfileIds)
    }

    @Test fun invalidNumericValuesAreDroppedWithoutLosingValidSettings() {
        val result = read(mapOf("Metadata/project_settings.config" to """{
            "layer_height":"oops", "wall_loops":"3", "filament_type":["PLA","PETG"],
            "nozzle_temperature":["210","999"], "pressure_advance":["0.04","NaN"] }"""))
        assertNull(result.process["layer_height"])
        assertEquals("3", result.process["wall_loops"])
        assertNull(result.filaments[1]["nozzle_temperature"])
        assertNull(result.filaments[1]["pressure_advance"])
        assertEquals(3, result.ignoredValues)
    }

    @Test fun malformedMetadataAndGeometryOnlyFilesDoNotCreateAnEmptyProject() {
        val geometryOnly = read(mapOf("3D/3dmodel.model" to "<model/>"))
        assertFalse(geometryOnly.hasSettings)
        val malformed = read(mapOf("Metadata/project_settings.config" to "{broken"))
        assertFalse(malformed.hasSettings)
        assertEquals(1, malformed.ignoredValues)
    }

    @Test(expected = IOException::class) fun oversizedSettingsAreBoundedEvenWhenHighlyCompressed() {
        read(mapOf("Metadata/project_settings.config" to " ".repeat(8 * 1024 * 1024 + 1)))
    }

    @Test fun manyUnrelated3mfAssetsDoNotCountAsSettings() {
        val entries = LinkedHashMap<String, String>()
        repeat(300) { entries["Metadata/plate_${it + 1}.png"] = "thumbnail" }
        entries["Metadata/project_settings.config"] = """{"layer_height":"0.24"}"""
        assertEquals("0.24", read(entries).process["layer_height"])
    }

    @Test(expected = IOException::class) fun excessiveSettingsEntriesAreRejectedBeforeParsing() {
        val entries = (0..256).associate { index ->
            "Metadata/filament_settings_$index.config" to """{"name":"Filament $index"}"""
        }
        read(entries)
    }

    private fun option(key: String, type: ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef.ConfigOptionType,
                       min: Float = Float.MIN_VALUE, max: Float = Float.MAX_VALUE) = ConfigOptionDef().apply {
        this.key = key; this.type = type; this.min = min; this.max = max
    }
}
