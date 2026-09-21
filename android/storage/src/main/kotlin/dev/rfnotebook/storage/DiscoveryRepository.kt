package dev.rfnotebook.storage

import dev.rfnotebook.domain.Detection
import dev.rfnotebook.domain.FingerprintState
import dev.rfnotebook.domain.ClassificationCategory
import dev.rfnotebook.domain.ClassificationHint
import dev.rfnotebook.domain.FingerprintProvenance
import dev.rfnotebook.domain.SignalFingerprint
import dev.rfnotebook.signal.processing.AggregateBin
import dev.rfnotebook.signal.processing.AggregateFrame
import dev.rfnotebook.signal.processing.DetectionPipeline
import dev.rfnotebook.signal.processing.DetectorConfig
import dev.rfnotebook.signal.processing.FingerprintClusterer
import dev.rfnotebook.signal.processing.FingerprintClusteringConfig
import dev.rfnotebook.signal.processing.FingerprintCorrections
import java.util.UUID

data class ReprocessingResult(
    val surveyId: String,
    val aggregateCount: Long,
    val detectionCount: Int,
    val fingerprintCount: Int,
    val detectorVersion: String,
    val clusteringVersion: String,
)

data class DiscoveryDetail(
    val fingerprint: SignalFingerprintEntity,
    val hints: List<FingerprintHintEntity>,
    val detections: List<DetectionEntity>,
    val provenance: List<FingerprintProvenanceEntity>,
)

class DiscoveryRepository(private val dao: NotebookDao) {
    private data class LocationExtent(
        val minimumLatitude: Double,
        val maximumLatitude: Double,
        val minimumLongitude: Double,
        val maximumLongitude: Double,
        val count: Int,
    )

    suspend fun reprocessSurvey(surveyId: String, nowEpochMs: Long = System.currentTimeMillis()): ReprocessingResult {
        val survey = requireNotNull(dao.survey(surveyId)) { "Survey $surveyId does not exist" }
        val band = requireNotNull(dao.bandProfile(survey.bandProfileVersionId))
        val aggregates = dao.surveyAggregates(surveyId)
        val detectorConfig = DetectorConfig(
            thresholdSnrDb = band.thresholdSnrDb,
            minimumBandwidthHz = band.minimumBandwidthHz,
            binWidthHz = band.binWidthHz,
        )
        val clusteringConfig = FingerprintClusteringConfig()
        val jobId = UUID.nameUUIDFromBytes("$surveyId:${detectorConfig.version}:${clusteringConfig.version}".toByteArray()).toString()
        dao.upsertReprocessingJob(ReprocessingJobEntity(
            jobId, surveyId, detectorConfig.version, clusteringConfig.version, "RUNNING", nowEpochMs,
            null, aggregates.size.toLong(), 0, 0, null,
        ))
        return runCatching {
            val frames = aggregates.groupBy { it.timeBucketStartEpochMs }.map { (time, bins) ->
                AggregateFrame(
                    surveyId = surveyId,
                    equipmentProfileVersionId = survey.equipmentProfileVersionId,
                    timeBucketStartEpochMs = time,
                    bins = bins.map { AggregateBin(it.frequencyBinHz, it.medianPowerDbfs, it.maximumPowerDbfs, it.sampleCount) },
                    locationFixId = bins.mapNotNull { it.locationFixId }.firstOrNull(),
                )
            }
            val detections = DetectionPipeline(detectorConfig).process(frames)
            val existingDetectionEntities = dao.detectionsExceptSurvey(surveyId)
            val existingDetections = existingDetectionEntities.map { it.toDomain() }
            val existingFingerprintEntities = dao.fingerprints()
            val existingFingerprints = existingFingerprintEntities.associateBy { it.id }
            val priorDetails = existingFingerprintEntities.map { detail(it.id) }
            val allDetections = existingDetections + detections
            val allDetectionIds = allDetections.map { it.id }.toSet()
            val corrected = priorDetails.filter { it.provenance.isNotEmpty() }.mapNotNull { prior ->
                val retainedIds = prior.detections.map { it.id }.toSet().intersect(allDetectionIds)
                prior.toDomain().copy(detectionIds = retainedIds, occurrenceCount = retainedIds.size)
                    .takeIf { retainedIds.isNotEmpty() }
            }
            val correctedDetectionIds = corrected.flatMap { it.detectionIds }.toSet()
            val automatic = FingerprintClusterer(clusteringConfig).cluster(
                allDetections.filterNot { it.id in correctedDetectionIds },
            ).map { fp ->
                val prior = existingFingerprints[fp.id]
                fp.copy(
                    noveltyScore = if (fp.detectionIds.any { id -> existingDetections.any { it.id == id } }) 0f else 1f,
                    state = prior?.state?.let(FingerprintState::valueOf) ?: fp.state,
                    userLabel = prior?.userLabel ?: fp.userLabel,
                    tags = prior?.tags?.split('|')?.filter { it.isNotBlank() }?.toSet() ?: fp.tags,
                    notes = prior?.notes ?: fp.notes,
                )
            }
            val fingerprints = (corrected + automatic).sortedBy { it.id }
            val fingerprintByDetection = fingerprints.flatMap { fp -> fp.detectionIds.map { it to fp.id } }.toMap()
            val detectionEntities = detections.map { it.toEntity(fingerprintByDetection[it.id]) }
            val allDetectionEntities = existingDetectionEntities + detectionEntities
            val extents = locationExtents(fingerprints, allDetectionEntities)
            dao.replaceDerivedSurveyData(
                surveyId,
                detectionEntities,
                fingerprints.map { fp -> fingerprintEntity(fp, extents[fp.id]) },
                fingerprints.flatMap { fp -> fp.classificationHints.mapIndexed { rank, hint ->
                    FingerprintHintEntity(fp.id, rank, hint.category.name, hint.confidence, hint.evidence)
                } },
                fingerprints.flatMap { fp -> fp.provenance.map { provenance ->
                    FingerprintProvenanceEntity(
                        fingerprintId = fp.id, operation = provenance.operation,
                        sourceFingerprintIds = provenance.sourceFingerprintIds.sorted().joinToString("|"),
                        atEpochMs = provenance.atEpochMs, explanation = provenance.explanation,
                    )
                } },
                fingerprints.associate { it.id to it.detectionIds.sorted() },
            )
            dao.upsertReprocessingJob(ReprocessingJobEntity(
                jobId, surveyId, detectorConfig.version, clusteringConfig.version, "COMPLETE", nowEpochMs,
                System.currentTimeMillis(), aggregates.size.toLong(), detections.size, fingerprints.size, null,
            ))
            ReprocessingResult(surveyId, aggregates.size.toLong(), detections.size, fingerprints.size, detectorConfig.version, clusteringConfig.version)
        }.getOrElse { error ->
            dao.upsertReprocessingJob(ReprocessingJobEntity(
                jobId, surveyId, detectorConfig.version, clusteringConfig.version, "FAILED", nowEpochMs,
                System.currentTimeMillis(), aggregates.size.toLong(), 0, 0, error.message ?: error::class.java.simpleName,
            ))
            throw error
        }
    }

    suspend fun discoveries(): List<SignalFingerprintEntity> = dao.fingerprints()

    suspend fun reprocessableSurveys(): List<SurveyEntity> = dao.completedSurveys()

    suspend fun partialDataExplanation(details: List<DiscoveryDetail>): String? {
        val surveyIds = details.flatMap { it.detections }.map { it.surveyId }.distinct()
        val reasons = buildList {
            val gapCount = surveyIds.sumOf { dao.gapCount(it) }
            if (gapCount > 0) add("$gapCount acquisition gaps are retained in the contributing surveys.")
            val corruptCount = details.flatMap { it.detections }.count { "CORRUPT_FRAME" in it.qualityFlags.split('|') }
            if (corruptCount > 0) add("$corruptCount detections include corrupt or incomplete frame evidence.")
            val missingLocation = details.flatMap { it.detections }.count { it.locationFixId == null }
            if (missingLocation > 0) add("$missingLocation detections have no associated location fix.")
        }
        return reasons.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    suspend fun detail(fingerprintId: String): DiscoveryDetail = DiscoveryDetail(
        fingerprint = requireNotNull(dao.fingerprint(fingerprintId)),
        hints = dao.fingerprintHints(fingerprintId),
        detections = dao.fingerprintDetections(fingerprintId),
        provenance = dao.fingerprintProvenance(fingerprintId),
    )

    suspend fun updateUserFields(
        fingerprintId: String,
        state: FingerprintState,
        label: String,
        tags: Set<String>,
        notes: String,
    ) {
        check(dao.updateFingerprintUserFields(fingerprintId, state.name, label, tags.sorted().joinToString("|"), notes) == 1)
    }

    suspend fun splitFingerprint(fingerprintId: String, movedDetectionIds: Set<String>, atEpochMs: Long): Pair<String, String> {
        val source = detail(fingerprintId).toDomain()
        val split = FingerprintCorrections.split(source, movedDetectionIds, atEpochMs)
        persistCorrection(listOf(fingerprintId), split.toList())
        return split.first.id to split.second.id
    }

    suspend fun mergeFingerprints(fingerprintIds: Set<String>, atEpochMs: Long): String {
        val sources = fingerprintIds.sorted().map { detail(it).toDomain() }
        val merged = FingerprintCorrections.merge(sources, atEpochMs)
        persistCorrection(fingerprintIds.sorted(), listOf(merged))
        return merged.id
    }

    private suspend fun persistCorrection(sourceIds: List<String>, fingerprints: List<SignalFingerprint>) {
        val detectionEntities = dao.detectionsByIds(fingerprints.flatMap { it.detectionIds }.distinct())
        val extents = locationExtents(fingerprints, detectionEntities)
        dao.persistFingerprintCorrection(
            sourceIds,
            fingerprints.map { fingerprintEntity(it, extents[it.id]) },
            fingerprints.flatMap { fp -> fp.classificationHints.mapIndexed { rank, hint ->
                FingerprintHintEntity(fp.id, rank, hint.category.name, hint.confidence, hint.evidence)
            } },
            fingerprints.flatMap { fp -> fp.provenance.map { provenance ->
                FingerprintProvenanceEntity(
                    fingerprintId = fp.id, operation = provenance.operation,
                    sourceFingerprintIds = provenance.sourceFingerprintIds.sorted().joinToString("|"),
                    atEpochMs = provenance.atEpochMs, explanation = provenance.explanation,
                )
            } },
            fingerprints.associate { it.id to it.detectionIds.sorted() },
        )
    }

    private fun DiscoveryDetail.toDomain() = SignalFingerprint(
        id = fingerprint.id,
        equipmentProfileVersionId = fingerprint.equipmentProfileVersionId,
        detectionIds = detections.map { it.id }.toSet(),
        nominalFrequencyHz = fingerprint.nominalFrequencyHz,
        typicalBandwidthHz = fingerprint.typicalBandwidthHz,
        firstSeenAtEpochMs = fingerprint.firstSeenAtEpochMs,
        lastSeenAtEpochMs = fingerprint.lastSeenAtEpochMs,
        occurrenceCount = fingerprint.occurrenceCount,
        dutyCycleEstimate = fingerprint.dutyCycleEstimate,
        typicalBurstDurationMs = fingerprint.typicalBurstDurationMs,
        typicalRepeatIntervalMs = fingerprint.typicalRepeatIntervalMs,
        noveltyScore = fingerprint.noveltyScore,
        classificationHints = hints.map { ClassificationHint(ClassificationCategory.valueOf(it.category), it.confidence, it.evidence) },
        algorithmVersion = fingerprint.algorithmVersion,
        state = FingerprintState.valueOf(fingerprint.state),
        userLabel = fingerprint.userLabel,
        tags = fingerprint.tags.split('|').filter { it.isNotBlank() }.toSet(),
        notes = fingerprint.notes,
        provenance = provenance.map { FingerprintProvenance(
            it.operation, it.sourceFingerprintIds.split('|').filter(String::isNotBlank).toSet(), it.atEpochMs, it.explanation,
        ) },
    )

    private suspend fun locationExtents(
        fingerprints: List<SignalFingerprint>,
        detections: List<DetectionEntity>,
    ): Map<String, LocationExtent> {
        val detectionsById = detections.associateBy { it.id }
        val locationIds = detections.mapNotNull { it.locationFixId }.distinct()
        val locations = if (locationIds.isEmpty()) emptyMap() else dao.locationFixesByIds(locationIds).associateBy { it.id }
        return fingerprints.mapNotNull { fingerprint ->
            val points = fingerprint.detectionIds.mapNotNull { detectionsById[it]?.locationFixId }.mapNotNull(locations::get)
            if (points.isEmpty()) null else fingerprint.id to LocationExtent(
                points.minOf { it.latitude }, points.maxOf { it.latitude },
                points.minOf { it.longitude }, points.maxOf { it.longitude }, points.size,
            )
        }.toMap()
    }

    private fun fingerprintEntity(fp: SignalFingerprint, extent: LocationExtent?) = SignalFingerprintEntity(
        fp.id, fp.equipmentProfileVersionId, fp.nominalFrequencyHz, fp.typicalBandwidthHz,
        fp.firstSeenAtEpochMs, fp.lastSeenAtEpochMs, fp.occurrenceCount, fp.dutyCycleEstimate,
        fp.typicalBurstDurationMs, fp.typicalRepeatIntervalMs, fp.noveltyScore, fp.algorithmVersion,
        fp.state.name, fp.userLabel, fp.tags.sorted().joinToString("|"), fp.notes,
        extent?.minimumLatitude, extent?.maximumLatitude, extent?.minimumLongitude,
        extent?.maximumLongitude, extent?.count ?: 0,
    )

    private fun Detection.toEntity(fingerprintId: String?) = DetectionEntity(
        id, surveyId, fingerprintId, equipmentProfileVersionId, startedAtEpochMs, endedAtEpochMs,
        centerFrequencyHz, bandwidthHz, peakPowerDbfs, medianPowerDbfs, snrDb, locationFixId,
        detectorVersion, kind.name, qualityFlags.map { it.name }.sorted().joinToString("|"),
    )

    private fun DetectionEntity.toDomain() = Detection(
        id, surveyId, equipmentProfileVersionId, startedAtEpochMs, endedAtEpochMs,
        centerFrequencyHz, bandwidthHz, peakPowerDbfs, medianPowerDbfs, snrDb,
        detectorVersion, dev.rfnotebook.domain.DetectionKind.valueOf(kind), locationFixId,
        qualityFlags.split('|').filter { it.isNotBlank() }.map(dev.rfnotebook.domain.QualityFlag::valueOf).toSet(),
    )
}
