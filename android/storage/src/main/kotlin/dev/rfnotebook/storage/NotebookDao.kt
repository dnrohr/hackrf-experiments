package dev.rfnotebook.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRadioDevice(device: RadioDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEquipmentProfile(profile: EquipmentProfileEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBandProfile(profile: BandProfileEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBandRanges(ranges: List<BandRangeEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSurvey(survey: SurveyEntity)

    @Query("SELECT * FROM surveys WHERE id = :surveyId")
    suspend fun survey(surveyId: String): SurveyEntity?

    @Query("SELECT * FROM surveys WHERE status IN ('VALIDATING', 'ACTIVE', 'PAUSED', 'FINALIZING') ORDER BY lastWallTimeEpochMs DESC")
    suspend fun interruptedSurveys(): List<SurveyEntity>

    @Query("SELECT * FROM surveys ORDER BY lastWallTimeEpochMs DESC")
    fun observeSurveys(): Flow<List<SurveyEntity>>

    @Query("UPDATE surveys SET status = :status, revision = :revision, lastWallTimeEpochMs = :wallTime, lastMonotonicNs = :monotonicNs, failureExplanation = :failure WHERE id = :surveyId AND revision = :expectedRevision")
    suspend fun compareAndSetSurveyState(
        surveyId: String,
        expectedRevision: Long,
        status: String,
        revision: Long,
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
            survey.lastWallTimeEpochMs,
            survey.lastMonotonicNs,
            survey.failureExplanation,
        )
        check(changed == 1) { "Survey ${survey.id} changed concurrently" }
        insertStateEvent(event)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLocationFix(fix: LocationFixEntity)

    @Query("SELECT * FROM location_fixes WHERE surveyId = :surveyId AND monotonicNs BETWEEN :fromNs AND :toNs ORDER BY monotonicNs")
    suspend fun locationFixes(surveyId: String, fromNs: Long, toNs: Long): List<LocationFixEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAggregates(aggregates: List<SpectrumAggregateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGap(gap: AcquisitionGapEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHealthSnapshot(snapshot: HealthSnapshotEntity)

    @Query("SELECT * FROM health_snapshots WHERE surveyId = :surveyId ORDER BY monotonicNs DESC LIMIT 1")
    fun observeLatestHealth(surveyId: String): Flow<HealthSnapshotEntity?>
}
