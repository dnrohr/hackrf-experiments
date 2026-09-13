package dev.rfnotebook.signal.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HackrfSweepProcessorTest {
    @Test fun `extracts frequency headers and power bins from device block`() {
        val block = ByteArray(16_384)
        block[0] = 0x7f
        block[1] = 0x7f
        writeLittleEndianLong(block, 2, 100_000_000L)
        repeat(20) { sample -> block[block.size - 40 + sample * 2] = if (sample % 2 == 0) 64 else -64 }

        val result = HackrfSweepProcessor.process(block, sampleRateHz = 2_000_000, binWidthHz = 100_000)

        assertEquals(0, result.malformedBlocks)
        assertEquals(2, result.frames.size)
        assertEquals(100_000_000L, result.frames[0].lowFrequencyHz)
        assertEquals(100_500_000L, result.frames[0].highFrequencyHz)
        assertEquals(5, result.frames[0].bins.size)
        assertTrue(result.frames.flatMap { it.bins }.all { it.powerDbfs.isFinite() })
    }

    @Test fun `counts invalid magic without inventing observations`() {
        val result = HackrfSweepProcessor.process(ByteArray(16_384), 2_000_000, 100_000)
        assertEquals(1, result.malformedBlocks)
        assertTrue(result.frames.isEmpty())
    }

    private fun writeLittleEndianLong(bytes: ByteArray, offset: Int, value: Long) {
        repeat(8) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
