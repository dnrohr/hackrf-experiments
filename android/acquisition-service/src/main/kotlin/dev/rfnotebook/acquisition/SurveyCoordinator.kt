package dev.rfnotebook.acquisition

import dev.rfnotebook.domain.GapReason
import dev.rfnotebook.domain.SurveyCommand
import dev.rfnotebook.domain.SurveyRuntimeState
import dev.rfnotebook.domain.SurveyStateMachine
import dev.rfnotebook.domain.SurveyStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SurveyStateStore {
    suspend fun load(surveyId: String): SurveyRuntimeState
    suspend fun save(previousRevision: Long, state: SurveyRuntimeState)
    suspend fun recordGap(
        surveyId: String,
        reason: GapReason,
        startedWallTimeEpochMs: Long,
        startedMonotonicNs: Long,
        endedWallTimeEpochMs: Long,
        endedMonotonicNs: Long,
    )
}

interface SurveyRadioController {
    suspend fun start()
    suspend fun stop()
}

fun interface SurveyClock {
    fun now(): SurveyTime
}

data class SurveyTime(val wallTimeEpochMs: Long, val monotonicNs: Long)

class SurveyCoordinator(
    private val store: SurveyStateStore,
    private val radio: SurveyRadioController,
    private val clock: SurveyClock,
) {
    private val mutex = Mutex()

    suspend fun command(surveyId: String, command: SurveyCommand): SurveyRuntimeState = mutex.withLock {
        val current = store.load(surveyId)
        val now = clock.now()
        val transition = SurveyStateMachine.transition(current, command, now.wallTimeEpochMs, now.monotonicNs)
        if (!transition.changed) return@withLock current

        if (current.status == SurveyStatus.ACTIVE && transition.state.status != SurveyStatus.ACTIVE) {
            radio.stop()
        }
        store.save(current.revision, transition.state)
        if (current.status != SurveyStatus.ACTIVE && transition.state.status == SurveyStatus.ACTIVE) {
            try {
                radio.start()
            } catch (failure: Throwable) {
                val failedAt = clock.now()
                val recoveryCommand = if (command == SurveyCommand.Resume) SurveyCommand.Pause else {
                    SurveyCommand.Fail(failure.message ?: failure.javaClass.simpleName)
                }
                val failed = SurveyStateMachine.transition(
                    transition.state,
                    recoveryCommand,
                    failedAt.wallTimeEpochMs,
                    failedAt.monotonicNs,
                ).state
                store.save(transition.state.revision, failed)
                throw failure
            }
        }
        transition.state
    }

    suspend fun recover(surveyId: String): SurveyRuntimeState = mutex.withLock {
        radio.stop()
        val current = store.load(surveyId)
        val now = clock.now()
        val recovery = SurveyStateMachine.recoverAfterProcessDeath(current, now.wallTimeEpochMs, now.monotonicNs)
        if (recovery.changed) {
            store.save(current.revision, recovery.state)
            recovery.gapReason?.let {
                store.recordGap(
                    surveyId, it, current.lastWallTimeEpochMs, current.lastMonotonicNs,
                    now.wallTimeEpochMs, now.monotonicNs,
                )
            }
        }
        recovery.state
    }
}
