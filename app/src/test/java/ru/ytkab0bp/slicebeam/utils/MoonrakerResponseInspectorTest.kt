package ru.ytkab0bp.slicebeam.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonrakerResponseInspectorTest {
    private fun bytes(value: String) = value.toByteArray(Charsets.UTF_8)

    @Test fun standardUploadReturnsTheActualStoredPath() {
        val acknowledgement = MoonrakerResponseInspector.uploadedFile(bytes(
            """{"item":{"root":"gcodes","path":"subdir/model-1.gcode"},"action":"create_file","print_started":false,"print_queued":false}"""))
        assertEquals("subdir/model-1.gcode", acknowledgement?.path)
        assertEquals("subdir/model-1.gcode", MoonrakerResponseInspector.uploadedFile(bytes(
            """{"item":{"root":"gcodes","path":"subdir/model-1.gcode"}}"""), "model-1.gcode")?.path)
        assertFalse(acknowledgement!!.printStarted)
        assertFalse(acknowledgement.printQueued)
        assertEquals("My model.gcode", MoonrakerResponseInspector.uploadedFile(bytes(
            """{"item":{"root":"gcodes","path":"My model.gcode"},"action":"create_file"}"""))?.path)
    }

    @Test fun wrappedUploadAndExistingPrintStateAreRecognized() {
        val started = MoonrakerResponseInspector.uploadedFile(bytes(
            """{"result":{"item":{"root":"gcodes","path":"part.gcode.3mf"},"print_started":true}}"""))
        val queued = MoonrakerResponseInspector.uploadedFile(bytes(
            """{"item":{"root":"gcodes","path":"part.gcode"},"print_queued":true,"action":"create_file"}"""))
        assertTrue(started!!.printStarted)
        assertTrue(queued!!.printQueued)
    }

    @Test fun invalidUploadAcknowledgementCannotStartAnotherFile() {
        for (payload in listOf("", "{}", "not json",
            """{"result":"ok"}""",
            """{"item":{"root":"config","path":"part.gcode"}}""",
            """{"item":{"root":"gcodes","path":"../other.gcode"}}""",
            """{"item":{"root":"gcodes","path":"/other.gcode"}}""",
            """{"item":{"root":"gcodes","path":"other.gcode"},"action":"delete_file"}""")) {
            assertNull("Accepted $payload", MoonrakerResponseInspector.uploadedFile(bytes(payload)))
        }
    }

    @Test fun acknowledgementForAnotherFileCannotBeStarted() {
        val reply = bytes("""{"item":{"root":"gcodes","path":"other.gcode"},"action":"create_file"}""")
        assertNull(MoonrakerResponseInspector.uploadedFile(reply, "wanted.gcode"))
    }

    @Test fun printStartRequiresAnExplicitOkResult() {
        assertTrue(MoonrakerResponseInspector.printStartConfirmed(bytes("""{"result":"ok"}""")))
        assertTrue(MoonrakerResponseInspector.printStartConfirmed(bytes("\"ok\"")))
        for (payload in listOf("", "{}", "not json", """{"result":null}""",
            """{"result":"busy"}""", """{"error":{"code":123,"message":"busy"}}""")) {
            assertFalse("Accepted $payload", MoonrakerResponseInspector.printStartConfirmed(bytes(payload)))
        }
    }

    @Test fun parsesPrinterStateAndOnlyConfirmsTheUploadedFile() {
        val status = MoonrakerResponseInspector.printStatus(bytes(
            """{"result":{"status":{"print_stats":{"state":"printing","filename":"subdir/model.gcode"}}}}"""))
        assertEquals("printing", status?.state)
        assertTrue(status!!.confirmsStartedFile("subdir/model.gcode"))
        assertFalse(status.confirmsStartedFile("model.gcode"))
        assertFalse(status.confirmsStartedFile("other.gcode"))

        val paused = MoonrakerResponseInspector.printStatus(bytes(
            """{"result":{"status":{"print_stats":{"state":"paused","filename":"model.gcode"}}}}"""))
        assertTrue(paused!!.confirmsStartedFile("model.gcode"))
        for (payload in listOf("", "{}", "not json")) {
            assertNull("Accepted $payload", MoonrakerResponseInspector.printStatus(bytes(payload)))
        }
        val idle = MoonrakerResponseInspector.printStatus(bytes(
            """{"result":{"status":{"print_stats":{"state":"idle","filename":"model.gcode"}}}}"""))
        assertFalse(idle!!.confirmsStartedFile("model.gcode"))
    }

    @Test fun sameBasenameInAnotherDirectoryCannotConfirmThePrint() {
        val status = MoonrakerResponseInspector.printStatus(bytes(
            """{"result":{"status":{"print_stats":{"state":"printing","filename":"other/model.gcode"}}}}"""))
        assertFalse(status!!.confirmsStartedFile("requested/model.gcode"))
        assertFalse(status.confirmsStartedFile("model.gcode"))
    }
}
