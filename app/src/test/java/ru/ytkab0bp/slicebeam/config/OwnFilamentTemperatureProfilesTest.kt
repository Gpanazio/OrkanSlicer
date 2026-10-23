package ru.ytkab0bp.slicebeam.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef.ConfigOptionType.INTS
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef.ConfigOptionType.STRINGS
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef.ConfigOptionType.FLOATS
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef.ConfigOptionType.BOOLS

class OwnFilamentTemperatureProfilesTest {
    private fun profile(name: String, material: String, nozzle: String, bed: String) = ConfigObject(name).apply {
        put("filament_type", material)
        put("nozzle_temperature", nozzle)
        put("hot_plate_temp", bed)
    }

    @Test fun staleAbsSlotMappingCannotSupplyPetgTemperatures() {
        val abs = profile("Anycubic ABS", "ABS", "205", "100")
        val petg = profile("Anycubic PETG", "PETG", "230", "70")
        assertEquals(petg, OwnFilamentTemperatureProfiles.choose(listOf(abs, petg), abs.title, abs.title, "PETG"))
        assertNull(OwnFilamentTemperatureProfiles.choose(listOf(abs), abs.title, abs.title, "PETG"))
    }

    @Test fun ownMaterialMatchingTemperaturesOverrideImportedValuesWithoutChangingColors() {
        val own = profile("Own PETG", "PETG", "240", "75")
        val imported = ThreeMfProjectSettings("part.3mf", emptyMap(), listOf(mapOf(
            "filament_type" to "PETG", "filament_colour" to "#FF0000", "nozzle_temperature" to "205",
            "hot_plate_temp" to "35",
        )), mapOf("filament_type" to STRINGS, "filament_colour" to STRINGS,
            "nozzle_temperature" to INTS, "hot_plate_temp" to INTS))
        try {
            TemporaryProjectSettings.activate(imported, listOf(own))
            val target = ConfigObject()
            TemporaryProjectSettings.applyTo(target)
            assertEquals("240", target["nozzle_temperature"])
            assertEquals("75", target["hot_plate_temp"])
            assertEquals("#FF0000", target["filament_colour"])
            assertEquals("PETG", target["filament_type"])
            assertEquals("240", TemporaryProjectSettings.valueFor(ConfigObject.PROFILE_LIST_FILAMENT, "nozzle_temperature", true))
        } finally {
            TemporaryProjectSettings.clear()
        }
    }

    @Test fun ownFilamentPressureAdvanceOverridesDisabledImportedProjectValue() {
        val own = profile("Own PETG", "PETG", "240", "75").apply {
            put("pressure_advance", "0.051")
            put("enable_pressure_advance", "1")
        }
        val imported = ThreeMfProjectSettings("part.3mf", emptyMap(), listOf(mapOf(
            "filament_type" to "PETG", "pressure_advance" to "0.02",
            "enable_pressure_advance" to "0",
        )), mapOf("filament_type" to STRINGS, "pressure_advance" to FLOATS,
            "enable_pressure_advance" to BOOLS))
        try {
            TemporaryProjectSettings.activate(imported, listOf(own))
            val target = ConfigObject()
            TemporaryProjectSettings.applyTo(target)
            assertEquals("0.051", target["pressure_advance"])
            assertEquals("1", target["enable_pressure_advance"])
        } finally {
            TemporaryProjectSettings.clear()
        }
    }
}
