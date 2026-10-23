package ru.ytkab0bp.slicebeam.render

import org.junit.Assert.*
import org.junit.Test

class GlEditDispatchTest {
    @Test fun copiedClickWaitsForGlQueueAndRendersOnlyWhenHandled() {
        val queue = mutableListOf<() -> Unit>()
        val calls = mutableListOf<String>()
        var x = 20f
        GlEditDispatch.click({ queue.add(it) }, x, 30f, .5f,
            { a, b -> calls.add("$a,$b"); true }, { calls.add("render") })
        x = 99f
        assertEquals(99f, x)
        assertTrue(calls.isEmpty())
        queue.single().invoke()
        assertEquals(listOf("10.0,15.0", "render"), calls)
        calls.clear(); queue.clear()
        GlEditDispatch.click({ queue.add(it) }, 1f, 1f, 1f, { _, _ -> false }, { calls.add("render") })
        queue.single().invoke()
        assertTrue(calls.isEmpty())
    }
    @Test fun bindingRejectsReplacementGeometryAndSelectionChanges() {
        val model = Any()
        val indices = intArrayOf(0, 2)
        val binding = ModelEditBinding(model, 5L, 3, 2, indices)
        indices[0] = 1
        assertTrue(binding.matches(model, 5L, 3, 2, intArrayOf(0, 2)))
        assertFalse(binding.matches(Any(), 5L, 3, 2, intArrayOf(0, 2)))
        assertFalse(binding.matches(model, 6L, 3, 2, intArrayOf(0, 2)))
        assertFalse(binding.matches(model, 5L, 2, 2, intArrayOf(0, 2)))
        assertFalse(binding.matches(model, 5L, 3, 0, intArrayOf(0, 2)))
        assertFalse(binding.matches(model, 5L, 3, 2, intArrayOf(2)))
        assertFalse(binding.matches(null, 5L, 3, 2, intArrayOf(0, 2)))
    }
}
