package dev.rfnotebook.domain

enum class RadioConnectionStatus {
    DISCONNECTED,
    PERMISSION_REQUIRED,
    OPENING,
    READY,
    SURVEYING,
    CLOSING,
    ERROR_RECOVERABLE,
    ERROR_TERMINAL,
}

sealed interface RadioConnectionCommand {
    data class Attach(val hasPermission: Boolean) : RadioConnectionCommand
    data object PermissionGranted : RadioConnectionCommand
    data object Opened : RadioConnectionCommand
    data object StartSurvey : RadioConnectionCommand
    data object StopSurvey : RadioConnectionCommand
    data object Close : RadioConnectionCommand
    data object Closed : RadioConnectionCommand
    data class RecoverableError(val explanation: String) : RadioConnectionCommand
    data class TerminalError(val explanation: String) : RadioConnectionCommand
    data object Retry : RadioConnectionCommand
}

data class RadioConnectionState(
    val status: RadioConnectionStatus,
    val revision: Long,
    val explanation: String? = null,
)

class InvalidRadioConnectionTransition(from: RadioConnectionStatus, command: RadioConnectionCommand) :
    IllegalStateException("Cannot apply ${command::class.simpleName} from $from")

object RadioConnectionStateMachine {
    fun transition(current: RadioConnectionState, command: RadioConnectionCommand): RadioConnectionState {
        val next = when (command) {
            is RadioConnectionCommand.Attach -> when (current.status) {
                RadioConnectionStatus.DISCONNECTED -> if (command.hasPermission) RadioConnectionStatus.OPENING else RadioConnectionStatus.PERMISSION_REQUIRED
                else -> throw InvalidRadioConnectionTransition(current.status, command)
            }
            RadioConnectionCommand.PermissionGranted -> when (current.status) {
                RadioConnectionStatus.PERMISSION_REQUIRED -> RadioConnectionStatus.OPENING
                else -> throw InvalidRadioConnectionTransition(current.status, command)
            }
            RadioConnectionCommand.Opened -> when (current.status) {
                RadioConnectionStatus.OPENING -> RadioConnectionStatus.READY
                else -> throw InvalidRadioConnectionTransition(current.status, command)
            }
            RadioConnectionCommand.StartSurvey -> when (current.status) {
                RadioConnectionStatus.READY -> RadioConnectionStatus.SURVEYING
                RadioConnectionStatus.SURVEYING -> current.status
                else -> throw InvalidRadioConnectionTransition(current.status, command)
            }
            RadioConnectionCommand.StopSurvey -> when (current.status) {
                RadioConnectionStatus.SURVEYING -> RadioConnectionStatus.READY
                RadioConnectionStatus.READY -> current.status
                else -> throw InvalidRadioConnectionTransition(current.status, command)
            }
            RadioConnectionCommand.Close -> when (current.status) {
                RadioConnectionStatus.DISCONNECTED -> current.status
                RadioConnectionStatus.CLOSING -> current.status
                else -> RadioConnectionStatus.CLOSING
            }
            RadioConnectionCommand.Closed -> when (current.status) {
                RadioConnectionStatus.CLOSING, RadioConnectionStatus.ERROR_TERMINAL -> RadioConnectionStatus.DISCONNECTED
                RadioConnectionStatus.DISCONNECTED -> current.status
                else -> throw InvalidRadioConnectionTransition(current.status, command)
            }
            is RadioConnectionCommand.RecoverableError -> RadioConnectionStatus.ERROR_RECOVERABLE
            is RadioConnectionCommand.TerminalError -> RadioConnectionStatus.ERROR_TERMINAL
            RadioConnectionCommand.Retry -> when (current.status) {
                RadioConnectionStatus.ERROR_RECOVERABLE -> RadioConnectionStatus.OPENING
                else -> throw InvalidRadioConnectionTransition(current.status, command)
            }
        }
        if (next == current.status) return current
        val explanation = when (command) {
            is RadioConnectionCommand.RecoverableError -> command.explanation
            is RadioConnectionCommand.TerminalError -> command.explanation
            else -> null
        }
        return RadioConnectionState(next, current.revision + 1, explanation)
    }
}
