package ru.ytkab0bp.slicebeam.render

import java.util.ArrayDeque

/** Owns copied snapshots; callers serialize access on GL and own restored values. */
internal class ModelEditHistory<T : Any>(
    private val capacity: Int,
    private val copy: (T) -> T,
    private val release: (T) -> Unit,
) {
    class Entry<T>(val value: T?)
    private val undo = ArrayDeque<Entry<T>>()
    private val redo = ArrayDeque<Entry<T>>()
    private var stable: Entry<T>? = null

    init { require(capacity > 0) }

    fun seed(current: T?) {
        clear()
        stable = snapshot(current)
    }

    fun record(current: T?) {
        val next = snapshot(current)
        stable?.let { push(undo, it) }
        clear(redo)
        stable = next
    }

    fun undo(current: T?): Entry<T>? = restore(undo, redo, current)
    fun redo(current: T?): Entry<T>? = restore(redo, undo, current)

    private fun restore(source: ArrayDeque<Entry<T>>, destination: ArrayDeque<Entry<T>>, current: T?): Entry<T>? {
        if (source.isEmpty()) return null
        val restored = source.last
        val nextStable = snapshot(restored.value)
        val previous = snapshot(current)
        source.removeLast()
        push(destination, previous)
        stable?.value?.let(release)
        stable = nextStable
        return restored
    }

    fun clear() {
        clear(undo)
        clear(redo)
        stable?.value?.let(release)
        stable = null
    }

    private fun snapshot(value: T?) = Entry(value?.let(copy))
    private fun push(queue: ArrayDeque<Entry<T>>, entry: Entry<T>) {
        queue.addLast(entry)
        while (queue.size > capacity) queue.removeFirst().value?.let(release)
    }
    private fun clear(queue: ArrayDeque<Entry<T>>) {
        while (queue.isNotEmpty()) queue.removeFirst().value?.let(release)
    }
}
