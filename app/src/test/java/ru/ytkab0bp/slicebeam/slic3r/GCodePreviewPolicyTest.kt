package ru.ytkab0bp.slicebeam.slic3r

import org.junit.Assert.assertEquals
import org.junit.Test

class GCodePreviewPolicyTest {
    @Test fun lightweightPreviewStartsWithFiftyLayersAndFullFileCountRemainsAvailable() {
        assertEquals(GCodePreviewSelection(1, 50), GCodePreviewPolicy.initial(1533, true, null))
        assertEquals(GCodePreviewSelection(1, 1533), GCodePreviewPolicy.initial(1533, false, null))
        assertEquals(GCodePreviewSelection(1, 23), GCodePreviewPolicy.initial(23, true, null))
    }
    @Test fun weakDevicePreviewStartsWithTwentyFiveLayersWithoutTruncatingThePrint() {
        assertEquals(GCodePreviewSelection(1, 25, 25), GCodePreviewPolicy.initial(104, true, null, 25))
        assertEquals(GCodePreviewSelection(1, 104, 25), GCodePreviewPolicy.initial(104, false, null, 25))
        assertEquals(GCodePreviewSelection(1, 25, 25), GCodePreviewPolicy.blockRange(1, 104, 25))
        assertEquals(GCodePreviewSelection(101, 104, 25), GCodePreviewPolicy.blockRange(5, 104, 25))
    }
    @Test fun consecutivePartsIncludeEachLayerExactlyOnceAndIncludeTheFinalLayer() {
        val ranges = (1..GCodePreviewPolicy.blockCount(1533, 50)).map { GCodePreviewPolicy.blockRange(it, 1533, 50) }
        assertEquals(31, ranges.size)
        assertEquals(GCodePreviewSelection(51, 100), ranges[1])
        assertEquals(GCodePreviewSelection(1501, 1533), ranges.last())
        assertEquals((1..1533).toList(), ranges.flatMap { it.from..it.to })
    }
    @Test fun savedCustomRangeAndPartSizeSurviveReopening() {
        val saved = GCodePreviewSelection(72, 91, 25)
        assertEquals(saved, GCodePreviewPolicy.initial(104, true, saved))
        assertEquals(GCodePreviewSelection(20, 20, 25), GCodePreviewPolicy.initial(20, true, saved))
    }
    @Test fun emptyFilesAndIntegerLimitsCannotCreateMissingOrOverflowingRanges() {
        assertEquals(GCodePreviewSelection(1, 1), GCodePreviewPolicy.initial(0, true, null))
        assertEquals(GCodePreviewSelection(Int.MAX_VALUE, Int.MAX_VALUE, 1),
            GCodePreviewPolicy.blockRange(Int.MAX_VALUE, Int.MAX_VALUE, 1))
        val count = GCodePreviewPolicy.blockCount(Int.MAX_VALUE, 50)
        assertEquals(Int.MAX_VALUE, GCodePreviewPolicy.blockRange(count, Int.MAX_VALUE, 50).to)
    }
    @Test fun changingPartSizeKeepsTheCurrentLayerInItsNewPart() {
        assertEquals(2, GCodePreviewPolicy.blockAt(51, 104, 50))
        assertEquals(1, GCodePreviewPolicy.blockAt(51, 104, 100))
        assertEquals(3, GCodePreviewPolicy.blockAt(104, 104, 50))
    }
}
