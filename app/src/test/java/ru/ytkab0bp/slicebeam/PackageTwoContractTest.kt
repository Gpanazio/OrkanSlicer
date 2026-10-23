package ru.ytkab0bp.slicebeam

import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source wiring checks complement geometry/policy unit tests; these do not execute Android GL. */
class PackageTwoContractTest {
    private fun source(path: String): String = File("src/$path").readText()

    @Test fun cockpitLabelsAndAccessibilityDescriptionStayCompatible() {
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        val strings = (0 until nodes.length).associate { i ->
            val element = nodes.item(i) as org.w3c.dom.Element
            element.getAttribute("name") to element.textContent
        }
        mapOf(
            "ImportSimplifyTitle" to "Simplify model?",
            "WorkspaceProfilesTitle" to "Print setup",
            "WorkspaceProfilePrinter" to "Printer profile",
            "WorkspaceProfileFilament" to "Filament profile",
            "WorkspaceProfileProcess" to "Print profile",
            "WorkspaceProfilesApply" to "Apply profiles",
            "MenuFileImportProfilesConflictTitle" to "Profiles already exist",
            "MenuFileImportProfilesOverwrite" to "Overwrite",
            "MenuFileImportProfilesWarningsTitle" to "Import notes",
            "GCodePreviewBadge" to "Preview: layers %1\$d–%2\$d of %3\$d",
            "WorkspaceProfilesStatusDescription" to "Selected printer: %1\$s. Nozzle: %2\$s. Filament: %3\$s. Print profile: %4\$s. Opens profile selection."
        ).forEach { (name, expected) -> assertEquals(name, expected, strings[name]) }
    }

    @Test fun bundledChangelogMatchesBuildVersionInEnglishForEveryLocale() {
        val version = File("build.gradle").readLines().first { it.trim().startsWith("versionName ") }.substringAfter('"').substringBefore('"')
        assertEquals("0.8.204", version)
        val notes = JsonParser.parseString(source("main/assets/update.json")).asJsonObject
        val english = notes.get("en").asString
        assertTrue(english.startsWith("OrkanSlicer $version\n"))
        notes.entrySet().forEach { assertEquals(english, it.value.asString) }
    }

    @Test fun glTouchPathBypassesBothNativeIndexAndNativeRaycastForBoundsHits() {
        val gl = source("main/java/ru/ytkab0bp/slicebeam/slic3r/GLModel.kt")
        assertTrue(gl.contains("LargeModelPick.needsPreciseSurfaceSimplification(model.getTriangleCount(i))"))
        assertTrue(gl.contains("if (approximatePickBounds == null) Native.glmodel_init_raycast_data(pointer)"))
        val branch = gl.substringAfter("approximatePickBounds?.let").substringBefore("val hits = Native.glmodel_raycast_closest_hit")
        assertTrue(branch.contains("LargeModelPick.hit"))
        assertTrue(branch.contains("return"))
        val renderer = source("main/java/ru/ytkab0bp/slicebeam/render/GLRenderer.kt")
        val paint = renderer.substringAfter("fun beginPaint(").substringBefore("fun paintAt(")
        val guard = paint.indexOf("if (LargeModelPick.needsPreciseSurfaceSimplification")
        assertTrue(guard >= 0)
        assertTrue(guard < paint.indexOf("PaintSession("))
        assertTrue(renderer.contains("if (!hit.approximate) return hit"))
    }

    @Test fun lowMemoryPolicyIsWiredToPrefsAndViewerWithoutNewPreviewJni() {
        val renderer = source("main/java/ru/ytkab0bp/slicebeam/render/GLRenderer.kt")
        assertTrue(renderer.contains("DevicePerformancePolicy.initialLayerBlockSize(Prefs.isLowRamDevice())"))
        assertTrue(renderer.contains("viewer!!.applyPerformanceMode(performance || Prefs.isLowRamDevice())"))
        val prefs = source("main/java/ru/ytkab0bp/slicebeam/utils/Prefs.kt")
        assertTrue(prefs.contains("DevicePerformancePolicy.defaultRenderScale(lowRamDevice)"))
        val native = source("main/java/ru/ytkab0bp/slicebeam/slic3r/Native.kt")
        assertFalse(native.contains("glmodel_init_from_model_object_preview"))
    }
}
