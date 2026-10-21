package ru.ytkab0bp.slicebeam.utils

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MoonrakerUploadSessionTest {
    private fun response(json: String) = MoonrakerUploadSession.Response(json.toByteArray())
    private val upload = response("""{"result":{"item":{"root":"gcodes","path":"folder/job.gcode"},"print_started":false,"print_queued":false}}""")
    private class MockTransport : MoonrakerUploadSession.Transport {
        var uploads = 0; var starts = 0; var queries = 0
        var path: String? = null
        lateinit var uploaded: (MoonrakerUploadSession.Response) -> Unit
        lateinit var started: (MoonrakerUploadSession.Response) -> Unit
        lateinit var statusResult: (MoonrakerUploadSession.Response) -> Unit
        override fun upload(callback: (MoonrakerUploadSession.Response) -> Unit) { uploads++; uploaded = callback }
        override fun start(path: String, callback: (MoonrakerUploadSession.Response) -> Unit) { starts++; this.path = path; started = callback }
        override fun status(callback: (MoonrakerUploadSession.Response) -> Unit) { queries++; statusResult = callback }
    }
    @Test fun uploadOnlyHasExactlyOneUploadAndNoStart() {
        val t = MockTransport(); val outcomes = mutableListOf<MoonrakerUploadSession.Outcome>()
        val s = MoonrakerUploadSession("job.gcode", false, t, outcomes::add)
        s.run(); s.run(); t.uploaded(upload); t.uploaded(upload)
        assertEquals(1, t.uploads); assertEquals(0, t.starts); assertEquals(0, t.queries)
        assertEquals(listOf(MoonrakerUploadSession.Kind.UPLOADED), outcomes.map { it.kind })
    }
    @Test fun uncertainStartQueriesOnceAndNeverRetriesEvenWithDuplicateCallbacks() {
        val t = MockTransport(); val outcomes = mutableListOf<MoonrakerUploadSession.Outcome>()
        MoonrakerUploadSession("job.gcode", true, t, outcomes::add).run()
        t.uploaded(upload); t.uploaded(upload)
        assertEquals("folder/job.gcode", t.path)
        val timeout = MoonrakerUploadSession.Response(null, IOException("timeout"), 0)
        t.started(timeout); t.started(timeout)
        assertEquals(1, t.starts); assertEquals(1, t.queries)
        val status = response("""{"result":{"status":{"print_stats":{"state":"printing","filename":"folder/job.gcode"}}}}""")
        t.statusResult(status); t.statusResult(status)
        assertEquals(listOf(MoonrakerUploadSession.Kind.STARTED), outcomes.map { it.kind })
    }
    @Test fun sameBasenameInAnotherFolderDoesNotConfirmUncertainStart() {
        val t = MockTransport(); var outcome: MoonrakerUploadSession.Outcome? = null
        MoonrakerUploadSession("job.gcode", true, t) { outcome = it }.run()
        t.uploaded(upload); t.started(response("{}"))
        t.statusResult(response("""{"result":{"status":{"print_stats":{"state":"printing","filename":"other/job.gcode"}}}}"""))
        assertEquals(MoonrakerUploadSession.Kind.START_UNCERTAIN, outcome?.kind)
        assertEquals(1, t.starts); assertEquals(1, t.queries)
        assertEquals("printing", outcome?.printerState)
    }
    @Test fun wrongFileEmbeddedErrorEmptyAndNullResponsesNeverStart() {
        for (r in listOf(response("""{"result":{"item":{"root":"gcodes","path":"wrong.gcode"}}}"""),
            response("""{"error":{"code":500,"message":"busy"}}"""), response(""), MoonrakerUploadSession.Response(null))) {
            val t = MockTransport(); var outcome: MoonrakerUploadSession.Outcome? = null
            MoonrakerUploadSession("job.gcode", true, t) { outcome = it }.run(); t.uploaded(r)
            assertNotNull(outcome); assertEquals(0, t.starts); assertEquals(0, t.queries)
        }
    }
    @Test fun alreadyQueuedOrStartedNeverStartsAgain() {
        for ((flag, kind) in listOf("print_started" to MoonrakerUploadSession.Kind.STARTED,
            "print_queued" to MoonrakerUploadSession.Kind.QUEUED)) {
            val t = MockTransport(); var outcome: MoonrakerUploadSession.Outcome? = null
            MoonrakerUploadSession("job.gcode", true, t) { outcome = it }.run()
            t.uploaded(response("""{"item":{"root":"gcodes","path":"job.gcode"},"$flag":true}"""))
            assertEquals(kind, outcome?.kind); assertEquals(0, t.starts)
        }
    }
    @Test fun terminalErrorsReleaseHostForNextJob() {
        val reservations = PrinterUploadReservation(); val key = reservations.reserve("printer")!!
        val t = MockTransport()
        MoonrakerUploadSession("job.gcode", true, t) { reservations.release(key) }.run()
        assertNull(reservations.reserve("http://printer:80/proxy"))
        t.uploaded(MoonrakerUploadSession.Response(null, IOException("failed"), 0))
        assertNotNull(reservations.reserve("http://printer:80/proxy"))
    }
    @Test fun explicitStartAcknowledgementDoesNotQueryStatus() {
        val t = MockTransport(); var outcome: MoonrakerUploadSession.Outcome? = null
        MoonrakerUploadSession("job.gcode", true, t) { outcome = it }.run()
        t.uploaded(upload); t.started(response("""{"result":"ok"}"""))
        assertEquals(MoonrakerUploadSession.Kind.STARTED, outcome?.kind); assertEquals(0, t.queries)
    }
}
