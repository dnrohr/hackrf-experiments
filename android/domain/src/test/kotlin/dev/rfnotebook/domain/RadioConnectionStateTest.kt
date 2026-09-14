package dev.rfnotebook.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioConnectionStateTest {
    @Test fun `permission opening survey and close lifecycle is explicit`() {
        var state = RadioConnectionState(RadioConnectionStatus.DISCONNECTED, 0)
        state = RadioConnectionStateMachine.transition(state, RadioConnectionCommand.Attach(false))
        assertEquals(RadioConnectionStatus.PERMISSION_REQUIRED, state.status)
        state = RadioConnectionStateMachine.transition(state, RadioConnectionCommand.PermissionGranted)
        state = RadioConnectionStateMachine.transition(state, RadioConnectionCommand.Opened)
        state = RadioConnectionStateMachine.transition(state, RadioConnectionCommand.StartSurvey)
        assertEquals(RadioConnectionStatus.SURVEYING, state.status)
        state = RadioConnectionStateMachine.transition(state, RadioConnectionCommand.Close)
        state = RadioConnectionStateMachine.transition(state, RadioConnectionCommand.Closed)
        assertEquals(RadioConnectionStatus.DISCONNECTED, state.status)
    }

    @Test fun `recoverable error retains explanation and can retry`() {
        val ready = RadioConnectionState(RadioConnectionStatus.READY, 2)
        val failed = RadioConnectionStateMachine.transition(ready, RadioConnectionCommand.RecoverableError("USB detached"))
        val retrying = RadioConnectionStateMachine.transition(failed, RadioConnectionCommand.Retry)

        assertEquals("USB detached", failed.explanation)
        assertEquals(RadioConnectionStatus.OPENING, retrying.status)
    }

    @Test(expected = InvalidRadioConnectionTransition::class)
    fun `cannot survey before radio is ready`() {
        RadioConnectionStateMachine.transition(
            RadioConnectionState(RadioConnectionStatus.DISCONNECTED, 0),
            RadioConnectionCommand.StartSurvey,
        )
    }
}
