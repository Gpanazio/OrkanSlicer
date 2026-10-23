package ru.ytkab0bp.slicebeam.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrinterUploadUrlTest {
    @Test fun buildsApiEndpointsWithOrWithoutASchemeOrTrailingSlash() {
        assertEquals("http://192.168.68.61:7125/server/files/upload",
            PrinterUploadUrl.endpoint(" 192.168.68.61:7125/ ", "/server/files/upload"))
        assertEquals("https://printer.local/moonraker/printer/print/start",
            PrinterUploadUrl.endpoint("https://printer.local/moonraker/", "/printer/print/start"))
        assertEquals("http://[::1]:7125/server/files/upload",
            PrinterUploadUrl.endpoint("http://[::1]:7125", "server/files/upload"))
    }

    @Test fun rejectsUnusableOrUnsafePrinterAddresses() {
        for (host in listOf(null, "", "  ", "ftp://printer", "http://", "http://printer:99999",
            "http://user:pass@printer", "http://printer/?key=secret", "http://printer/#section")) {
            assertNull("Accepted $host", PrinterUploadUrl.baseUrl(host))
        }
    }
}
