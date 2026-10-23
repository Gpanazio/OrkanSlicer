package ru.ytkab0bp.slicebeam.config

/** Preserve explicit Orca bed-surface temperatures and backfill only absent legacy values. */
object SliceTemperatureNormalizer {
    private val surfaces = listOf(
        "cool_plate_temp", "eng_plate_temp", "textured_plate_temp",
        "textured_cool_plate_temp", "supertack_plate_temp",
    )

    fun fillMissingBedSurfaceTemperatures(config: ConfigObject) {
        for (suffix in listOf("", "_initial_layer")) {
            val general = config["hot_plate_temp$suffix"]?.takeIf { it.isNotBlank() } ?: continue
            for (surface in surfaces) {
                val key = "$surface$suffix"
                if (config[key].isNullOrBlank()) config.put(key, general)
            }
        }
    }
}
