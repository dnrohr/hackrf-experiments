package dev.rfnotebook.acquisition

import dev.rfnotebook.domain.GapReason
import dev.rfnotebook.domain.SurveyRuntimeState
import dev.rfnotebook.domain.SurveyStatus
import dev.rfnotebook.storage.AcquisitionGapEntity
import dev.rfnotebook.storage.NotebookDao
import dev.rfnotebook.storage.SurveyEntity
import dev.rfnotebook.storage.SurveyStateEventEntity
import java.util.UUID

class RoomSurveyStateStore(private val dao: NotebookDao) : SurveyStateStore {
    override suspend fun load(surveyId: String): SurveyRuntimeState =
        requireNotNull(dao.survey(surveyId)) { "Survey $surveyId does not exist" }.toRuntimeState()

    override suspend fun save(previousRevision: Long, state: SurveyRuntimeState) {
        val current = requireNotNull(dao.survey(state.surveyId)) { "Survey ${state.surveyId} does not exist" }
        check(current.revision == previousRevision) { "Survey ${state.surveyId} changed concurrently" }
        val updated = current.copy(
            status = state.status.name,
            revision = state.revision,
            startedAtEpochMs = current.startedAtEpochMs ?: state.lastWallTimeEpochMs.takeIf { state.status == SurveyStatus.ACTIVE },
            endedAtEpochMs = state.lastWallTimeEpochMs.takeIf { state.status == SurveyStatus.COMPLETE || state.status == SurveyStatus.FAILED },
            lastWallTimeEpochMs = state.lastWallTimeEpochMs,
            lastMonotonicNs = state.lastMonotonicNs,
            failureExplanation = state.failureExplanation,
        )
        dao.persistStateTransition(
            previousRevision,
            updated,
            SurveyStateEventEntity(
                surveyId = state.surveyId,
                fromStatus = current.status,
                toStatus = state.status.name,
                wallTimeEpochMs = state.lastWallTimeEpochMs,
                monotonicNs = state.lastMonotonicNs,
                explanation = state.failureExplanation,
            ),
        )
    }

    override suspend fun recordGap(
        surveyId: String,
        reason: GapReason,
        startedWallTimeEpochMs: Long,
        startedMonotonicNs: Long,
        endedWallTimeEpochMs: Long,
        endedMonotonicNs: Long,
    ) {
        insertGap(
            surveyId, reason, startedWallTimeEpochMs, startedMonotonicNs,
            endedWallTimeEpochMs, endedMonotonicNs, 0, null,
        )
    }

    suspend fun recordOpenGap(surveyId: String, reason: GapReason, wallTimeEpochMs: Long, monotonicNs: Long) {
        insertGap(surveyId, reason, wallTimeEpochMs, monotonicNs, null, null, 0, null)
    }

    suspend fun recordCountedGap(
        surveyId: String,
        reason: GapReason,
        wallTimeEpochMs: Long,
        monotonicNs: Long,
        droppedUnitCount: Long,
        explanation: String,
    ) {
        insertGap(
            surveyId, reason, wallTimeEpochMs, monotonicNs, wallTimeEpochMs, monotonicNs,
            droppedUnitCount, explanation,
        )
    }

    suspend fun closeOpenGaps(surveyId: String, wallTimeEpochMs: Long, monotonicNs: Long) {
        dao.closeOpenGaps(surveyId, wallTimeEpochMs, monotonicNs)
    }

    private suspend fun insertGap(
        surveyId: String,
        reason: GapReason,
        wallTimeEpochMs: Long,
        monotonicNs: Long,
        endedWallTimeEpochMs: Long?,
        endedMonotonicNs: Long?,
        droppedUnitCount: Long,
        suppliedExplanation: String?,
    ) {
        dao.insertGap(
            AcquisitionGapEntity(
                id = UUID.randomUUID().toString(),
                surveyId = surveyId,
                reason = reason.name,
                startedWallTimeEpochMs = wallTimeEpochMs,
                startedMonotonicNs = monotonicNs,
                endedWallTimeEpochMs = endedWallTimeEpochMs,
                endedMonotonicNs = endedMonotonicNs,
                droppedUnitCount = droppedUnitCount,
                explanation = suppliedExplanation ?: "Survey recovered after ${reason.name.lowercase().replace('_', ' ')}",
            ),
        )
    }

    private fun SurveyEntity.toRuntimeState() = SurveyRuntimeState(
        surveyId = id,
        status = SurveyStatus.valueOf(status),
        revision = revision,
        lastWallTimeEpochMs = lastWallTimeEpochMs,
        lastMonotonicNs = lastMonotonicNs,
        failureExplanation = failureExplanation,
    )
}
