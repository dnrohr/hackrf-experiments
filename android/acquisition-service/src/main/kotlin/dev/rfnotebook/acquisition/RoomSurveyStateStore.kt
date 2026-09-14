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

    override suspend fun recordGap(surveyId: String, reason: GapReason, wallTimeEpochMs: Long, monotonicNs: Long) {
        dao.insertGap(
            AcquisitionGapEntity(
                id = UUID.randomUUID().toString(),
                surveyId = surveyId,
                reason = reason.name,
                startedWallTimeEpochMs = wallTimeEpochMs,
                startedMonotonicNs = monotonicNs,
                endedWallTimeEpochMs = wallTimeEpochMs,
                endedMonotonicNs = monotonicNs,
                droppedUnitCount = 0,
                explanation = "Survey recovered after ${reason.name.lowercase().replace('_', ' ')}",
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
