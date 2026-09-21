package dev.rfnotebook.signal.processing

import dev.rfnotebook.domain.ClassificationCategory
import dev.rfnotebook.domain.ClassificationHint
import dev.rfnotebook.domain.Detection
import dev.rfnotebook.domain.DetectionKind
import dev.rfnotebook.domain.FingerprintProvenance
import dev.rfnotebook.domain.QualityFlag
import dev.rfnotebook.domain.SignalFingerprint
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.abs

data class FingerprintClusteringConfig(
    val version: String = "cluster-v1",
    val frequencyToleranceHz: Long = 250_000,
    val bandwidthToleranceFraction: Float = 0.75f,
    val durationToleranceFraction: Float = 2.0f,
)

class FingerprintClusterer(private val config: FingerprintClusteringConfig) {
    fun cluster(input: List<Detection>): List<SignalFingerprint> {
        val clusters = mutableListOf<MutableList<Detection>>()
        input.sortedWith(compareBy<Detection> { it.equipmentProfileVersionId }.thenBy { it.centerFrequencyHz }.thenBy { it.startedAtEpochMs }.thenBy { it.id })
            .forEach { detection ->
                val match = clusters.firstOrNull { cluster -> comparable(cluster, detection) }
                if (match == null) clusters += mutableListOf(detection) else match += detection
            }
        return clusters.map(::summarize).sortedBy { it.id }
    }

    private fun comparable(cluster: List<Detection>, detection: Detection): Boolean {
        val reference = cluster.first()
        if (reference.equipmentProfileVersionId != detection.equipmentProfileVersionId) return false
        if (reference.kind != detection.kind) return false
        val frequency = cluster.map { it.centerFrequencyHz }.average()
        val bandwidth = cluster.map { it.bandwidthHz }.average().coerceAtLeast(1.0)
        val duration = cluster.map { it.durationMs }.average().coerceAtLeast(1.0)
        return abs(detection.centerFrequencyHz - frequency) <= config.frequencyToleranceHz &&
            abs(detection.bandwidthHz - bandwidth) / bandwidth <= config.bandwidthToleranceFraction &&
            abs(detection.durationMs - duration) / duration <= config.durationToleranceFraction
    }

    private fun summarize(detections: List<Detection>): SignalFingerprint {
        val sorted = detections.sortedWith(compareBy<Detection> { it.startedAtEpochMs }.thenBy { it.id })
        val nominalFrequency = sorted.map { it.centerFrequencyHz }.average().toLong()
        val typicalBandwidth = medianLong(sorted.map { it.bandwidthHz })
        val frequencyBucket = nominalFrequency / config.frequencyToleranceHz
        val bandwidthBucket = typicalBandwidth / config.frequencyToleranceHz.coerceAtLeast(1)
        val anchorDuration = sorted.first().durationMs.coerceAtLeast(1)
        val durationMagnitude = 63 - java.lang.Long.numberOfLeadingZeros(anchorDuration)
        val stableKey = "${config.version}:${sorted.first().equipmentProfileVersionId}:${sorted.first().kind}:$frequencyBucket:$bandwidthBucket:$durationMagnitude"
        val first = sorted.minOf { it.startedAtEpochMs }
        val last = sorted.maxOf { it.endedAtEpochMs }
        val span = (last - first).coerceAtLeast(1)
        val intervals = sorted.zipWithNext().map { (a, b) -> b.startedAtEpochMs - a.startedAtEpochMs }.filter { it > 0 }
        return SignalFingerprint(
            id = UUID.nameUUIDFromBytes(stableKey.toByteArray(StandardCharsets.UTF_8)).toString(),
            equipmentProfileVersionId = sorted.first().equipmentProfileVersionId,
            detectionIds = sorted.map { it.id }.toSet(),
            nominalFrequencyHz = nominalFrequency,
            typicalBandwidthHz = typicalBandwidth,
            firstSeenAtEpochMs = first,
            lastSeenAtEpochMs = last,
            occurrenceCount = sorted.size,
            dutyCycleEstimate = (sorted.sumOf { it.durationMs }.toFloat() / span).coerceIn(0f, 1f),
            typicalBurstDurationMs = sorted.filter { it.kind == DetectionKind.DISCRETE_BURST }.map { it.durationMs }.takeIf { it.isNotEmpty() }?.let(::medianLong),
            typicalRepeatIntervalMs = intervals.takeIf { it.isNotEmpty() }?.let(::medianLong),
            noveltyScore = (1f / sorted.size).coerceIn(0f, 1f),
            classificationHints = hints(sorted),
            algorithmVersion = config.version,
        )
    }

    private fun hints(detections: List<Detection>): List<ClassificationHint> {
        val artifactCount = detections.count { it.qualityFlags.isNotEmpty() }
        val persistentCount = detections.count { it.kind == DetectionKind.PERSISTENT_CARRIER }
        val hints = buildList {
            if (artifactCount > 0) add(ClassificationHint(
                ClassificationCategory.LIKELY_LOCAL_INTERFERENCE_OR_OVERLOAD,
                artifactCount.toFloat() / detections.size,
                "$artifactCount of ${detections.size} detections carry center, symmetry, broadband, overload, or frame-quality evidence.",
            ))
            if (persistentCount > 0) add(ClassificationHint(
                ClassificationCategory.CONTINUOUS_NARROWBAND_CARRIER,
                persistentCount.toFloat() / detections.size,
                "$persistentCount detections crossed the versioned persistent-duration threshold.",
            ))
            val bursts = detections.size - persistentCount
            if (bursts >= 2) add(ClassificationHint(
                ClassificationCategory.REPEATING_SHORT_OOK_LIKE_BURST,
                (bursts.toFloat() / detections.size * 0.8f).coerceAtMost(0.8f),
                "$bursts separated short energy events recur near the same frequency; waveform-level confirmation is unavailable from aggregates.",
            ))
            val centers = detections.map { it.centerFrequencyHz }.distinct().sorted()
            val centerSpread = (centers.lastOrNull() ?: 0L) - (centers.firstOrNull() ?: 0L)
            val typicalBandwidth = medianLong(detections.map { it.bandwidthHz })
            if (centers.size == 2 && bursts >= 2) add(ClassificationHint(
                ClassificationCategory.TWO_LEVEL_FSK_LIKE,
                0.55f,
                "Energy alternates between two aggregate center-frequency levels separated by $centerSpread Hz; no payload was inspected.",
            ))
            if (centers.size > 2 && centerSpread > config.frequencyToleranceHz) add(ClassificationHint(
                ClassificationCategory.FREQUENCY_HOPPING_OR_UNRESOLVED,
                0.45f,
                "Aggregate events occupy ${centers.size} center-frequency regions across $centerSpread Hz.",
            ))
            if (typicalBandwidth >= 500_000 && bursts >= 2) add(ClassificationHint(
                ClassificationCategory.WIDER_FSK_FAMILY,
                0.4f,
                "Typical occupied bandwidth is $typicalBandwidth Hz with repeated discrete activity.",
            ))
            if (typicalBandwidth >= 150_000 && persistentCount > 0) add(ClassificationHint(
                ClassificationCategory.ANALOG_FM_LIKE,
                0.3f,
                "Persistent aggregate energy spans $typicalBandwidth Hz; this low-confidence shape hint requires focused IQ confirmation.",
            ))
        }
        return hints.sortedByDescending { it.confidence }
    }
}

object FingerprintCorrections {
    fun split(source: SignalFingerprint, movedDetectionIds: Set<String>, atEpochMs: Long): Pair<SignalFingerprint, SignalFingerprint> {
        require(movedDetectionIds.isNotEmpty() && source.detectionIds.containsAll(movedDetectionIds))
        require(source.detectionIds.size > movedDetectionIds.size)
        val retained = source.detectionIds - movedDetectionIds
        val provenance = FingerprintProvenance("SPLIT", setOf(source.id), atEpochMs, "User split preserved every source detection.")
        return source.copy(
            id = stableCorrectionId("split-a", source.id, retained), detectionIds = retained,
            occurrenceCount = retained.size, provenance = source.provenance + provenance,
        ) to source.copy(
            id = stableCorrectionId("split-b", source.id, movedDetectionIds), detectionIds = movedDetectionIds,
            occurrenceCount = movedDetectionIds.size, provenance = source.provenance + provenance,
        )
    }

    fun merge(sources: List<SignalFingerprint>, atEpochMs: Long): SignalFingerprint {
        require(sources.size >= 2)
        require(sources.map { it.equipmentProfileVersionId }.distinct().size == 1) { "Incomparable equipment profiles cannot be merged" }
        val ids = sources.flatMap { it.detectionIds }.toSet()
        val sourceIds = sources.map { it.id }.toSet()
        val base = sources.minBy { it.id }
        val labels = sources.map { it.userLabel }.filter { it.isNotBlank() }.distinct()
        val notes = sources.map { it.notes }.filter { it.isNotBlank() }.distinct()
        return base.copy(
            id = stableCorrectionId("merge", sourceIds.sorted().joinToString(), ids),
            detectionIds = ids,
            nominalFrequencyHz = sources.map { it.nominalFrequencyHz }.average().toLong(),
            typicalBandwidthHz = medianLong(sources.map { it.typicalBandwidthHz }),
            firstSeenAtEpochMs = sources.minOf { it.firstSeenAtEpochMs },
            lastSeenAtEpochMs = sources.maxOf { it.lastSeenAtEpochMs },
            occurrenceCount = ids.size,
            dutyCycleEstimate = sources.map { it.dutyCycleEstimate }.average().toFloat().coerceIn(0f, 1f),
            typicalBurstDurationMs = sources.mapNotNull { it.typicalBurstDurationMs }.takeIf { it.isNotEmpty() }?.let(::medianLong),
            typicalRepeatIntervalMs = sources.mapNotNull { it.typicalRepeatIntervalMs }.takeIf { it.isNotEmpty() }?.let(::medianLong),
            noveltyScore = sources.minOf { it.noveltyScore },
            classificationHints = sources.flatMap { it.classificationHints }.groupBy { it.category }
                .map { (_, values) -> values.maxBy { it.confidence } }.sortedByDescending { it.confidence },
            userLabel = labels.joinToString(" / "),
            tags = sources.flatMap { it.tags }.toSet(),
            notes = notes.joinToString("\n"),
            provenance = sources.flatMap { it.provenance }.distinct() + FingerprintProvenance(
                "MERGE", sourceIds, atEpochMs, "User merge preserved every source detection.",
            ),
        )
    }

    private fun stableCorrectionId(operation: String, source: String, detections: Set<String>): String =
        UUID.nameUUIDFromBytes("$operation:$source:${detections.sorted().joinToString()}".toByteArray(StandardCharsets.UTF_8)).toString()
}

private fun medianLong(values: List<Long>): Long {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}
