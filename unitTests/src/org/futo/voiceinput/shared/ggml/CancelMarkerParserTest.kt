package org.futo.voiceinput.shared.ggml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CancelMarkerParserTest {
    @Test
    fun nonCancelledTextReturnsNotCancelled() {
        val result = parseCancelMarker("hello world")
        assertTrue(result is CancelMarkerResult.NotCancelled)
    }

    @Test
    fun userCancellationDetectedWithWhitespace() {
        val result1 = parseCancelMarker("<>CANCELLED<> flag")
        val result2 = parseCancelMarker("  <>CANCELLED<> flag  ")
        assertTrue(result1 is CancelMarkerResult.CancelledByFlag)
        assertTrue(result2 is CancelMarkerResult.CancelledByFlag)
    }

    @Test
    fun bailLanguageParsed() {
        val resultEn = parseCancelMarker("<>CANCELLED<> lang=en")
        val resultEs = parseCancelMarker("<>CANCELLED<>   lang=es   ")
        assertEquals(CancelMarkerResult.BailedLanguage("en"), resultEn)
        assertEquals(CancelMarkerResult.BailedLanguage("es"), resultEs)
    }

    @Test
    fun malformedCancelledStringsReportedAsUnknown() {
        val resultEmptyLang = parseCancelMarker("<>CANCELLED<> lang=")
        val resultMarkerOnly = parseCancelMarker("<>CANCELLED<>")
        assertTrue(resultEmptyLang is CancelMarkerResult.UnknownFormat)
        assertTrue(resultMarkerOnly is CancelMarkerResult.UnknownFormat)
    }
}
