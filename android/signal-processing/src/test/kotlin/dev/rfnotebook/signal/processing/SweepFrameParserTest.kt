package dev.rfnotebook.signal.processing

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class SweepFrameParserTest {
    @Test fun `parses frequencies and power bins`() {
        val frame = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(902_000_000).putLong(904_000_000)
            .putFloat(-72.5f).putFloat(-41.25f).array()
        val parsed = SweepFrameParser.parse(frame)
        assertEquals(902_000_000, parsed.bins[0].frequencyHz)
        assertEquals(903_000_000, parsed.bins[1].frequencyHz)
        assertEquals(-41.25f, parsed.bins[1].powerDbfs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects incomplete frames`() { SweepFrameParser.parse(ByteArray(19)) }

    @Test fun `encodes a processed frame for a sanitized fixture round trip`() {
        val source = ParsedSweepFrame(
            99_000_000,
            99_500_000,
            listOf(SweepObservation(99_000_000, -72.5f), SweepObservation(99_250_000, -41.25f)),
        )

        val parsed = SweepFrameParser.parse(SweepFrameParser.encode(source))

        assertEquals(source, parsed)
    }

    @Test fun `parses sanitized Pixel 8a HackRF sweep fixture`() {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/fixtures/m0-hackrf-sweep-frame.bin")).use { it.readBytes() }

        val parsed = SweepFrameParser.parse(bytes)

        assertEquals(88_000_000L, parsed.lowFrequencyHz)
        assertEquals(88_500_000L, parsed.highFrequencyHz)
        assertEquals(5, parsed.bins.size)
        assertEquals(listOf(-87f, -69f, -64f, -63f, -69f), parsed.bins.map { it.powerDbfs })
    }
}
