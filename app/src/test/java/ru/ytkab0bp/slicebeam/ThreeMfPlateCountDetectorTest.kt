package ru.ytkab0bp.slicebeam

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

class ThreeMfPlateCountDetectorTest {
    @Test fun ignoresOrphanThumbnailsWhenProjectDeclaresOnePlate() {
        val xml = "<config><plates><plate index=\"1\" /></plates></config>"
        assertEquals(1, ThreeMfPlateCountDetector.detect(xml,
            listOf("Metadata/plate_1.png", "Metadata/plate_2.png", "Metadata/plate_no_light_2.png")))
    }

    @Test fun countsRealPlateElementsAndFallsBackToPlateDataWithoutThem() {
        val xml = "<config><plates><plate /><plate /></plates></config>"
        assertEquals(2, ThreeMfPlateCountDetector.detect(xml, listOf("Metadata/plate_3.png")))
        assertEquals(3, ThreeMfPlateCountDetector.detect(xml, listOf("Metadata/plate_3.json")))
        assertEquals(3, ThreeMfPlateCountDetector.detect(null,
            listOf("Metadata/plate_3.gcode", "Metadata/plate_4.png")))
        assertEquals(1, ThreeMfPlateCountDetector.detect(null, listOf("Metadata/plate_9.png")))
    }

    @Test fun ignoresCommentedPlatesAndRecognizesSelfClosingPlates() {
        val xml = """<config><!-- <plate/> --><![CDATA[<plate/>]]><plate id="1"/><plate /></config>"""
        assertEquals(2, ThreeMfPlateCountDetector.detect(xml, listOf("Metadata/plate_3.png")))
    }

    @Test fun acceptsAll36DesktopPlatesIncludingProjectsAbove32() {
        val xml = "<config>" + "<plate />".repeat(36) + "</config>"
        assertEquals(36, ThreeMfPlateCountDetector.detect(xml, emptyList()))
        assertEquals(36, ThreeMfPlateCountDetector.detect(null, listOf("Metadata/plate_36.json")))
    }

    @Test(expected = IOException::class)
    fun rejectsProjectsAboveDesktopLimit() {
        ThreeMfPlateCountDetector.detect("<config>" + "<plate />".repeat(37) + "</config>", emptyList())
    }

    @Test(expected = IOException::class)
    fun rejectsUnreasonablePlateIndexesBeforeAllocatingModels() {
        ThreeMfPlateCountDetector.detect(null, listOf("Metadata/plate_1000000.json"))
    }

    @Test fun keepsTheBundledOrcaBenchyOnItsSingleDeclaredPlate() {
        val fixture = File("../test_3mf/3DBenchy.3mf").takeIf(File::isFile)
            ?: File("test_3mf/3DBenchy.3mf")
        ZipFile(fixture).use { zip ->
            val xml = zip.getInputStream(zip.getEntry("Metadata/model_settings.config"))
                .bufferedReader().use { it.readText() }
            assertEquals(1, ThreeMfPlateCountDetector.detect(xml,
                zip.entries().asSequence().map { it.name }.toList()))
        }
    }
}
