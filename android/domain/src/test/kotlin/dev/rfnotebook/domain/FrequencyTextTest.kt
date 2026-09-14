package dev.rfnotebook.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FrequencyTextTest {
    @Test fun `accepts unambiguous frequency units`() {
        assertEquals(915_000_000L, FrequencyText.parseHz("915 MHz"))
        assertEquals(433_920_000L, FrequencyText.parseHz("433.92mhz"))
        assertEquals(2_400_000_000L, FrequencyText.parseHz("2.4 GHz"))
        assertEquals(100_000L, FrequencyText.parseHz("100 kHz"))
        assertEquals(123L, FrequencyText.parseHz("123"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects ambiguous prose`() {
        FrequencyText.parseHz("around 915")
    }
}
