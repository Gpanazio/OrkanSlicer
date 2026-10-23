package ru.ytkab0bp.slicebeam.config

import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef.ConfigOptionType.*
import ru.ytkab0bp.slicebeam.utils.FilamentSlot

class TemporaryProjectSettingsTest {
    @Test fun projectPlateChoiceDoesNotOverwriteTheSavedPrinter() {
        val printer = ConfigObject().apply { put("curr_bed_type", "Cool Plate"); put("nozzle_diameter", "0.4") }
        TemporaryProjectSettings.activate(project().copy(process = mapOf("curr_bed_type" to "Textured PEI Plate")))
        assertEquals("Textured PEI Plate", ProfileSettingsRepository.valueFor(ConfigObject.PROFILE_LIST_PRINTER, printer, "curr_bed_type", true))
        ProfileSettingsRepository.setPendingValue(ConfigObject.PROFILE_LIST_PRINTER, "curr_bed_type", "Cool Plate", "High Temp Plate")
        ProfileSettingsRepository.commitDraft(ConfigObject.PROFILE_LIST_PRINTER, printer)
        assertEquals("Cool Plate", printer["curr_bed_type"])
        val effective = ConfigObject(printer)
        TemporaryProjectSettings.applyTo(effective)
        assertEquals("High Temp Plate", effective["curr_bed_type"])
        assertEquals("0.4", effective["nozzle_diameter"])
    }
    private val print = ConfigObject.PROFILE_LIST_PRINT
    private val filament = ConfigObject.PROFILE_LIST_FILAMENT
    private fun project() = ThreeMfProjectSettings("two-colors.3mf", mapOf("layer_height" to "0.24"), listOf(
        mapOf("filament_colour" to "#112233", "filament_type" to "PLA", "nozzle_temperature" to "210", "pressure_advance" to "0.04"),
        mapOf("filament_colour" to "#FFEEDD", "filament_type" to "PETG", "nozzle_temperature" to "235", "pressure_advance" to "0.06"),
    ), mapOf("filament_colour" to STRINGS, "filament_type" to STRINGS, "nozzle_temperature" to INTS,
        "pressure_advance" to FLOATS, "filament_start_gcode" to STRINGS, "filament_diameter" to FLOATS,
        "hot_plate_temp" to INTS, "cool_plate_temp" to INTS, "textured_plate_temp" to INTS))

    @Before fun setup() { TemporaryProjectSettings.clear(); ProfileSettingsRepository.clearAllDrafts() }
    @After fun cleanup() { TemporaryProjectSettings.clear(); ProfileSettingsRepository.clearAllDrafts() }

    @Test fun projectOverlayProducesPerFilamentVectorsWithoutChangingItsTargetPreset() {
        TemporaryProjectSettings.activate(project())
        val saved = ConfigObject("PLA").apply { put("nozzle_temperature", "200"); put("z_offset", "0.12") }
        val snapshot = ConfigObject(saved)
        TemporaryProjectSettings.applyTo(snapshot)
        assertEquals("210,235", snapshot["nozzle_temperature"])
        assertEquals("0.04,0.06", snapshot["pressure_advance"])
        assertEquals("#112233;#FFEEDD", snapshot["filament_colour"])
        assertEquals("PLA;PETG", snapshot["filament_type"])
        assertEquals("0.12", snapshot["z_offset"])
        assertEquals("200", saved["nozzle_temperature"])
    }

    @Test fun editsAndCommitAreProjectLocalAndOldDraftsReappearAfterClear() {
        val saved = ConfigObject("Own").apply { put("layer_height", "0.16") }
        ProfileSettingsRepository.setPendingValue(print, "layer_height", "0.16", "0.20")
        TemporaryProjectSettings.activate(project())
        ProfileSettingsRepository.setPendingValue(print, "layer_height", "0.24", "0.28")
        ProfileSettingsRepository.commitDraft(print, saved)
        assertEquals("0.16", saved["layer_height"])
        assertEquals("0.28", ProfileSettingsRepository.valueFor(print, saved, "layer_height", true))
        TemporaryProjectSettings.clear()
        assertEquals("0.20", ProfileSettingsRepository.valueFor(print, saved, "layer_height", true))
    }

    @Test fun selectedFilamentEditsOnlyItsOwnValuesAndStringsStayIntact() {
        TemporaryProjectSettings.activate(project())
        TemporaryProjectSettings.selectFilament(1)
        assertEquals("235", TemporaryProjectSettings.valueFor(filament, "nozzle_temperature", true))
        TemporaryProjectSettings.setValue(filament, "nozzle_temperature", "240")
        val macro = "; comment\nM118 \"PETG\""
        TemporaryProjectSettings.setValue(filament, "filament_start_gcode", macro)
        val target = ConfigObject().apply { put("filament_start_gcode", "\"\"") }
        TemporaryProjectSettings.applyTo(target)
        assertEquals("210,240", target["nozzle_temperature"])
        assertEquals(listOf("", macro), ProfileStringVectorCodec.parse(target["filament_start_gcode"]!!))
        TemporaryProjectSettings.selectFilament(0)
        assertEquals("210", TemporaryProjectSettings.valueFor(filament, "nozzle_temperature", true))
        assertTrue(TemporaryProjectSettings.edits(filament).isEmpty())
    }

    @Test fun missingFileValuesUseTheMatchingSavedVectorElement() {
        TemporaryProjectSettings.activate(project())
        TemporaryProjectSettings.selectFilament(1)
        val saved = ConfigObject().apply { put("filament_diameter", "1.75,2.85") }
        assertEquals("2.85", ProfileSettingsRepository.valueFor(filament, saved, "filament_diameter", true))
        saved.put("filament_diameter", "1.75")
        assertEquals("1.75", ProfileSettingsRepository.valueFor(filament, saved, "filament_diameter", true))
    }

    @Test fun nativeDefinitionIsNotMutatedWhenBuildingAScalarEditor() {
        TemporaryProjectSettings.activate(project())
        TemporaryProjectSettings.selectFilament(1)
        val native = ConfigOptionDef().apply { key = "nozzle_temperature"; type = INTS; defaultValue = "200,220"; min = 0f; label = "Temperature" }
        val ui = TemporaryProjectSettings.scalarDefinition(native)
        assertEquals(INT, ui.type)
        assertEquals("220", ui.defaultValue)
        assertEquals(INTS, native.type)
        assertEquals("200,220", native.defaultValue)
        assertEquals("Temperature", ui.label)
    }

    @Test fun removingFilamentKeepsRemainingValuesAndPhysicalGatesAligned() {
        TemporaryProjectSettings.activate(project())
        TemporaryProjectSettings.setSelectedGates("Printer", intArrayOf(0, 3))
        TemporaryProjectSettings.removeSelectedGate(0)
        TemporaryProjectSettings.removeFilament(0)
        val target = ConfigObject()
        TemporaryProjectSettings.applyTo(target)
        assertEquals("235", target["nozzle_temperature"])
        assertEquals("PETG", target["filament_type"])
        assertArrayEquals(intArrayOf(3), TemporaryProjectSettings.selectedGates("Printer", 1))
    }

    @Test fun resetRestoresFileValuesAndOneFilamentSliceTrimsUnusedVectors() {
        TemporaryProjectSettings.activate(project())
        TemporaryProjectSettings.setValue(filament, "nozzle_temperature", "250")
        TemporaryProjectSettings.resetEdits(filament)
        val target = ConfigObject()
        TemporaryProjectSettings.applyTo(target, 1)
        assertEquals("210", target["nozzle_temperature"])
        assertEquals("#112233", target["filament_colour"])
        assertEquals("PLA", target["filament_type"])
    }

    @Test fun slotAndInputCopiesCannotMutateTheSessionBehindItsBack() {
        val values = mutableMapOf("layer_height" to "0.24")
        TemporaryProjectSettings.activate(project().copy(process = values))
        values["layer_height"] = "0.9"
        TemporaryProjectSettings.slots()!![0].type = "ABS"
        val slots = listOf(FilamentSlot(0x123456, "TPU"))
        TemporaryProjectSettings.setSlots(slots)
        slots[0].type = "ABS"
        assertEquals("0.24", TemporaryProjectSettings.valueFor(print, "layer_height", true))
        assertEquals("TPU", TemporaryProjectSettings.slots()!!.single().type)
    }

    @Test fun bedEditorPreservesOtherSurfaceTemperatures() {
        TemporaryProjectSettings.activate(project())
        TemporaryProjectSettings.selectFilament(1)
        TemporaryProjectSettings.setValue(filament, "hot_plate_temp", "70")
        val target = ConfigObject().apply { put("hot_plate_temp", "60"); put("cool_plate_temp", "35"); put("textured_plate_temp", "55") }
        TemporaryProjectSettings.applyTo(target)
        assertEquals("60,70", target["hot_plate_temp"])
        assertEquals("35", target["cool_plate_temp"])
        assertEquals("55", target["textured_plate_temp"])
    }

    @Test fun clearingDropsProjectSlotsAndMappingsAndChangingSelectionDoesNotInvalidateSlices() {
        TemporaryProjectSettings.activate(project())
        val before = TemporaryProjectSettings.sliceRevision
        TemporaryProjectSettings.selectFilament(1)
        assertEquals(before, TemporaryProjectSettings.sliceRevision)
        TemporaryProjectSettings.setValue(filament, "pressure_advance", "0.1")
        assertTrue(TemporaryProjectSettings.sliceRevision > before)
        TemporaryProjectSettings.setSelectedGates("Printer", intArrayOf(0, 2))
        TemporaryProjectSettings.setSyncedPrinter("Printer")
        TemporaryProjectSettings.clear()
        assertFalse(TemporaryProjectSettings.active)
        assertNull(TemporaryProjectSettings.slots())
        assertNull(TemporaryProjectSettings.selectedGates("Printer", 1))
        assertNull(TemporaryProjectSettings.syncedPrinter)
    }

    @Test fun pickingSavedFilamentCopiesItWithoutImportingItsMetadataOrChangingTheOriginal() {
        TemporaryProjectSettings.activate(project())
        val saved = ConfigObject("ABS").apply {
            put("filament_type", "ABS"); put("nozzle_temperature", "250"); put("compatible_printers", "A;B"); put("print_host", "host")
        }
        TemporaryProjectSettings.useProfile(filament, saved)
        TemporaryProjectSettings.setValue(filament, "nozzle_temperature", "255")
        assertEquals("250", saved["nozzle_temperature"])
        assertEquals("ABS", TemporaryProjectSettings.slots()!![0].type)
        assertNull(TemporaryProjectSettings.valueFor(filament, "print_host", true))
        val target = ConfigObject()
        TemporaryProjectSettings.applyTo(target)
        assertEquals("255,235", target["nozzle_temperature"])
    }
}
