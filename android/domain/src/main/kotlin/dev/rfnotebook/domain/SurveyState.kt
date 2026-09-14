package dev.rfnotebook.domain

enum class SurveyStatus { DRAFT, VALIDATING, ACTIVE, PAUSED, FINALIZING, COMPLETE, FAILED }

sealed interface SurveyCommand {
    data object Validate : SurveyCommand
    data object Start : SurveyCommand
    data object Pause : SurveyCommand
    data object Resume : SurveyCommand
    data object Stop : SurveyCommand
    data object Finalize : SurveyCommand
    data class Fail(val explanation: String) : SurveyCommand
}

data class SurveyRuntimeState(
    val surveyId: String,
    val status: SurveyStatus,
    val revision: Long,
    val lastWallTimeEpochMs: Long,
    val lastMonotonicNs: Long,
    val failureExplanation: String? = null,
)

data class SurveyTransition(
    val state: SurveyRuntimeState,
    val changed: Boolean,
    val shouldStream: Boolean,
    val gapReason: GapReason? = null,
)

enum class GapReason { PROCESS_DEATH, USB_DETACH, RADIO_STALL, QUEUE_PRESSURE, LOW_STORAGE, SERVICE_INTERRUPTION }

class InvalidSurveyTransition(from: SurveyStatus, command: SurveyCommand) :
    IllegalStateException("Cannot apply ${command::class.simpleName} from $from")

object SurveyStateMachine {
    fun transition(
        current: SurveyRuntimeState,
        command: SurveyCommand,
        wallTimeEpochMs: Long,
        monotonicNs: Long,
    ): SurveyTransition {
        val next = nextStatus(current.status, command)
        if (next == current.status) return SurveyTransition(current, changed = false, shouldStream = next == SurveyStatus.ACTIVE)
        require(wallTimeEpochMs >= current.lastWallTimeEpochMs) { "Wall time must not move backward" }
        require(monotonicNs >= current.lastMonotonicNs) { "Monotonic time must not move backward" }
        return SurveyTransition(
            state = current.copy(
                status = next,
                revision = current.revision + 1,
                lastWallTimeEpochMs = wallTimeEpochMs,
                lastMonotonicNs = monotonicNs,
                failureExplanation = (command as? SurveyCommand.Fail)?.explanation,
            ),
            changed = true,
            shouldStream = next == SurveyStatus.ACTIVE,
        )
    }

    fun recoverAfterProcessDeath(
        persisted: SurveyRuntimeState,
        wallTimeEpochMs: Long,
        monotonicNs: Long,
    ): SurveyTransition = when (persisted.status) {
        SurveyStatus.ACTIVE, SurveyStatus.VALIDATING -> SurveyTransition(
            state = persisted.copy(
                status = SurveyStatus.PAUSED,
                revision = persisted.revision + 1,
                lastWallTimeEpochMs = wallTimeEpochMs,
                lastMonotonicNs = monotonicNs,
            ),
            changed = true,
            shouldStream = false,
            gapReason = GapReason.PROCESS_DEATH,
        )
        SurveyStatus.FINALIZING -> transition(persisted, SurveyCommand.Finalize, wallTimeEpochMs, monotonicNs)
        else -> SurveyTransition(persisted, changed = false, shouldStream = false)
    }

    private fun nextStatus(from: SurveyStatus, command: SurveyCommand): SurveyStatus = when (command) {
        SurveyCommand.Validate -> when (from) {
            SurveyStatus.DRAFT -> SurveyStatus.VALIDATING
            SurveyStatus.VALIDATING -> from
            else -> throw InvalidSurveyTransition(from, command)
        }
        SurveyCommand.Start -> when (from) {
            SurveyStatus.VALIDATING -> SurveyStatus.ACTIVE
            SurveyStatus.ACTIVE -> from
            else -> throw InvalidSurveyTransition(from, command)
        }
        SurveyCommand.Pause -> when (from) {
            SurveyStatus.ACTIVE -> SurveyStatus.PAUSED
            SurveyStatus.PAUSED -> from
            else -> throw InvalidSurveyTransition(from, command)
        }
        SurveyCommand.Resume -> when (from) {
            SurveyStatus.PAUSED -> SurveyStatus.ACTIVE
            SurveyStatus.ACTIVE -> from
            else -> throw InvalidSurveyTransition(from, command)
        }
        SurveyCommand.Stop -> when (from) {
            SurveyStatus.ACTIVE, SurveyStatus.PAUSED -> SurveyStatus.FINALIZING
            SurveyStatus.FINALIZING, SurveyStatus.COMPLETE -> from
            else -> throw InvalidSurveyTransition(from, command)
        }
        SurveyCommand.Finalize -> when (from) {
            SurveyStatus.FINALIZING -> SurveyStatus.COMPLETE
            SurveyStatus.COMPLETE -> from
            else -> throw InvalidSurveyTransition(from, command)
        }
        is SurveyCommand.Fail -> when (from) {
            SurveyStatus.COMPLETE -> throw InvalidSurveyTransition(from, command)
            SurveyStatus.FAILED -> from
            else -> SurveyStatus.FAILED
        }
    }
}
