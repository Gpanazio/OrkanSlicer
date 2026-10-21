package ru.ytkab0bp.slicebeam.utils

import java.util.concurrent.atomic.AtomicBoolean

/** One upload, at most one start, and one read-only reconciliation after an uncertain start. */
class MoonrakerUploadSession(
    private val expectedName: String,
    private val print: Boolean,
    private val transport: Transport,
    private val finish: (Outcome) -> Unit,
) {
    data class Response(val body: ByteArray?, val error: Throwable? = null, val status: Int = 200)
    enum class Kind { UPLOADED, STARTED, QUEUED, UPLOAD_FAILED, FILE_NOT_CONFIRMED, START_UNCERTAIN }
    data class Outcome(val kind: Kind, val response: Response)
    interface Transport {
        fun upload(callback: (Response) -> Unit)
        fun start(path: String, callback: (Response) -> Unit)
        fun status(callback: (Response) -> Unit)
    }
    private val begun = AtomicBoolean()
    private val uploaded = AtomicBoolean()
    private val startAnswered = AtomicBoolean()
    private val reconciled = AtomicBoolean()
    private val finished = AtomicBoolean()

    fun run() {
        if (!begun.compareAndSet(false, true)) return
        try { transport.upload(::onUpload) }
        catch (error: RuntimeException) { onUpload(Response(null, error, 0)) }
    }

    private fun onUpload(response: Response) {
        if (!uploaded.compareAndSet(false, true)) return
        if (response.error != null || response.status !in 200..299 ||
            PrinterResponseInspector.embeddedError(response.body) != null) {
            complete(Kind.UPLOAD_FAILED, response)
            return
        }
        val file = MoonrakerResponseInspector.uploadedFile(response.body, expectedName)
        if (file == null) { complete(Kind.FILE_NOT_CONFIRMED, response); return }
        if (file.printStarted) { complete(Kind.STARTED, response); return }
        if (file.printQueued) { complete(Kind.QUEUED, response); return }
        if (!print) { complete(Kind.UPLOADED, response); return }
        try { transport.start(file.path) { onStart(file.path, it) } }
        catch (error: RuntimeException) { onStart(file.path, Response(null, error, 0)) }
    }

    private fun onStart(path: String, response: Response) {
        if (!startAnswered.compareAndSet(false, true)) return
        if (response.error == null && response.status in 200..299 &&
            PrinterResponseInspector.embeddedError(response.body) == null &&
            MoonrakerResponseInspector.printStartConfirmed(response.body)) {
            complete(Kind.STARTED, response)
            return
        }
        // A lost response does not establish that start failed. Never issue a second start.
        try {
            transport.status { status ->
                if (!reconciled.compareAndSet(false, true)) return@status
                val confirmed = status.error == null && status.status in 200..299 &&
                    PrinterResponseInspector.embeddedError(status.body) == null &&
                    MoonrakerResponseInspector.printStatus(status.body)?.confirmsStartedFile(path) == true
                complete(if (confirmed) Kind.STARTED else Kind.START_UNCERTAIN, response)
            }
        } catch (error: RuntimeException) {
            complete(Kind.START_UNCERTAIN, Response(null, error, 0))
        }
    }

    private fun complete(kind: Kind, response: Response) {
        if (finished.compareAndSet(false, true)) finish(Outcome(kind, response))
    }
}
