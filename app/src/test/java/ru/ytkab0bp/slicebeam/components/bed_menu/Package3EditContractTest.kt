package ru.ytkab0bp.slicebeam.components.bed_menu

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Static wiring checks complement the JVM policies; these do not execute Android or JNI. */
class Package3EditContractTest {
    private fun source(path: String) = File("src/main/java/ru/ytkab0bp/slicebeam/$path").readText()

    @Test fun explicitObjectFilamentReplacesImportedPaintAndVolumes() {
        val actions = source("components/bed_menu/FileMenuObjectActions.kt")
        assertTrue(actions.contains("setObjectUniformFilament"))
        assertFalse(actions.contains("setObjectBaseFilament"))
        assertTrue(source("render/GLRenderer.kt").contains("this.model!!.setUniformFilament(i, filament)"))
    }
    @Test fun importedGeometrySeedsUndoBaseline() {
        val bed = source("fragment/BedFragment.kt")
        assertTrue(bed.contains("private fun onModelImportCommitted()"))
        assertEquals(4, Regex("onModelImportCommitted\\(\\)").findAll(bed).count())
    }
    @Test fun resetScaleChangesNativeGeometryAndSeatsOnBed() {
        val bed = source("fragment/BedFragment.kt")
        val reset = bed.substringAfter("fun resetModelScale(").substringBefore("@JvmOverloads fun enterPaintMode")
        assertTrue(reset.contains("model.scale(index, 1.0, 1.0, 1.0)"))
        assertTrue(reset.contains("model.ensureOnBed(index)"))
        assertTrue(reset.contains("recordModelEditOnGl()"))
        assertFalse(source("slic3r/Native.kt").contains("model_reset_rotation"))
        assertFalse(source("slic3r/Native.kt").contains("model_mirror("))
    }
    @Test fun delayedEditorsAndObjectDialogsCheckCapturedSelection() {
        for (path in listOf("OrientationTransformMenus.kt", "TransformScaleMenu.kt", "FileMenuObjectActions.kt")) {
            val editor = source("components/bed_menu/$path")
            assertTrue("$path must bind delayed work to its opening selection", editor.contains("isSelectionSnapshotCurrent(snapshot)"))
            assertTrue(editor.contains("queueEvent"))
        }
        assertTrue(source("view/GLView.kt").contains("GlEditDispatch.click("))
        val bed = source("fragment/BedFragment.kt")
        assertTrue(bed.contains("modelEditHistory.seed(current)"))
        assertTrue(bed.contains("modelEditHistory.record(currentModel)"))
        assertTrue(bed.contains("isSelectionSnapshotCurrent(snapshot)"))
    }
}
