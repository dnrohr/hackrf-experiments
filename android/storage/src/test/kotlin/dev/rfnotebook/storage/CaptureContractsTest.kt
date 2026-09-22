package dev.rfnotebook.storage

import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureContractsTest {
    @Test fun `preflight enforces duration throughput and storage reserve`() {
        assertEquals(16_000_000L, CapturePreflight.expectedBytes(1_000, 8_000_000))
        assertFalse(CapturePreflight.assess(249, 2_000_000, 8_000_000, Long.MAX_VALUE).allowed)
        assertFalse(CapturePreflight.assess(1_000, 8_000_000, 7_500_000, Long.MAX_VALUE).allowed)
        assertTrue(CapturePreflight.assess(1_000, 8_000_000, 7_600_000, Long.MAX_VALUE).allowed)
        assertFalse(CapturePreflight.assess(1_000, 8_000_000, 8_000_000, 16_000_000L).allowed)
        assertTrue(CapturePreflight.assess(1_000, 8_000_000, 8_000_000, 16_000_000L + CapturePreflight.DEFAULT_RESERVE_BYTES).allowed)
    }

    @Test fun `atomic writer finalizes exact signed interleaved IQ and hashes it`() {
        val root = createTempDirectory("capture-success").toFile()
        val metadata = CaptureMetadata.fixture(durationMs = 250, sampleRateHz = 2_000_000)
        val writer = AtomicIqCapture(root, metadata, expectedBytes = 12)
        writer.append(byteArrayOf(-128, 127, -1, 1, 4, -4, 9, -9, 20, -20, 40, -40, 99))
        val result = writer.complete()

        assertEquals(12, result.byteCount)
        assertEquals(6, result.complexSampleCount)
        assertTrue(result.iqFile.name.endsWith(".cs8"))
        assertTrue(result.sidecarFile.readText().contains("\"sampleFormat\":\"signed-int8-interleaved-iq\""))
        assertEquals(result.sha256, result.iqFile.inputStream().use(::sha256Hex))
        assertTrue(result.previewFile.readBytes().copyOfRange(0, 2).contentEquals("P5".toByteArray()))
        assertFalse(root.walkTopDown().any { it.name.endsWith(".part") })
    }

    @Test fun `gap overrun cancellation and short writes never finalize`() {
        listOf("gap", "overrun", "cancel", "short").forEach { mode ->
            val root = createTempDirectory("capture-$mode").toFile()
            val writer = AtomicIqCapture(root, CaptureMetadata.fixture(), expectedBytes = 8)
            writer.append(ByteArray(if (mode == "short") 4 else 8))
            when (mode) {
                "gap" -> writer.recordGap()
                "overrun" -> writer.recordOverrun()
                "cancel" -> writer.cancel()
            }
            val failed = runCatching { writer.complete() }.isFailure
            assertTrue("$mode must fail", failed)
            assertFalse(root.walkTopDown().any { it.extension == "cs8" || it.extension == "json" })
        }
    }

    private fun CaptureMetadata.Companion.fixture(durationMs: Long = 250, sampleRateHz: Int = 2_000_000) = CaptureMetadata(
        id = "capture-test", fingerprintId = null, surveyId = null,
        startedAtEpochMs = 1_790_000_000_000, startedMonotonicNs = 12,
        durationMs = durationMs, centerFrequencyHz = 433_920_000, sampleRateHz = sampleRateHz,
        basebandFilterHz = 1_750_000, lnaGainDb = 16, vgaGainDb = 16,
        rfAmpEnabled = false, antennaPowerEnabled = false, equipmentProfileVersionId = "equipment-v1",
        serialSuffix = "1234abcd", latitude = null, longitude = null, horizontalAccuracyM = null,
        locationAgeMs = null, note = "ambient receive-only capture", appVersion = "0.4.0-m4",
    )
}
