package ru.ytkab0bp.slicebeam.render

import org.junit.Assert.*
import org.junit.Test

class ModelEditHistoryTest {
    private data class Geometry(val name: String, val scale: Double)
    private fun history(capacity: Int = 3) = ModelEditHistory<Geometry>(capacity, { it.copy() }, {})

    @Test fun firstScaleAfterImportUndoesToImportedGeometry() {
        val h = history()
        val imported = Geometry("imported", 1.0)
        h.seed(imported)
        val scaled = imported.copy(scale = 1.5)
        h.record(scaled)
        assertEquals(imported, h.undo(scaled)!!.value)
        assertEquals(scaled, h.redo(imported)!!.value)
    }
    @Test fun committedScaleResetCanBeUndoneAndRedone() {
        val h = history()
        val scaled = Geometry("imported", 2.0)
        val reset = scaled.copy(scale = 1.0)
        h.seed(scaled); h.record(reset)
        assertEquals(2.0, h.undo(reset)!!.value!!.scale, 0.0)
        assertEquals(1.0, h.redo(scaled)!!.value!!.scale, 0.0)
    }
    @Test fun aNewEditInvalidatesRedoAndImportReplacesOldBaseline() {
        val h = history()
        val original = Geometry("first", 1.0)
        val edit = original.copy(scale = 2.0)
        h.seed(original); h.record(edit); h.undo(edit)
        h.record(original.copy(scale = 3.0))
        assertNull(h.redo(original))
        val imported = Geometry("second", 1.0)
        h.seed(imported)
        assertNull(h.undo(imported))
    }
    @Test fun deletionAndEmptyWorkspaceAreValidHistoryStates() {
        val h = history()
        val imported = Geometry("object", 1.0)
        h.seed(imported); h.record(null)
        assertEquals(imported, h.undo(null)!!.value)
        assertNull(h.redo(imported)!!.value)
    }
    @Test fun boundedHistoryReleasesOwnedCopiesExactlyOnce() {
        val released = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Geometry, Boolean>())
        val h = ModelEditHistory<Geometry>(1, { it.copy() }, { assertTrue(released.add(it)) })
        val original = Geometry("object", 1.0)
        h.seed(original); h.record(original.copy(scale = 2.0)); h.record(original.copy(scale = 3.0))
        assertEquals(1, released.size)
        h.clear(); h.clear()
        assertEquals(3, released.size)
    }
}
