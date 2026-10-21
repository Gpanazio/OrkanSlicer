package ru.ytkab0bp.slicebeam.utils

import java.util.concurrent.atomic.AtomicReference

/** Serializes preparation and native writes, releasing even when setup or a worker throws an Error. */
internal class SliceExecutionGate {
    private val inFlight = AtomicReference<Any?>()

    fun launch(
        onBusy: () -> Unit,
        prepare: () -> (() -> Unit),
        start: (() -> Unit) -> Unit = { work -> Thread { work() }.start() },
    ) {
        val ticket = Any()
        if (!inFlight.compareAndSet(null, ticket)) {
            onBusy()
            return
        }
        try {
            val work = prepare()
            start {
                try { work() }
                finally { inFlight.compareAndSet(ticket, null) }
            }
        } catch (failure: Throwable) {
            // Includes allocation/show failures and Thread.start() failing before the worker runs.
            inFlight.compareAndSet(ticket, null)
            throw failure
        }
    }
}
