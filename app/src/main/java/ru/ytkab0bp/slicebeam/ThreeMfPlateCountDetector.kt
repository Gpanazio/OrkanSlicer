package ru.ytkab0bp.slicebeam

import java.io.IOException

/** Counts actual project plates, ignoring orphan thumbnail images in 3MF archives. */
internal object ThreeMfPlateCountDetector {
    // OrcaSlicer desktop: src/slic3r/GUI/PartPlate.hpp, MAX_PLATE_COUNT.
    private const val MAX_PLATES = 36
    private val ignoredXml = Regex("<!--[\\s\\S]*?-->|<!\\[CDATA\\[[\\s\\S]*?]]>")
    private val plateElement = Regex("<plate(?:\\s|/?>)")
    private val plateData = Regex("^Metadata/plate_(\\d+)\\.(?:json|gcode)$", RegexOption.IGNORE_CASE)

    @Throws(IOException::class)
    fun detect(modelSettingsXml: String?, entryNames: Iterable<String>): Int {
        val declared = modelSettingsXml?.let { plateElement.findAll(it.replace(ignoredXml, "")).count() } ?: 0
        if (declared > MAX_PLATES) throw IOException("3MF project has too many build plates")
        var inferred = maxOf(1, declared)
        for (name in entryNames) {
            val index = plateData.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (index > MAX_PLATES) throw IOException("3MF project has too many build plates")
            inferred = maxOf(inferred, index)
        }
        return inferred
    }
}
