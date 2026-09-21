package dev.rfnotebook.domain

enum class DetectionKind { DISCRETE_BURST, PERSISTENT_CARRIER }

enum class QualityFlag {
    CENTER_DC,
    SYMMETRIC_CANDIDATE,
    BROADBAND_IMPULSE,
    OVERLOAD,
    CORRUPT_FRAME,
}

data class Detection(
    val id: String,
    val surveyId: String,
    val equipmentProfileVersionId: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val centerFrequencyHz: Long,
    val bandwidthHz: Long,
    val peakPowerDbfs: Float,
    val medianPowerDbfs: Float,
    val snrDb: Float,
    val detectorVersion: String,
    val kind: DetectionKind,
    val locationFixId: String? = null,
    val qualityFlags: Set<QualityFlag> = emptySet(),
) {
    val durationMs: Long get() = (endedAtEpochMs - startedAtEpochMs).coerceAtLeast(0)
}

enum class FingerprintState { NEW, INTERESTING, IDENTIFIED, IGNORED, ARTIFACT }

enum class ClassificationCategory {
    CONTINUOUS_NARROWBAND_CARRIER,
    REPEATING_SHORT_OOK_LIKE_BURST,
    TWO_LEVEL_FSK_LIKE,
    WIDER_FSK_FAMILY,
    ANALOG_FM_LIKE,
    FREQUENCY_HOPPING_OR_UNRESOLVED,
    LIKELY_LOCAL_INTERFERENCE_OR_OVERLOAD,
}

data class ClassificationHint(
    val category: ClassificationCategory,
    val confidence: Float,
    val evidence: String,
)

data class FingerprintProvenance(
    val operation: String,
    val sourceFingerprintIds: Set<String>,
    val atEpochMs: Long,
    val explanation: String,
)

data class SignalFingerprint(
    val id: String,
    val equipmentProfileVersionId: String,
    val detectionIds: Set<String>,
    val nominalFrequencyHz: Long,
    val typicalBandwidthHz: Long,
    val firstSeenAtEpochMs: Long,
    val lastSeenAtEpochMs: Long,
    val occurrenceCount: Int,
    val dutyCycleEstimate: Float,
    val typicalBurstDurationMs: Long?,
    val typicalRepeatIntervalMs: Long?,
    val noveltyScore: Float,
    val classificationHints: List<ClassificationHint>,
    val algorithmVersion: String,
    val state: FingerprintState = FingerprintState.NEW,
    val userLabel: String = "",
    val tags: Set<String> = emptySet(),
    val notes: String = "",
    val provenance: List<FingerprintProvenance> = emptyList(),
)
