package dev.rfnotebook.radio.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiveOnlyContractTest {
    @Test fun `application radio surface contains no transmit operation`() {
        val methods = listOf(ReceiveOnlyRadio::class.java, RadioSession::class.java)
            .flatMap { it.methods.asIterable() }
            .map { it.name.lowercase() }
        assertFalse(methods.any { it.contains("transmit") || it == "tx" || it.startsWith("starttx") })
    }

    @Test(expected = RadioException::class)
    fun `unsupported sample rate is rejected`() {
        RadioLimits.requireValid(RxConfig(915_000_000, 3_000_000))
    }

    @Test fun `known usb api major and reported firmware are compatible`() {
        val result = HackrfCompatibilityPolicy.evaluate(
            RadioDeviceInfo("HackRF One", "2026.01.3", "1.10", "r9", "suffix"),
        )

        assertTrue(result.compatible)
    }

    @Test fun `unknown usb api major is rejected with explanation`() {
        val result = HackrfCompatibilityPolicy.evaluate(
            RadioDeviceInfo("HackRF One", "future", "2.0", "future", "suffix"),
        )

        assertFalse(result.compatible)
        assertTrue(result.explanation.contains("unsupported"))
    }
}
