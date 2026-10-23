package ru.ytkab0bp.slicebeam.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePerformancePolicyTest {
    @Test fun weakDevicesUseFewerPixelsAndShorterInitialPreview() {
        assertEquals(0.7f, DevicePerformancePolicy.defaultRenderScale(true))
        assertEquals(25, DevicePerformancePolicy.initialLayerBlockSize(true))
        assertEquals(1f, DevicePerformancePolicy.defaultRenderScale(false))
        assertEquals(50, DevicePerformancePolicy.initialLayerBlockSize(false))
    }

    @Test fun inactivePlatesCanUseCheapPreviewWithoutChangingTheActiveModel() {
        assertTrue(DevicePerformancePolicy.useSimpleInactivePlatePreview(true, false))
        assertTrue(DevicePerformancePolicy.useSimpleInactivePlatePreview(false, true))
        assertFalse(DevicePerformancePolicy.useSimpleInactivePlatePreview(false, false))
    }
}
