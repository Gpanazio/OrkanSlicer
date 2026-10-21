package ru.ytkab0bp.slicebeam.utils

/** Revisions prevent a printer A -> B -> A switch from reviving stale G-code. */
internal object SliceResultValidity {
    fun isCurrent(slicedRevision: Int, slicedPrinter: String?, currentRevision: Int,
                  currentPrinter: String?): Boolean =
        slicedRevision >= 0 && slicedRevision == currentRevision && slicedPrinter == currentPrinter
}
