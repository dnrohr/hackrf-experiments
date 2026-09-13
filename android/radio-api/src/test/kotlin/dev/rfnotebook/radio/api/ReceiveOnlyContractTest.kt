package dev.rfnotebook.radio.api

import org.junit.Assert.assertFalse
import org.junit.Test

class ReceiveOnlyContractTest {
    @Test fun `application radio surface contains no transmit operation`() {
        val methods = listOf(ReceiveOnlyRadio::class.java, RadioSession::class.java)
            .flatMap { it.methods.asIterable() }
            .map { it.name.lowercase() }
        assertFalse(methods.any { it.contains("transmit") || it == "tx" || it.startsWith("starttx") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported sample rate is rejected`() {
        RadioLimits.requireValid(RxConfig(915_000_000, 3_000_000))
    }
}
