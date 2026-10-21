package ru.ytkab0bp.slicebeam.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OctoPrintResponseInspectorTest {
    private fun bytes(value: String) = value.toByteArray(Charsets.UTF_8)

    @Test fun uploadReportsActualFileAndEffectivePrintState() {
        val reply = OctoPrintResponseInspector.uploadedFile(bytes(
            """{"files":{"local":{"name":"wanted.gcode","path":"renamed.gcode","origin":"local"}},"done":true,"effectiveSelect":true,"effectivePrint":true}"""))
        assertEquals("renamed.gcode", reply?.path)
        assertTrue(reply!!.processingDone == true)
        assertTrue(reply.selected == true)
        assertTrue(reply.printStarted == true)
        assertEquals(OctoPrintResponseInspector.UploadOutcome.PRINT_STARTED, reply.outcome(true))
    }

    @Test fun rejectedSelectionOrPrintCannotBeReportedAsStarted() {
        val reply = OctoPrintResponseInspector.uploadedFile(bytes(
            """{"files":{"local":{"name":"model.gcode"}},"done":true,"effectiveSelect":false,"effectivePrint":false}"""))
        assertEquals("model.gcode", reply?.path)
        assertFalse(reply!!.selected == true)
        assertFalse(reply.printStarted == true)
        assertEquals(OctoPrintResponseInspector.UploadOutcome.PRINT_NOT_STARTED, reply.outcome(true))
    }

    @Test fun unfinishedUploadAndAbsentFlagsRemainUnconfirmed() {
        val processing = OctoPrintResponseInspector.uploadedFile(bytes(
            """{"files":{"local":{"path":"folder/model.gcode"}},"done":false}"""))
        assertEquals("folder/model.gcode", processing?.path)
        assertFalse(processing!!.processingDone == true)
        assertNull(processing.printStarted)
        assertEquals(OctoPrintResponseInspector.UploadOutcome.PROCESSING, processing.outcome(false))
        assertEquals(OctoPrintResponseInspector.UploadOutcome.PRINT_NOT_STARTED, processing.outcome(true))
        val legacy = OctoPrintResponseInspector.uploadedFile(bytes(
            """{"files":{"local":{"name":"legacy.gcode"}}}"""))
        assertNull(legacy?.processingDone)
        assertNull(legacy?.selected)
        assertNull(legacy?.printStarted)
        assertEquals(OctoPrintResponseInspector.UploadOutcome.UPLOADED, legacy?.outcome(false))
        val selected = OctoPrintResponseInspector.uploadedFile(bytes(
            """{"files":{"local":{"path":"selected.gcode"}},"effectiveSelect":true,"effectivePrint":false}"""))
        assertEquals(OctoPrintResponseInspector.UploadOutcome.SELECTED_NOT_STARTED, selected?.outcome(true))
    }

    @Test fun unrelatedFolderOrUnsafePathIsNotAFileAcknowledgement() {
        for (payload in listOf("", "{}", "not json", """{"folder":{"name":"model"},"done":true}""",
            """{"files":{"sdcard":{"name":"model.gcode"}}}""",
            """{"files":{"local":{"origin":"sdcard","name":"model.gcode"}}}""",
            """{"files":{"local":{"path":"../model.gcode"}}}""",
            """{"files":{"local":{"path":"/model.gcode"}}}""",
            """{"files":{"local":{"name":"model\\x.gcode"}}}""")) {
            assertNull("Accepted $payload", OctoPrintResponseInspector.uploadedFile(bytes(payload)))
        }
    }
}
