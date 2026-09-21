package dev.rfnotebook.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Upsert
    suspend fun insertRadioDevice(device: RadioDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEquipmentProfile(profile: EquipmentProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBandProfile(profile: BandProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBandRanges(ranges: List<BandRangeEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSurvey(survey: SurveyEntity)

    @Query("SELECT * FROM surveys WHERE id = :surveyId")
    suspend fun survey(surveyId: String): SurveyEntity?

    @Query("SELECT * FROM surveys WHERE status IN ('VALIDATING', 'ACTIVE', 'PAUSED', 'FINALIZING') ORDER BY lastWallTimeEpochMs DESC")
    suspend fun interruptedSurveys(): List<SurveyEntity>

    @Query("SELECT * FROM surveys ORDER BY lastWallTimeEpochMs DESC")
    fun observeSurveys(): Flow<List<SurveyEntity>>

    @Query("SELECT * FROM surveys WHERE status = 'COMPLETE' ORDER BY endedAtEpochMs DESC, lastWallTimeEpochMs DESC")
    suspend fun completedSurveys(): List<SurveyEntity>

    @Query("UPDATE surveys SET status = :status, revision = :revision, startedAtEpochMs = :startedAt, endedAtEpochMs = :endedAt, lastWallTimeEpochMs = :wallTime, lastMonotonicNs = :monotonicNs, failureExplanation = :failure WHERE id = :surveyId AND revision = :expectedRevision")
    suspend fun compareAndSetSurveyState(
        surveyId: String,
        expectedRevision: Long,
        status: String,
        revision: Long,
        startedAt: Long?,
        endedAt: Long?,
        wallTime: Long,
        monotonicNs: Long,
        failure: String?,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStateEvent(event: SurveyStateEventEntity)

    @Transaction
    suspend fun persistStateTransition(expectedRevision: Long, survey: SurveyEntity, event: SurveyStateEventEntity) {
        val changed = compareAndSetSurveyState(
            survey.id,
            expectedRevision,
            survey.status,
            survey.revision,
            survey.startedAtEpochMs,
            survey.endedAtEpochMs,
            survey.lastWallTimeEpochMs,
            survey.lastMonotonicNs,
            survey.failureExplanation,
        )
        check(changed == 1) { "Survey ${survey.id} changed concurrently" }
        insertStateEvent(event)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocationFix(fix: LocationFixEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocationFixes(fixes: List<LocationFixEntity>)

    @Query("SELECT * FROM location_fixes WHERE surveyId = :surveyId AND monotonicNs BETWEEN :fromNs AND :toNs ORDER BY monotonicNs")
    suspend fun locationFixes(surveyId: String, fromNs: Long, toNs: Long): List<LocationFixEntity>

    @Query("SELECT * FROM location_fixes WHERE id IN (:ids)")
    suspend fun locationFixesByIds(ids: List<String>): List<LocationFixEntity>

    @Query("SELECT * FROM location_fixes WHERE surveyId = :surveyId ORDER BY wallTimeEpochMs, monotonicNs")
    suspend fun surveyLocationFixes(surveyId: String): List<LocationFixEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAggregates(aggregates: List<SpectrumAggregateEntity>)

    @Transaction
    suspend fun insertAggregateBatch(fix: LocationFixEntity?, aggregates: List<SpectrumAggregateEntity>) {
        fix?.let { insertLocationFix(it) }
        insertAggregates(aggregates)
    }

    @Transaction
    suspend fun insertSurveyBatch(fixes: List<LocationFixEntity>, aggregates: List<SpectrumAggregateEntity>) {
        if (fixes.isNotEmpty()) insertLocationFixes(fixes)
        if (aggregates.isNotEmpty()) insertAggregates(aggregates)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGap(gap: AcquisitionGapEntity)

    @Query("UPDATE acquisition_gaps SET endedWallTimeEpochMs = :wallTimeEpochMs, endedMonotonicNs = :monotonicNs WHERE surveyId = :surveyId AND endedMonotonicNs IS NULL")
    suspend fun closeOpenGaps(surveyId: String, wallTimeEpochMs: Long, monotonicNs: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHealthSnapshot(snapshot: HealthSnapshotEntity)

    @Query("SELECT COUNT(*) FROM spectrum_aggregates WHERE surveyId = :surveyId")
    suspend fun aggregateCount(surveyId: String): Long

    @Query("SELECT COUNT(*) FROM acquisition_gaps WHERE surveyId = :surveyId")
    suspend fun gapCount(surveyId: String): Long

    @Query("SELECT * FROM acquisition_gaps WHERE surveyId = :surveyId ORDER BY startedMonotonicNs")
    suspend fun surveyGaps(surveyId: String): List<AcquisitionGapEntity>

    @Query("SELECT COUNT(*) FROM location_fixes WHERE surveyId = :surveyId")
    suspend fun locationFixCount(surveyId: String): Long

    @Query("UPDATE surveys SET distanceMeters = :distanceMeters, locationCoverageRatio = :locationCoverageRatio, droppedFrameCount = :droppedFrameCount, overrunCount = :overrunCount, malformedFrameCount = :malformedFrameCount, staleFixCount = :staleFixCount, unlocatedObservationCount = :unlocatedObservationCount WHERE id = :surveyId")
    suspend fun updateSurveyHealth(
        surveyId: String,
        distanceMeters: Double,
        locationCoverageRatio: Double,
        droppedFrameCount: Long,
        overrunCount: Long,
        malformedFrameCount: Long,
        staleFixCount: Long,
        unlocatedObservationCount: Long,
    )

    @Query("SELECT * FROM equipment_profiles WHERE retiredAtEpochMs IS NULL ORDER BY name, version DESC")
    suspend fun activeEquipmentProfiles(): List<EquipmentProfileEntity>

    @Query("SELECT * FROM equipment_profiles WHERE profileId = :profileId ORDER BY version")
    suspend fun equipmentProfileVersions(profileId: String): List<EquipmentProfileEntity>

    @Query("UPDATE equipment_profiles SET retiredAtEpochMs = :retiredAt WHERE versionId = :versionId AND retiredAtEpochMs IS NULL")
    suspend fun retireEquipmentProfile(versionId: String, retiredAt: Long)

    @Query("SELECT * FROM equipment_profiles WHERE versionId = :versionId")
    suspend fun equipmentProfile(versionId: String): EquipmentProfileEntity?

    @Query("SELECT * FROM radio_devices WHERE id = :id")
    suspend fun radioDevice(id: String): RadioDeviceEntity?

    @Query("UPDATE radio_devices SET connectionState = :state, connectionRevision = :revision WHERE id = :radioDeviceId AND connectionRevision = :expectedRevision")
    suspend fun compareAndSetConnectionState(radioDeviceId: String, expectedRevision: Long, state: String, revision: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConnectionStateEvent(event: ConnectionStateEventEntity)

    @Transaction
    suspend fun persistConnectionTransition(expectedRevision: Long, device: RadioDeviceEntity, event: ConnectionStateEventEntity) {
        check(compareAndSetConnectionState(device.id, expectedRevision, device.connectionState, device.connectionRevision) == 1) {
            "Radio ${device.id} connection state changed concurrently"
        }
        insertConnectionStateEvent(event)
    }

    @Query("SELECT * FROM band_profiles WHERE retiredAtEpochMs IS NULL ORDER BY name, version DESC")
    suspend fun activeBandProfiles(): List<BandProfileEntity>

    @Query("UPDATE band_profiles SET retiredAtEpochMs = :retiredAt WHERE versionId = :versionId AND retiredAtEpochMs IS NULL")
    suspend fun retireBandProfile(versionId: String, retiredAt: Long)

    @Query("SELECT * FROM band_profiles WHERE versionId = :versionId")
    suspend fun bandProfile(versionId: String): BandProfileEntity?

    @Query("SELECT * FROM band_ranges WHERE bandProfileVersionId = :versionId ORDER BY kind, ordinal")
    suspend fun bandRanges(versionId: String): List<BandRangeEntity>

    @Query("SELECT * FROM health_snapshots WHERE surveyId = :surveyId ORDER BY monotonicNs DESC LIMIT 1")
    fun observeLatestHealth(surveyId: String): Flow<HealthSnapshotEntity?>

    @Query("SELECT * FROM health_snapshots WHERE surveyId = :surveyId ORDER BY monotonicNs DESC LIMIT 1")
    suspend fun latestHealth(surveyId: String): HealthSnapshotEntity?

    @Query("SELECT * FROM spectrum_aggregates WHERE surveyId = :surveyId ORDER BY timeBucketStartEpochMs, frequencyBinHz")
    suspend fun surveyAggregates(surveyId: String): List<SpectrumAggregateEntity>

    @Query("SELECT * FROM detections WHERE surveyId = :surveyId ORDER BY startedAtEpochMs, centerFrequencyHz")
    suspend fun surveyDetections(surveyId: String): List<DetectionEntity>

    @Query("SELECT * FROM detections WHERE surveyId != :surveyId ORDER BY equipmentProfileVersionId, startedAtEpochMs, centerFrequencyHz")
    suspend fun detectionsExceptSurvey(surveyId: String): List<DetectionEntity>

    @Query("SELECT * FROM detections WHERE fingerprintId = :fingerprintId ORDER BY startedAtEpochMs")
    suspend fun fingerprintDetections(fingerprintId: String): List<DetectionEntity>

    @Query("SELECT * FROM detections WHERE id IN (:ids)")
    suspend fun detectionsByIds(ids: List<String>): List<DetectionEntity>

    @Query("SELECT * FROM signal_fingerprints ORDER BY noveltyScore DESC, lastSeenAtEpochMs DESC")
    fun observeFingerprints(): Flow<List<SignalFingerprintEntity>>

    @Query("SELECT * FROM signal_fingerprints ORDER BY noveltyScore DESC, lastSeenAtEpochMs DESC")
    suspend fun fingerprints(): List<SignalFingerprintEntity>

    @Query("SELECT * FROM signal_fingerprints WHERE id = :fingerprintId")
    suspend fun fingerprint(fingerprintId: String): SignalFingerprintEntity?

    @Query("SELECT * FROM fingerprint_hints WHERE fingerprintId = :fingerprintId ORDER BY rank")
    suspend fun fingerprintHints(fingerprintId: String): List<FingerprintHintEntity>

    @Query("SELECT * FROM fingerprint_provenance WHERE fingerprintId = :fingerprintId ORDER BY atEpochMs, id")
    suspend fun fingerprintProvenance(fingerprintId: String): List<FingerprintProvenanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetections(values: List<DetectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFingerprints(values: List<SignalFingerprintEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFingerprintHints(values: List<FingerprintHintEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFingerprintProvenance(values: List<FingerprintProvenanceEntity>)

    @Upsert
    suspend fun upsertReprocessingJob(job: ReprocessingJobEntity)

    @Query("UPDATE detections SET fingerprintId = :fingerprintId WHERE id IN (:detectionIds)")
    suspend fun assignFingerprint(fingerprintId: String, detectionIds: List<String>)

    @Query("DELETE FROM detections WHERE surveyId = :surveyId")
    suspend fun deleteSurveyDetections(surveyId: String)

    @Query("DELETE FROM signal_fingerprints WHERE id NOT IN (SELECT DISTINCT fingerprintId FROM detections WHERE fingerprintId IS NOT NULL)")
    suspend fun deleteUnreferencedFingerprints()

    @Transaction
    suspend fun replaceDerivedSurveyData(
        surveyId: String,
        detections: List<DetectionEntity>,
        fingerprints: List<SignalFingerprintEntity>,
        hints: List<FingerprintHintEntity>,
        provenance: List<FingerprintProvenanceEntity>,
        detectionAssignments: Map<String, List<String>>,
    ) {
        deleteSurveyDetections(surveyId)
        deleteUnreferencedFingerprints()
        insertFingerprints(fingerprints)
        insertDetections(detections)
        detectionAssignments.forEach { (fingerprintId, detectionIds) -> assignFingerprint(fingerprintId, detectionIds) }
        insertFingerprintHints(hints)
        insertFingerprintProvenance(provenance)
        deleteUnreferencedFingerprints()
    }

    @Query("UPDATE signal_fingerprints SET state = :state, userLabel = :userLabel, tags = :tags, notes = :notes WHERE id = :fingerprintId")
    suspend fun updateFingerprintUserFields(fingerprintId: String, state: String, userLabel: String, tags: String, notes: String): Int

    @Query("DELETE FROM signal_fingerprints WHERE id IN (:fingerprintIds)")
    suspend fun deleteFingerprints(fingerprintIds: List<String>)

    @Transaction
    suspend fun persistFingerprintCorrection(
        sourceFingerprintIds: List<String>,
        fingerprints: List<SignalFingerprintEntity>,
        hints: List<FingerprintHintEntity>,
        provenance: List<FingerprintProvenanceEntity>,
        detectionAssignments: Map<String, List<String>>,
    ) {
        insertFingerprints(fingerprints)
        detectionAssignments.forEach { (fingerprintId, detectionIds) -> assignFingerprint(fingerprintId, detectionIds) }
        insertFingerprintHints(hints)
        insertFingerprintProvenance(provenance)
        deleteFingerprints(sourceFingerprintIds)
    }
}
