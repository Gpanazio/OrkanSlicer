package ru.ytkab0bp.slicebeam.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterResponseInspectorTest {
    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8)

    @Test fun moonrakerResultIsNotAnEmbeddedError() {
        assertNull(PrinterResponseInspector.embeddedError(bytes("""{"result":{"item":{"path":"test.gcode"}}}""")))
        assertNull(PrinterResponseInspector.embeddedError(bytes("""{"result":"ok","code":0}""")))
        assertNull(PrinterResponseInspector.embeddedError(bytes("""{"result":"ok","error":0}""")))
    }

    @Test fun httpSuccessWithErrorObjectDoesNotMeanPrintStarted() {
        val detail = PrinterResponseInspector.embeddedError(bytes(
            """{"error":{"code":10011703,"message":"unknown filament in extruder"}}"""))
        assertEquals("unknown filament in extruder (10011703)", detail)
    }

    @Test fun topLevelDeviceErrorCodeIsReported() {
        val detail = PrinterResponseInspector.embeddedError(bytes(
            """{"code":10011725,"message":"The device cannot parse the file"}"""))
        assertEquals("The device cannot parse the file (10011725)", detail)
    }

    @Test fun zeroErrorFieldDoesNotHideTopLevelDeviceError() {
        for (neutralError in listOf("0", "false", "\"\"", "{\"code\":0,\"message\":\"ok\"}")) {
            val detail = PrinterResponseInspector.embeddedError(bytes(
                """{"error":$neutralError,"code":10011725,"message":"The device cannot parse the file"}"""))
            assertEquals("The device cannot parse the file (10011725)", detail)
        }
    }

    @Test fun zeroErrorObjectWithoutDeviceCodeIsSuccess() {
        assertNull(PrinterResponseInspector.embeddedError(bytes(
            """{"error":{"code":0,"message":"ok"},"result":{"filename":"test.gcode"}}""")))
    }

    @Test fun explicitRejectionIsNotACompletedUpload() {
        assertEquals("Printer busy", PrinterResponseInspector.embeddedError(bytes(
            """{"success":false,"message":"Printer busy","code":0}""")))
        assertEquals("@upload_rejected", PrinterResponseInspector.embeddedError(bytes(
            """{"success":false,"result":null}""")))
        assertNull(PrinterResponseInspector.embeddedError(bytes("""{"success":true,"result":"ok"}""")))
    }

    @Test fun stringErrorAndMalformedFailureBodiesRemainReadable() {
        assertEquals("Unable to start", PrinterResponseInspector.embeddedError(bytes("""{"error":"Unable to start"}""")))
        assertEquals("Bad gateway", PrinterResponseInspector.failureDetail(bytes("Bad gateway"), "fallback"))
        assertEquals("fallback", PrinterResponseInspector.failureDetail(null, "fallback"))
    }

    @Test fun longDeviceResponsesAreBounded() {
        val detail = PrinterResponseInspector.failureDetail(bytes("x".repeat(5000)), "fallback")
        assertEquals(500, detail.length)
        assertFalse(detail.contains("fallback"))
        assertTrue(detail.all { it == 'x' })
    }

    @Test fun htmlFromPrinterApiIsExplainedWithoutClaimingPrintStarted() {
        val fallback = "No upload confirmation"
        assertTrue(PrinterResponseInspector.missingAcknowledgement(
            bytes("<html><body><center>200 OK</center></body></html>"), fallback).contains("@upload_api_html"))
        assertEquals(fallback, PrinterResponseInspector.missingAcknowledgement(bytes("{}"), fallback))
    }

    @Test fun startTimeoutExplainsThatPrinterStatusMustBeCheckedBeforeRetry() {
        val detail = PrinterResponseInspector.printStartFailureDetail(null, "Read timed out")
        assertTrue(detail.contains("@upload_start_uncertain"))
        assertTrue(detail.contains("Read timed out"))
        assertEquals("@upload_start_rejected Printer busy", PrinterResponseInspector.printStartFailureDetail(
            bytes("""{"error":"Printer busy"}"""), "Read timed out"))
    }

    @Test fun oversizedPrinterResponseCannotBeParsedOrCopiedIntoAnErrorDialog() {
        val huge = bytes("<html>" + "x".repeat(300_000))
        assertNull(PrinterResponseInspector.embeddedError(huge))
        assertTrue(PrinterResponseInspector.failureDetail(huge, "fallback").contains("@upload_response_large"))
        assertTrue(PrinterResponseInspector.missingAcknowledgement(huge, "fallback").contains("@upload_api_html"))
    }
    @Test fun uncertainMessageIsSingleAndIncludesObservedPrinterState() {
        assertEquals("@upload_start_uncertain @upload_printer_state standby",
            PrinterResponseInspector.printStartFailureDetail(null, null, "standby"))
        assertEquals("@upload_start_uncertain timeout @upload_printer_state printing",
            PrinterResponseInspector.printStartFailureDetail(null, "timeout", "printing"))
    }

}
