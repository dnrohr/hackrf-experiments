package dev.rfnotebook.signal.processing

import dev.rfnotebook.domain.DetectionKind
import dev.rfnotebook.domain.QualityFlag
import dev.rfnotebook.domain.ClassificationCategory
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureEvaluationTest {
    private val config = DetectorConfig(persistentAfterMs = 10_000)

    @Test fun `golden fixtures meet detection and artifact expectations`() {
        assertTrue(run(SyntheticFixtureGenerator.noiseOnly()).isEmpty())
        assertEquals(DetectionKind.PERSISTENT_CARRIER, run(SyntheticFixtureGenerator.continuousCarrier()).single().kind)
        assertEquals(3, run(SyntheticFixtureGenerator.repeatingOok()).size)
        val fskFingerprint = FingerprintClusterer(FingerprintClusteringConfig()).cluster(
            run(SyntheticFixtureGenerator.twoLevelFsk()),
        ).single()
        assertTrue(fskFingerprint.classificationHints.any { it.category == ClassificationCategory.TWO_LEVEL_FSK_LIKE })
        assertTrue(run(SyntheticFixtureGenerator.centerArtifact()).single().qualityFlags.contains(QualityFlag.CENTER_DC))
        val overload = run(SyntheticFixtureGenerator.broadbandOverload()).single()
        assertTrue(overload.qualityFlags.contains(QualityFlag.BROADBAND_IMPULSE))
        assertTrue(overload.qualityFlags.contains(QualityFlag.OVERLOAD))
        assertTrue(run(SyntheticFixtureGenerator.missingAndReordered()).any { QualityFlag.CORRUPT_FRAME in it.qualityFlags })
    }

    @Test fun `uniform higher startup floor does not create sustained false detections`() {
        val frames = SyntheticFixtureGenerator.noiseOnly().map { frame ->
            frame.copy(bins = frame.bins.map { it.copy(medianPowerDbfs = -75f, maximumPowerDbfs = -75f) })
        }
        assertTrue(run(frames).isEmpty())
    }

    @Test fun `normalized evaluation output is invariant to shuffled input`() {
        val fixtures = SyntheticFixtureGenerator.repeatingOok() + SyntheticFixtureGenerator.twoLevelFsk()
        val expected = normalize(run(fixtures))

        repeat(2) { seed ->
            assertEquals(expected, normalize(run(fixtures.shuffled(Random(seed)))))
        }
    }

    @Test fun `evaluation workload records runtime and heap delta`() {
        val frames = SyntheticFixtureGenerator.noiseOnly(60) +
            SyntheticFixtureGenerator.continuousCarrier(60) + SyntheticFixtureGenerator.repeatingOok()
        val runtime = Runtime.getRuntime()
        System.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        val started = System.nanoTime()
        var detectionCount = 0
        repeat(100) { detectionCount += run(frames).size }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        val heapDeltaBytes = ((runtime.totalMemory() - runtime.freeMemory()) - before).coerceAtLeast(0)
        println("M2_EVALUATION frames=${frames.size * 100} detections=$detectionCount runtimeMs=$elapsedMs heapDeltaBytes=$heapDeltaBytes")
        assertTrue(detectionCount > 0)
    }

    private fun run(frames: List<AggregateFrame>) = DetectionPipeline(config).process(frames)
    private fun normalize(detections: List<dev.rfnotebook.domain.Detection>) = detections.map {
        listOf(it.startedAtEpochMs, it.endedAtEpochMs, it.centerFrequencyHz, it.bandwidthHz, it.kind.name, it.qualityFlags.sortedBy(Enum<*>::name).joinToString())
    }
}
