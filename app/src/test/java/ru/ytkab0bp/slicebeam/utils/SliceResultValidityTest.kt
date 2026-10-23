package ru.ytkab0bp.slicebeam.utils

import org.junit.Assert.*
import org.junit.Test

class SliceResultValidityTest {
    @Test fun changingPrinterBackDoesNotReviveResult() {
        assertTrue(SliceResultValidity.isCurrent(1, "A", 1, "A"))
        assertFalse(SliceResultValidity.isCurrent(1, "A", 2, "B"))
        assertFalse(SliceResultValidity.isCurrent(1, "A", 3, "A"))
        assertTrue(SliceResultValidity.isCurrent(3, "A", 3, "A"))
    }
    @Test fun changedSelectionWithoutSaveIsAlsoRejected() {
        assertFalse(SliceResultValidity.isCurrent(1, "A", 1, "B"))
        assertFalse(SliceResultValidity.isCurrent(-1, null, 0, null))
    }
}
