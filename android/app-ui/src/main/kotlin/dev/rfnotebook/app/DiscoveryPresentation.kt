package dev.rfnotebook.app

import dev.rfnotebook.storage.DiscoveryDetail
import dev.rfnotebook.storage.SignalFingerprintEntity
import dev.rfnotebook.storage.SurveyEntity

enum class DiscoveryPhase { EMPTY, PROCESSING, CONTENT, PARTIAL, FAILED }

data class DiscoveryFilters(
    val minimumFrequencyHz: Long? = null,
    val maximumFrequencyHz: Long? = null,
    val fromEpochMs: Long? = null,
    val toEpochMs: Long? = null,
    val state: String? = null,
    val hint: String? = null,
    val surveyId: String? = null,
    val equipmentProfileVersionId: String? = null,
)

data class DiscoveryUiState(
    val phase: DiscoveryPhase,
    val fingerprints: List<SignalFingerprintEntity> = emptyList(),
    val details: List<DiscoveryDetail> = emptyList(),
    val problem: String? = null,
    val reprocessableSurveys: List<SurveyEntity> = emptyList(),
)

object DiscoveryPresentation {
    fun filterAndRank(
        details: List<DiscoveryDetail>,
        filters: DiscoveryFilters,
    ): List<SignalFingerprintEntity> = details.filter { detail ->
        val fingerprint = detail.fingerprint
        (filters.minimumFrequencyHz == null || fingerprint.nominalFrequencyHz >= filters.minimumFrequencyHz) &&
            (filters.maximumFrequencyHz == null || fingerprint.nominalFrequencyHz <= filters.maximumFrequencyHz) &&
            (filters.fromEpochMs == null || fingerprint.lastSeenAtEpochMs >= filters.fromEpochMs) &&
            (filters.toEpochMs == null || fingerprint.firstSeenAtEpochMs <= filters.toEpochMs) &&
            (filters.state == null || fingerprint.state == filters.state) &&
            (filters.hint == null || detail.hints.any { it.category == filters.hint }) &&
            (filters.surveyId == null || detail.detections.any { it.surveyId == filters.surveyId }) &&
            (filters.equipmentProfileVersionId == null || fingerprint.equipmentProfileVersionId == filters.equipmentProfileVersionId)
    }.sortedWith(
        compareByDescending<DiscoveryDetail> { score(it) }
            .thenByDescending { it.fingerprint.lastSeenAtEpochMs }
            .thenBy { it.fingerprint.id },
    ).map { it.fingerprint }

    private fun score(detail: DiscoveryDetail): Double {
        val strength = detail.detections.maxOfOrNull { it.snrDb }?.toDouble() ?: 0.0
        val recurrence = detail.fingerprint.occurrenceCount.coerceAtMost(20) / 20.0
        val latitudeSpan = detail.fingerprint.maximumLatitude?.let { maximum ->
            detail.fingerprint.minimumLatitude?.let { minimum -> (maximum - minimum).coerceAtLeast(0.0) }
        }
        val longitudeSpan = detail.fingerprint.maximumLongitude?.let { maximum ->
            detail.fingerprint.minimumLongitude?.let { minimum -> (maximum - minimum).coerceAtLeast(0.0) }
        }
        val geographicSpecificity = if (detail.fingerprint.locatedObservationCount > 0 && latitudeSpan != null && longitudeSpan != null) {
            1.0 / (1.0 + (latitudeSpan + longitudeSpan) * 1_000.0)
        } else 0.0
        return detail.fingerprint.noveltyScore * 3.0 + strength / 40.0 + recurrence + geographicSpecificity * 0.25
    }
}
