package dev.rfnotebook.acquisition

import dev.rfnotebook.domain.RadioConnectionCommand
import dev.rfnotebook.domain.RadioConnectionState
import dev.rfnotebook.domain.RadioConnectionStateMachine
import dev.rfnotebook.domain.RadioConnectionStatus
import dev.rfnotebook.storage.ConnectionStateEventEntity
import dev.rfnotebook.storage.NotebookDao

class RoomRadioConnectionStore(
    private val dao: NotebookDao,
    private val radioDeviceId: String,
    private val clock: SurveyClock,
) {
    suspend fun state(): RadioConnectionState {
        val device = requireNotNull(dao.radioDevice(radioDeviceId)) { "Radio $radioDeviceId does not exist" }
        return RadioConnectionState(
            RadioConnectionStatus.valueOf(device.connectionState),
            device.connectionRevision,
        )
    }

    suspend fun transition(command: RadioConnectionCommand): RadioConnectionState {
        val device = requireNotNull(dao.radioDevice(radioDeviceId)) { "Radio $radioDeviceId does not exist" }
        val current = RadioConnectionState(
            RadioConnectionStatus.valueOf(device.connectionState),
            device.connectionRevision,
        )
        val next = RadioConnectionStateMachine.transition(current, command)
        if (next == current) return current
        val now = clock.now()
        dao.persistConnectionTransition(
            current.revision,
            device.copy(connectionState = next.status.name, connectionRevision = next.revision),
            ConnectionStateEventEntity(
                radioDeviceId = radioDeviceId,
                fromState = current.status.name,
                toState = next.status.name,
                revision = next.revision,
                wallTimeEpochMs = now.wallTimeEpochMs,
                monotonicNs = now.monotonicNs,
                explanation = next.explanation,
            ),
        )
        return next
    }
}
