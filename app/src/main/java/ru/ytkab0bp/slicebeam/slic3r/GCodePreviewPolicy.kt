package ru.ytkab0bp.slicebeam.slic3r

/** One-based preview selection; this never limits the sliced/exported print file. */
data class GCodePreviewSelection(val from: Int, val to: Int, val blockSize: Int = 50)

object GCodePreviewPolicy {
    const val DEFAULT_BLOCK_SIZE = 50

    fun maximum(layerCount: Long): Int = layerCount.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()

    fun initial(layerCount: Long, performance: Boolean, saved: GCodePreviewSelection?, blockSize: Int = DEFAULT_BLOCK_SIZE): GCodePreviewSelection {
        val max = maximum(layerCount)
        if (saved != null) {
            val from = saved.from.coerceIn(1, max)
            return saved.copy(from = from, to = saved.to.coerceIn(from, max), blockSize = saved.blockSize.coerceAtLeast(1))
        }
        return GCodePreviewSelection(1, if (performance) minOf(max, blockSize.coerceAtLeast(1)) else max, blockSize.coerceAtLeast(1))
    }

    fun blockCount(maximum: Int, size: Int): Int =
        ((maximum.coerceAtLeast(1).toLong() + size.coerceAtLeast(1) - 1) / size.coerceAtLeast(1)).toInt()

    fun blockAt(layer: Int, maximum: Int, size: Int): Int =
        (layer.coerceIn(1, maximum.coerceAtLeast(1)) - 1) / size.coerceAtLeast(1) + 1

    fun blockRange(part: Int, maximum: Int, size: Int): GCodePreviewSelection {
        val max = maximum.coerceAtLeast(1)
        val safeSize = size.coerceAtLeast(1)
        val index = part.coerceIn(1, blockCount(max, safeSize))
        val start = (index.toLong() - 1) * safeSize + 1
        return GCodePreviewSelection(start.toInt(), minOf(max.toLong(), start + safeSize - 1).toInt(), safeSize)
    }
}
