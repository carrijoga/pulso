package dev.pulso.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCaptureTest {
    @Test fun appendsToEmptyField() = assertEquals("comprar pão", appendDictation("", "  comprar pão "))
    @Test fun appendsWithSingleSpace() = assertEquals("ligar para Ana e pagar luz", appendDictation("ligar para Ana  ", "e pagar luz"))
    @Test fun blankFieldIgnoresWhitespace() = assertEquals("oi", appendDictation("   ", "oi"))
    @Test fun truncatesAtCaptureLimit() = assertEquals(500, appendDictation("a".repeat(495), "bbbbbbbbbb").length)
}
