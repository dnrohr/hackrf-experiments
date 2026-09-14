package dev.rfnotebook.signal.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class SpectrumBucketAccumulatorTest {
    @Test fun `bucket preserves exact min median max and count`() {
        val accumulator = SpectrumBucketAccumulator(1_000)
        listOf(-90f, -70f, -80f, -60f).forEachIndexed { index, power ->
            accumulator.add(
                wallTimeEpochMs = 1_000L + index,
                monotonicNs = index.toLong(),
                frame = ParsedSweepFrame(100, 200, listOf(SweepObservation(150, power))),
            )
        }

        val result = accumulator.flushAll().single()

        assertEquals(-90f, result.minimumPowerDbfs)
        assertEquals(-75f, result.medianPowerDbfs)
        assertEquals(-60f, result.maximumPowerDbfs)
        assertEquals(4, result.sampleCount)
    }

    @Test fun `advancing time flushes only completed buckets`() {
        val accumulator = SpectrumBucketAccumulator(1_000)
        accumulator.add(1_500, 1, ParsedSweepFrame(100, 200, listOf(SweepObservation(150, -80f))))

        val completed = accumulator.add(2_000, 2, ParsedSweepFrame(100, 200, listOf(SweepObservation(150, -70f))))

        assertEquals(1_000L, completed.single().timeBucketStartEpochMs)
        assertEquals(2_000L, accumulator.flushAll().single().timeBucketStartEpochMs)
    }
}
