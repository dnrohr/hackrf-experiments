package dev.rfnotebook.signal.processing

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

data class SweepProcessResult(val frames: List<ParsedSweepFrame>, val malformedBlocks: Int)

/** Converts raw HackRF sweep transfer blocks into the same frequency/power model used by the M0 fixture parser. */
object HackrfSweepProcessor {
    private const val BYTES_PER_BLOCK = 16_384
    private const val HEADER_BYTES = 10
    private const val MAX_DFT_BINS = 1_024

    fun process(transfer: ByteArray, sampleRateHz: Int, binWidthHz: Int): SweepProcessResult {
        require(transfer.isNotEmpty() && transfer.size % BYTES_PER_BLOCK == 0) { "Malformed sweep transfer length" }
        require(binWidthHz > 0 && sampleRateHz % binWidthHz == 0) { "Bin width must divide sample rate" }
        val fftBins = sampleRateHz / binWidthHz
        require(fftBins in 4..MAX_DFT_BINS && fftBins % 4 == 0) { "Sweep DFT bins must be divisible by four and at most $MAX_DFT_BINS" }
        require(fftBins * 2 <= BYTES_PER_BLOCK - HEADER_BYTES) { "Sweep block does not contain the requested IQ window" }

        val frames = mutableListOf<ParsedSweepFrame>()
        var malformed = 0
        for (blockOffset in transfer.indices step BYTES_PER_BLOCK) {
            if ((transfer[blockOffset].toInt() and 0xff) != 0x7f ||
                (transfer[blockOffset + 1].toInt() and 0xff) != 0x7f
            ) {
                malformed++
                continue
            }
            val center = littleEndianLong(transfer, blockOffset + 2)
            if (center !in 1_000_000L..6_000_000_000L) {
                malformed++
                continue
            }
            val powers = dftPowers(transfer, blockOffset + BYTES_PER_BLOCK - fftBins * 2, fftBins)
            val quarter = fftBins / 4
            frames += frame(center, center + sampleRateHz / 4L, powers, 1 + fftBins * 5 / 8, quarter)
            frames += frame(center + sampleRateHz / 2L, center + sampleRateHz * 3L / 4L, powers, 1 + fftBins / 8, quarter)
        }
        return SweepProcessResult(frames, malformed)
    }

    private fun frame(low: Long, high: Long, powers: DoubleArray, offset: Int, count: Int): ParsedSweepFrame {
        val width = (high - low).toDouble() / count
        val bins = List(count) { index -> SweepObservation((low + width * index).toLong(), powers[offset + index].toFloat()) }
        return ParsedSweepFrame(low, high, bins)
    }

    private fun dftPowers(bytes: ByteArray, offset: Int, count: Int): DoubleArray {
        val result = DoubleArray(count)
        for (frequencyBin in 0 until count) {
            var real = 0.0
            var imaginary = 0.0
            for (sample in 0 until count) {
                val window = if (count == 1) 1.0 else 0.5 - 0.5 * cos(2.0 * PI * sample / (count - 1))
                val i = bytes[offset + sample * 2].toDouble() / 128.0 * window
                val q = bytes[offset + sample * 2 + 1].toDouble() / 128.0 * window
                val angle = -2.0 * PI * frequencyBin * sample / count
                real += i * cos(angle) - q * sin(angle)
                imaginary += i * sin(angle) + q * cos(angle)
            }
            val magnitudeSquared = (real * real + imaginary * imaginary) / (count.toDouble() * count)
            result[frequencyBin] = 10.0 * log10(magnitudeSquared.coerceAtLeast(1e-24))
        }
        return result
    }

    private fun littleEndianLong(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (index in 0 until 8) value = value or ((bytes[offset + index].toLong() and 0xff) shl (index * 8))
        return value
    }
}
