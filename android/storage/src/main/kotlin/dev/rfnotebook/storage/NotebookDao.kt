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

    @Query("SELECT * FROM location_fixes WHERE surveyId = :surveyId AND monotonicNs BETWEEN :fromNs AND :toNs ORDER BY monotonicNs")
    suspend fun locationFixes(surveyId: String, fromNs: Long, toNs: Long): List<LocationFixEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAggregates(aggregates: List<SpectrumAggregateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGap(gap: AcquisitionGapEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHealthSnapshot(snapshot: HealthSnapshotEntity)

    @Query("SELECT COUNT(*) FROM spectrum_aggregates WHERE surveyId = :surveyId")
    suspend fun aggregateCount(surveyId: String): Long

    @Query("SELECT COUNT(*) FROM acquisition_gaps WHERE surveyId = :surveyId")
    suspend fun gapCount(surveyId: String): Long

    @Query("SELECT COUNT(*) FROM location_fixes WHERE surveyId = :surveyId")
    suspend fun locationFixCount(surveyId: String): Long

    @Query("UPDATE surveys SET locationCoverageRatio = :locationCoverageRatio, droppedFrameCount = :droppedFrameCount, overrunCount = :overrunCount, malformedFrameCount = :malformedFrameCount, staleFixCount = :staleFixCount, unlocatedObservationCount = :unlocatedObservationCount WHERE id = :surveyId")
    suspend fun updateSurveyHealth(
        surveyId: String,
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
}
