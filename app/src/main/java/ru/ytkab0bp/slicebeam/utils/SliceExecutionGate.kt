package ru.ytkab0bp.slicebeam.utils

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Serializes setup, native writes and the final UI publication, including all failure paths. */
internal class SliceExecutionGate(
    private val dispatchCompletion: (() -> Unit) -> Unit = { it() },
) {
    private val inFlight = AtomicReference<Any?>()

    fun launch(
        onBusy: () -> Unit,
        prepare: (complete: (() -> Unit) -> Unit) -> (() -> Unit),
        start: (() -> Unit) -> Unit = { work -> Thread { work() }.start() },
    ) {
        // Allocate before acquiring: allocation failure must not leave a held gate.
        val ticket = Any()
        val completionSubmitted = AtomicBoolean()
        if (!inFlight.compareAndSet(null, ticket)) {
            onBusy()
            return
        }
        try {
            val work = prepare { action ->
                if (completionSubmitted.compareAndSet(false, true)) {
                    try {
                        dispatchCompletion {
                            try { action() }
                            finally { inFlight.compareAndSet(ticket, null) }
                        }
                    } catch (failure: Throwable) {
                        inFlight.compareAndSet(ticket, null)
                        throw failure
                    }
                }
            }
            start {
                try { work() }
                finally {
                    // A queued completion owns the gate until it publishes or discards the result.
                    if (!completionSubmitted.get()) inFlight.compareAndSet(ticket, null)
                }
            }
        } catch (failure: Throwable) {
            // Covers setup, allocation and Thread.start() failures as well as synchronous runners.
            inFlight.compareAndSet(ticket, null)
            throw failure
        }
    }
}
