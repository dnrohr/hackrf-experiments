package dev.rfnotebook.app

import dev.rfnotebook.storage.DetectionEntity
import dev.rfnotebook.storage.DiscoveryDetail
import dev.rfnotebook.storage.FingerprintHintEntity
import dev.rfnotebook.storage.SignalFingerprintEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryPresentationTest {
    @Test fun `large discovery list ranks deterministically and applies all filter dimensions`() {
        val details = (0 until 500).map { index -> detail(index) }
        val filters = DiscoveryFilters(
            minimumFrequencyHz = 915_000_000,
            maximumFrequencyHz = 915_000_000,
            fromEpochMs = 100,
            toEpochMs = 1_000,
            state = "NEW",
            hint = "REPEATING_SHORT_OOK_LIKE_BURST",
            surveyId = "survey-1",
            equipmentProfileVersionId = "equipment:v1",
        )

        val result = DiscoveryPresentation.filterAndRank(details, filters)

        assertEquals(1, result.size)
        assertEquals("fingerprint-1", result.single().id)
    }

    private fun detail(index: Int): DiscoveryDetail {
        val matching = index == 1
        val fingerprint = SignalFingerprintEntity(
            id = "fingerprint-$index", equipmentProfileVersionId = if (matching) "equipment:v1" else "equipment:v2",
            nominalFrequencyHz = if (matching) 915_000_000 else 433_920_000,
            typicalBandwidthHz = 100_000, firstSeenAtEpochMs = 200, lastSeenAtEpochMs = 900,
            occurrenceCount = index + 1, dutyCycleEstimate = 0.1f, typicalBurstDurationMs = 500,
            typicalRepeatIntervalMs = 2_000, noveltyScore = 1f / (index + 1), algorithmVersion = "cluster-v1",
            state = "NEW", userLabel = "", tags = "", notes = "",
        )
        val detection = DetectionEntity(
            "d-$index", if (matching) "survey-1" else "survey-2", fingerprint.id, fingerprint.equipmentProfileVersionId,
            200, 700, fingerprint.nominalFrequencyHz, 100_000, -50f, -55f, 35f, if (matching) "fix" else null,
            "detector-v1", "DISCRETE_BURST", "",
        )
        val hint = FingerprintHintEntity(
            fingerprint.id, 0, if (matching) "REPEATING_SHORT_OOK_LIKE_BURST" else "CONTINUOUS_NARROWBAND_CARRIER",
            0.7f, "fixture evidence",
        )
        return DiscoveryDetail(fingerprint, listOf(hint), listOf(detection), emptyList())
    }
}
