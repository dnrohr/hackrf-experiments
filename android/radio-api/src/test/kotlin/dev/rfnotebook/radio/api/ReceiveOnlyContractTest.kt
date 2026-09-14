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

    @Test fun `default radio settings keep both powered features off`() {
        val rx = RxConfig(915_000_000, 4_000_000)
        val sweep = SweepConfig(902_000_000, 928_000_000, 100_000, 4_000_000)

        assertFalse(rx.rfAmpEnabled)
        assertFalse(rx.antennaPowerEnabled)
        assertFalse(sweep.rfAmpEnabled)
        assertFalse(sweep.antennaPowerEnabled)
    }

    @Test(expected = RadioException::class)
    fun `invalid hardware gain step is rejected`() {
        RadioLimits.requireValid(SweepConfig(902_000_000, 928_000_000, 100_000, 4_000_000, lnaGainDb = 10))
    }

    @Test(expected = RadioException::class)
    fun `filter that does not match sample rate is rejected`() {
        RadioLimits.requireValid(
            SweepConfig(902_000_000, 928_000_000, 100_000, 2_000_000, basebandFilterHz = 3_500_000),
        )
    }

    @Test fun `multiple disjoint sweep ranges are accepted`() {
        RadioLimits.requireValid(
            SweepConfig(
                902_000_000,
                928_000_000,
                100_000,
                4_000_000,
                ranges = listOf(SweepRange(902_000_000, 910_000_000), SweepRange(920_000_000, 928_000_000)),
            ),
        )
    }

    @Test(expected = RadioException::class)
    fun `overlapping sweep ranges are rejected`() {
        RadioLimits.requireValid(
            SweepConfig(
                902_000_000,
                928_000_000,
                100_000,
                4_000_000,
                ranges = listOf(SweepRange(902_000_000, 920_000_000), SweepRange(910_000_000, 928_000_000)),
            ),
        )
    }
}
