package ru.ytkab0bp.slicebeam.config

import org.junit.Assert.assertEquals
import org.junit.Test

class BedSurfaceTemperaturePolicyTest {
    @Test
    fun mapsEveryOrcaPlateNameToItsNativeTemperaturePair() {
        val expected = mapOf(
            "Cool Plate" to "cool_plate",
            "Engineering Plate" to "eng_plate",
            "High Temp Plate" to "hot_plate",
            "Textured PEI Plate" to "textured_plate",
            "Textured Cool Plate" to "textured_cool_plate",
            "Supertack Plate" to "supertack_plate",
        )

        expected.forEach { (name, prefix) ->
            assertEquals(
                BedSurfaceTemperaturePolicy.Keys("${prefix}_temp_initial_layer", "${prefix}_temp"),
                BedSurfaceTemperaturePolicy.keysFor(name),
            )
        }
    }

    @Test
    fun mapsNativeEnumValuesAndUsesOrkanPeiFallback() {
        assertEquals("cool_plate_temp", BedSurfaceTemperaturePolicy.keysFor("1").otherLayers)
        assertEquals("eng_plate_temp", BedSurfaceTemperaturePolicy.keysFor("2").otherLayers)
        assertEquals("hot_plate_temp", BedSurfaceTemperaturePolicy.keysFor("3").otherLayers)
        assertEquals("textured_plate_temp", BedSurfaceTemperaturePolicy.keysFor("4").otherLayers)
        assertEquals("textured_cool_plate_temp", BedSurfaceTemperaturePolicy.keysFor("5").otherLayers)
        assertEquals("supertack_plate_temp", BedSurfaceTemperaturePolicy.keysFor("6").otherLayers)
        assertEquals("hot_plate_temp", BedSurfaceTemperaturePolicy.keysFor(null).otherLayers)
    }

    @Test
    fun resolvesPrinterBedDefaultsAndLeavesUnknownValuesToPeiFallback() {
        assertEquals("Cool Plate", BedSurfaceTemperaturePolicy.printerDefaultValue("1"))
        assertEquals("Engineering Plate", BedSurfaceTemperaturePolicy.printerDefaultValue("2"))
        assertEquals("High Temp Plate", BedSurfaceTemperaturePolicy.printerDefaultValue("3"))
        assertEquals("Textured PEI Plate", BedSurfaceTemperaturePolicy.printerDefaultValue("4"))
        assertEquals("Textured Cool Plate", BedSurfaceTemperaturePolicy.printerDefaultValue("5"))
        assertEquals("Supertack Plate", BedSurfaceTemperaturePolicy.printerDefaultValue("6"))
        assertEquals(null, BedSurfaceTemperaturePolicy.printerDefaultValue("unknown"))
    }
    @Test fun missingSurfaceDefaultsAreNotInjectedBeforeLegacyBackfill() {
        val skipped = ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef.SKIP_DEFAULT_OPTIONS
        for (surface in listOf("cool_plate", "eng_plate", "textured_plate", "textured_cool_plate", "supertack_plate")) {
            org.junit.Assert.assertTrue("$surface defaults would mask a missing legacy temperature", "${surface}_temp" in skipped)
            org.junit.Assert.assertTrue("${surface}_temp_initial_layer" in skipped)
        }
    }
}
