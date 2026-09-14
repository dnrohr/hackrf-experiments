package dev.rfnotebook.signal.processing

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SweepObservation(val frequencyHz: Long, val powerDbfs: Float)
data class ParsedSweepFrame(val lowFrequencyHz: Long, val highFrequencyHz: Long, val bins: List<SweepObservation>)

/** Parses the 16-byte hackrf_sweep header followed by little-endian float bins. */
object SweepFrameParser {
    const val HEADER_BYTES = 16

    fun encode(frame: ParsedSweepFrame): ByteArray {
        require(frame.lowFrequencyHz in 1_000_000 until frame.highFrequencyHz && frame.highFrequencyHz <= 6_000_000_000L) {
            "Invalid sweep range"
        }
        require(frame.bins.isNotEmpty()) { "Sweep frame has no bins" }
        require(frame.bins.all { it.powerDbfs.isFinite() }) { "Non-finite power bin" }
        val buffer = ByteBuffer.allocate(HEADER_BYTES + frame.bins.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putLong(frame.lowFrequencyHz)
        buffer.putLong(frame.highFrequencyHz)
        frame.bins.forEach { buffer.putFloat(it.powerDbfs) }
        return buffer.array()
    }

    fun parse(frame: ByteArray): ParsedSweepFrame {
        require(frame.size >= HEADER_BYTES && (frame.size - HEADER_BYTES) % Float.SIZE_BYTES == 0) {
            "Malformed sweep frame length: ${frame.size}"
        }
        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        val low = buffer.long
        val high = buffer.long
        require(low in 1_000_000 until high && high <= 6_000_000_000L) { "Invalid sweep range" }
        val count = (frame.size - HEADER_BYTES) / Float.SIZE_BYTES
        require(count > 0) { "Sweep frame has no bins" }
        val width = (high - low).toDouble() / count
        val bins = List(count) { index ->
            val power = buffer.float
            require(power.isFinite()) { "Non-finite power bin" }
            SweepObservation((low + width * index).toLong(), power)
        }
        return ParsedSweepFrame(low, high, bins)
    }
}
