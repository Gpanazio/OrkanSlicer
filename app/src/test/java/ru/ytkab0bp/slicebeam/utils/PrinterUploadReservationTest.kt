package ru.ytkab0bp.slicebeam.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class PrinterUploadReservationTest {
    @Test fun onlyOneRequestCanReserveAnOriginUntilItIsReleased() {
        val reservations = PrinterUploadReservation()
        val reservation = reservations.reserve(" printer.local:7125/ ")
        assertNotNull(reservation)
        assertNull(reservations.reserve("http://PRINTER.local:7125"))
        assertNotNull(reservations.reserve("http://printer.local:7126"))

        reservations.release(reservation!!)
        assertEquals(reservation, reservations.reserve("http://printer.local:7125"))
    }
    @Test fun proxyPathsAndDefaultPortsShareTheOrigin() {
        val reservations = PrinterUploadReservation()
        val reservation = reservations.reserve("PRINTER.local/proxy-A")!!
        assertNull(reservations.reserve("http://printer.local:80/proxy-B"))
        reservations.release(reservation)
        assertNotNull(reservations.reserve("http://printer.local:80/proxy-B"))
        assertNull(reservations.reserve("http://printer.local/"))
        assertNotNull(reservations.reserve("https://printer.local/"))
    }
    @Test fun invalidHostsAreNotReserved() {
        val reservations = PrinterUploadReservation()
        assertNull(reservations.reserve("http://"))
        assertNull(reservations.reserve("http://user:secret@printer"))
    }
}
