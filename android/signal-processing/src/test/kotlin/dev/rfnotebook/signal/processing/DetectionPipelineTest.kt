package dev.rfnotebook.signal.processing

import dev.rfnotebook.domain.DetectionKind
import dev.rfnotebook.domain.QualityFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionPipelineTest {
    private val config = DetectorConfig(
        thresholdSnrDb = 8f,
        minimumBandwidthHz = 100_000,
        binWidthHz = 100_000,
        closeGapMs = 1_500,
        persistentAfterMs = 4_000,
    )

    @Test fun `persistent carrier remains detectable after estimator warmup`() {
        val frames = buildList {
            repeat(8) { second ->
                add(frame(second * 1_000L, mapOf(914_900_000L to -90f, 915_000_000L to -55f, 915_100_000L to -90f)))
            }
        }

        val detections = DetectionPipeline(config).process(frames)

        assertEquals(1, detections.size)
        assertEquals(DetectionKind.PERSISTENT_CARRIER, detections.single().kind)
        assertTrue(detections.single().snrDb >= 8f)
    }

    @Test fun `bursts merge across adjacent frames and close after bounded gap`() {
        val frames = listOf(
            frame(0, mapOf(915_000_000L to -55f, 915_100_000L to -54f)),
            frame(1_000, mapOf(915_000_000L to -56f, 915_100_000L to -55f)),
            frame(3_000, emptyMap()),
            frame(6_000, mapOf(915_000_000L to -53f, 915_100_000L to -52f)),
        )

        val detections = DetectionPipeline(config).process(frames)

        assertEquals(2, detections.size)
        assertEquals(2_000L, detections.first().durationMs)
        assertEquals(6_000L, detections.last().startedAtEpochMs)
    }

    @Test fun `artifact and corrupt frame evidence is retained`() {
        val frames = listOf(
            frame(0, mapOf(914_900_000L to -45f, 915_000_000L to -42f, 915_100_000L to -45f), center = 915_000_000L),
            frame(1_000, (0..9).associate { (914_500_000L + it * 100_000) to -40f }, corrupt = true),
        )

        val detections = DetectionPipeline(config.copy(broadbandFraction = 0.7f)).process(frames)

        assertTrue(detections.any { QualityFlag.CENTER_DC in it.qualityFlags })
        assertTrue(detections.any { QualityFlag.BROADBAND_IMPULSE in it.qualityFlags })
        assertTrue(detections.any { QualityFlag.CORRUPT_FRAME in it.qualityFlags })
        assertTrue(detections.any { QualityFlag.OVERLOAD in it.qualityFlags })
        assertFalse(detections.isEmpty())
    }

    @Test fun `symmetric candidates are flagged without deletion`() {
        val detections = DetectionPipeline(config).process(listOf(frame(
            0,
            mapOf(914_800_000L to -45f, 915_200_000L to -45f),
            center = 915_000_000L,
        )))

        assertEquals(2, detections.size)
        assertTrue(detections.all { QualityFlag.SYMMETRIC_CANDIDATE in it.qualityFlags })
    }

    private fun frame(
        time: Long,
        signals: Map<Long, Float>,
        center: Long? = null,
        corrupt: Boolean = false,
    ) = AggregateFrame(
        surveyId = "survey",
        equipmentProfileVersionId = "equipment:v1",
        timeBucketStartEpochMs = time,
        bins = (914_500_000L..915_400_000L step 100_000L).map { frequency ->
            AggregateBin(frequency, signals[frequency] ?: -90f, signals[frequency] ?: -90f, 10)
        },
        hardwareCenterHz = center,
        corruptOrIncomplete = corrupt,
    )
}
