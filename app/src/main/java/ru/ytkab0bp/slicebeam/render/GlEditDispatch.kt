package ru.ytkab0bp.slicebeam.render

/** Copies touch values before Android recycles the MotionEvent. */
internal object GlEditDispatch {
    fun click(queue: (() -> Unit) -> Unit, x: Float, y: Float, scale: Float,
              onClick: (Float, Float) -> Boolean, render: () -> Unit) {
        val scaledX = x * scale
        val scaledY = y * scale
        queue { if (onClick(scaledX, scaledY)) render() }
    }
}

/** Immutable identity/geometry/selection binding for delayed edits. No JNI is read by this value. */
internal class ModelEditBinding<T : Any>(
    val model: T,
    val geometryRevision: Long,
    val objectCount: Int,
    val primaryObject: Int,
    selectedIndices: IntArray,
) {
    private val indices = selectedIndices.copyOf()
    val selectedIndices: IntArray get() = indices.copyOf()
    fun matches(currentModel: T?, revision: Long, count: Int, primary: Int, selected: IntArray): Boolean =
        currentModel === model && revision == geometryRevision && count == objectCount &&
            primary == primaryObject && selected.contentEquals(indices)
}

internal typealias ModelSelectionSnapshot = ModelEditBinding<ru.ytkab0bp.slicebeam.slic3r.Model>
