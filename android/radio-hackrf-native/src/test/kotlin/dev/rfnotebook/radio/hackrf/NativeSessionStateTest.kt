package dev.rfnotebook.radio.hackrf

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeSessionStateTest {
    @Test fun `cancel and close release streaming state`() {
        val session = NativeSessionState().apply { open(); start(); stop(); close() }
        assertEquals(NativeSessionState.State.CLOSED, session.state)
    }

    @Test fun `detach closes an active stream`() {
        val session = NativeSessionState().apply { open(); start(); onUsbDetach() }
        assertEquals(NativeSessionState.State.CLOSED, session.state)
    }
}
