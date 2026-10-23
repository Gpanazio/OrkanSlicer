package ru.ytkab0bp.slicebeam.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineWidthConversionTest {
    @Test fun percentAndMillimetresStayCoupledToNozzle() {
        assertEquals(105.0, LineWidthConversion.percent("0.42", 0.4)!!, 0.0001)
        assertEquals(0.42, LineWidthConversion.millimetres(105.0, 0.4), 0.0001)
        assertEquals(105.0, LineWidthConversion.percentFromMillimetres(0.63, 0.6), 0.0001)
        assertEquals("105", LineWidthConversion.formatPercent(105.0))
        assertEquals("0.42", LineWidthConversion.formatMillimetres(0.42))
    }

    @Test fun percentageRemainsRelativeWhenNozzleChanges() {
        assertEquals(0.42, LineWidthConversion.millimetres(LineWidthConversion.percent("105%", 0.4)!!, 0.4), 0.0001)
        assertEquals(0.63, LineWidthConversion.millimetres(LineWidthConversion.percent("105%", 0.6)!!, 0.6), 0.0001)
    }

    @Test fun bridgeWidthUsesTheSamePairedEditorButCannotExceedTheNozzle() {
        assertTrue("bridge_line_width" in LineWidthConversion.keys)
        assertEquals(100.0, LineWidthConversion.maxPercent("bridge_line_width"), 0.0)
        assertEquals(300.0, LineWidthConversion.maxPercent("outer_wall_line_width"), 0.0)
        assertEquals(0.4, LineWidthConversion.millimetres(100.0, 0.4), 0.0001)
    }
    @Test fun bridgeAngleAndWidthAreExposedOnceInTheExistingProcessEditor() {
        val keys = ru.ytkab0bp.slicebeam.ui.settings.OrcaProcessSettingsCatalog.categories()
            .flatMap { it.sections }.flatMap { it.keys }
        assertEquals(1, keys.count { it == "bridge_line_width" })
        assertEquals(1, keys.count { it == "relative_bridge_angle" })
        assertEquals(87.5, LineWidthConversion.percent("0.35", 0.4)!!, 0.0001)
        assertEquals(0.35, LineWidthConversion.millimetres(87.5, 0.4), 0.0001)
    }
}
