package ru.ytkab0bp.slicebeam.config

/** Maps Orca's active plate enum to the filament temperature keys used by its G-code engine. */
object BedSurfaceTemperaturePolicy {
    data class Keys(val firstLayer: String, val otherLayers: String)

    fun printerDefaultValue(defaultBedType: String?): String? = when (defaultBedType?.trim()) {
        "1" -> "Cool Plate"
        "2" -> "Engineering Plate"
        "3" -> "High Temp Plate"
        "4" -> "Textured PEI Plate"
        "5" -> "Textured Cool Plate"
        "6" -> "Supertack Plate"
        else -> null
    }

    /** `curr_bed_type` uses native enum values: 1 Cool, 2 Engineering, 3 High Temp,
     * 4 Textured PEI, 5 Textured Cool, and 6 SuperTack. Missing values use Orkan's btPEI fallback. */
    fun keysFor(bedType: String?): Keys {
        val normalized = bedType.orEmpty().trim().lowercase()
        val prefix = when (normalized) {
            "1", "cool plate", "smooth cool plate" -> "cool_plate"
            "2", "engineering plate" -> "eng_plate"
            "3", "high temp plate", "default plate" -> "hot_plate"
            "4", "textured pei plate" -> "textured_plate"
            "5", "textured cool plate" -> "textured_cool_plate"
            "6", "supertack plate", "cool plate (supertack)" -> "supertack_plate"
            else -> "hot_plate"
        }
        return Keys("${prefix}_temp_initial_layer", "${prefix}_temp")
    }
}
