package ru.ytkab0bp.slicebeam.render

/** Defaults for the viewport only; slicing always uses the original model. */
object DevicePerformancePolicy {
    fun defaultRenderScale(lowRamDevice: Boolean): Float = if (lowRamDevice) 0.7f else 1f

    fun useSimpleInactivePlatePreview(performanceMode: Boolean, lowRamDevice: Boolean): Boolean =
        performanceMode || lowRamDevice

    fun initialLayerBlockSize(lowRamDevice: Boolean): Int = if (lowRamDevice) 25 else 50
}
