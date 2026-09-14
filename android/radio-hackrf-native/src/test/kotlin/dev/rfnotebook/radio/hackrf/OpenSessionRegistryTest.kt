package dev.rfnotebook.radio.hackrf

import dev.rfnotebook.radio.api.RadioErrorCode
import dev.rfnotebook.radio.api.RadioException
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenSessionRegistryTest {
    @Test fun `same physical device cannot have two sessions`() {
        val registry = OpenSessionRegistry()
        registry.acquire("abc123")

        val failure = runCatching { registry.acquire("abc123") }.exceptionOrNull() as RadioException

        assertEquals(RadioErrorCode.SESSION_ALREADY_OPEN, failure.code)
    }

    @Test fun `close permits a later session`() {
        val registry = OpenSessionRegistry()
        registry.acquire("abc123")
        registry.release("abc123")
        registry.acquire("abc123")
    }
}
