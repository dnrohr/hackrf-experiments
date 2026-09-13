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
}
