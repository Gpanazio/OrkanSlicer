package ru.ytkab0bp.slicebeam.config

import org.junit.Assert.assertEquals
import org.junit.Test

class SliceTemperatureNormalizerTest {
    @Test
    fun explicitBedSurfaceTemperaturesArePreservedAndMissingValuesAreBackfilled() {
        val config = ConfigObject().apply {
            put("hot_plate_temp", "70,100,70")
            put("hot_plate_temp_initial_layer", "70,100,70")
            put("cool_plate_temp", "35,0,35")
            put("textured_plate_temp", "45,100,45")
            put("textured_cool_plate_temp_initial_layer", "32,0,32")
            put("nozzle_temperature", "230,205,230")
        }

        SliceTemperatureNormalizer.fillMissingBedSurfaceTemperatures(config)

        assertEquals("35,0,35", config["cool_plate_temp"])
        assertEquals("70,100,70", config["cool_plate_temp_initial_layer"])
        assertEquals("70,100,70", config["eng_plate_temp"])
        assertEquals("45,100,45", config["textured_plate_temp"])
        assertEquals("32,0,32", config["textured_cool_plate_temp_initial_layer"])
        assertEquals("70,100,70", config["supertack_plate_temp"])
        assertEquals("230,205,230", config["nozzle_temperature"])
    }
}
