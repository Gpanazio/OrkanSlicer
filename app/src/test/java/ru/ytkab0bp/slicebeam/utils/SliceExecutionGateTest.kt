package ru.ytkab0bp.slicebeam.utils

import org.junit.Assert.*
import org.junit.Test

class SliceExecutionGateTest {
    @Test fun concurrentRequestIsReportedAndCannotWriteUntilFirstFinishes() {
        val gate = SliceExecutionGate()
        lateinit var pending: () -> Unit
        var busy = 0
        var writes = 0
        gate.launch({ busy++ }, { { writes++ } }, { pending = it })
        gate.launch({ busy++ }, { fail("Concurrent preparation must not run"); {} }, { it() })
        assertEquals(1, busy)
        assertEquals(0, writes)
        pending()
        gate.launch({ busy++ }, { { writes++ } }, { it() })
        assertEquals(2, writes)
        assertEquals(1, busy)
    }

    @Test fun releasesAfterPreparationOrThreadStartThrowsAnyThrowable() {
        for (failure in listOf(IllegalStateException("setup"), OutOfMemoryError("setup"), LinkageError("JNI"))) {
            for (failInPreparation in listOf(true, false)) {
                val gate = SliceExecutionGate()
                try {
                    gate.launch({ fail("Unexpected busy") }, {
                        if (failInPreparation) throw failure
                        {}
                    }, { throw failure })
                    fail("Expected failure")
                } catch (actual: Throwable) { assertSame(failure, actual) }
                var ran = false
                gate.launch({ fail("Stuck after setup/start failure") }, { { ran = true } }, { it() })
                assertTrue(ran)
            }
        }
    }

    @Test fun releasesInFinallyAfterWorkerThrowsIncludingOutOfMemory() {
        for (failure in listOf(IllegalStateException("slice"), OutOfMemoryError("slice"), LinkageError("JNI"))) {
            val gate = SliceExecutionGate()
            lateinit var pending: () -> Unit
            gate.launch({ fail("Unexpected busy") }, { { throw failure } }, { pending = it })
            try { pending(); fail("Expected failure") }
            catch (actual: Throwable) { assertSame(failure, actual) }
            var ran = false
            gate.launch({ fail("Stuck after worker failure") }, { { ran = true } }, { it() })
            assertTrue(ran)
        }
    }
}
