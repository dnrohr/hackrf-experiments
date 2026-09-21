package dev.rfnotebook.signal.processing

import dev.rfnotebook.domain.Detection
import dev.rfnotebook.domain.DetectionKind
import dev.rfnotebook.domain.FingerprintState
import dev.rfnotebook.domain.ClassificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintClustererTest {
    @Test fun `clustering is deterministic across input order and separates equipment`() {
        val detections = listOf(
            detection("b", "equipment:v1", 2_000, 915_020_000),
            detection("a", "equipment:v1", 0, 915_000_000),
            detection("c", "equipment:v2", 1_000, 915_010_000),
        )
        val clusterer = FingerprintClusterer(FingerprintClusteringConfig())

        val first = clusterer.cluster(detections)
        val second = clusterer.cluster(detections.reversed())

        assertEquals(first, second)
        assertEquals(2, first.size)
        assertNotEquals(first[0].equipmentProfileVersionId, first[1].equipmentProfileVersionId)
    }

    @Test fun `split and merge retain detections and provenance`() {
        val clustered = FingerprintClusterer(FingerprintClusteringConfig()).cluster(
            listOf(detection("a", "equipment:v1", 0, 915_000_000), detection("b", "equipment:v1", 2_000, 915_020_000)),
        ).single()

        val split = FingerprintCorrections.split(clustered, setOf("b"), 10_000)
        val merged = FingerprintCorrections.merge(split.toList(), 11_000)

        assertEquals(setOf("a", "b"), merged.detectionIds)
        assertEquals("MERGE", merged.provenance.last().operation)
        assertEquals(FingerprintState.NEW, merged.state)
    }

    @Test fun `fingerprint id remains stable when a later comparable detection arrives`() {
        val clusterer = FingerprintClusterer(FingerprintClusteringConfig())
        val initial = clusterer.cluster(listOf(detection("a", "equipment:v1", 0, 915_000_000))).single()
        val updated = clusterer.cluster(listOf(
            detection("a", "equipment:v1", 0, 915_000_000),
            detection("b", "equipment:v1", 5_000, 915_020_000),
        )).single()

        assertEquals(initial.id, updated.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `manual merge rejects incomparable equipment`() {
        val values = FingerprintClusterer(FingerprintClusteringConfig()).cluster(listOf(
            detection("a", "equipment:v1", 0, 915_000_000),
            detection("b", "equipment:v2", 0, 915_000_000),
        ))
        FingerprintCorrections.merge(values, 1_000)
    }

    @Test fun `persistent and burst temporal behavior do not cluster together`() {
        val burst = detection("a", "equipment:v1", 0, 915_000_000)
        val persistent = detection("b", "equipment:v1", 0, 915_000_000).copy(
            endedAtEpochMs = 20_000,
            kind = DetectionKind.PERSISTENT_CARRIER,
        )

        val fingerprints = FingerprintClusterer(FingerprintClusteringConfig()).cluster(listOf(burst, persistent))

        assertEquals(2, fingerprints.size)
        assertEquals(2, fingerprints.map { it.id }.distinct().size)
    }

    @Test fun `duration-separated clusters in one frequency bucket have distinct ids`() {
        val short = detection("a", "equipment:v1", 0, 915_000_000)
        val long = detection("b", "equipment:v1", 2_000, 915_000_000).copy(endedAtEpochMs = 5_500)

        val fingerprints = FingerprintClusterer(FingerprintClusteringConfig()).cluster(listOf(short, long))

        assertEquals(2, fingerprints.size)
        assertEquals(2, fingerprints.map { it.id }.distinct().size)
    }

    @Test fun `two recurring frequency levels produce cautious FSK hint with evidence`() {
        val result = FingerprintClusterer(FingerprintClusteringConfig()).cluster(listOf(
            detection("a", "equipment:v1", 0, 914_900_000),
            detection("b", "equipment:v1", 2_000, 915_100_000),
        )).single()

        val hint = result.classificationHints.first { it.category == ClassificationCategory.TWO_LEVEL_FSK_LIKE }
        assertTrue(hint.confidence in 0f..1f)
        assertTrue(hint.evidence.contains("two aggregate center-frequency levels"))
    }

    @Test fun `manual merge preserves user labels tags and notes`() {
        val sources = FingerprintClusterer(FingerprintClusteringConfig()).cluster(listOf(
            detection("a", "equipment:v1", 0, 914_000_000),
            detection("b", "equipment:v1", 0, 916_000_000),
        )).mapIndexed { index, fingerprint -> fingerprint.copy(
            userLabel = "label-$index",
            tags = setOf("tag-$index"),
            notes = "note-$index",
        ) }

        val merged = FingerprintCorrections.merge(sources, 1_000)

        assertEquals("label-0 / label-1", merged.userLabel)
        assertEquals(setOf("tag-0", "tag-1"), merged.tags)
        assertTrue(merged.notes.contains("note-0") && merged.notes.contains("note-1"))
    }

    private fun detection(id: String, equipment: String, time: Long, frequency: Long) = Detection(
        id = id, surveyId = "survey", equipmentProfileVersionId = equipment,
        startedAtEpochMs = time, endedAtEpochMs = time + 500,
        centerFrequencyHz = frequency, bandwidthHz = 100_000,
        peakPowerDbfs = -50f, medianPowerDbfs = -55f, snrDb = 35f,
        detectorVersion = "detector-v1", kind = DetectionKind.DISCRETE_BURST,
    )
}
