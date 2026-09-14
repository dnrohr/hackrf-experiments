package dev.rfnotebook.radio.hackrf

import dev.rfnotebook.radio.api.RadioErrorCode
import dev.rfnotebook.radio.api.RadioException

internal class OpenSessionRegistry {
    private val serialSuffixes = mutableSetOf<String>()

    @Synchronized
    fun acquire(serialSuffix: String) {
        if (!serialSuffixes.add(serialSuffix)) {
            throw RadioException(RadioErrorCode.SESSION_ALREADY_OPEN, "A session for HackRF …$serialSuffix is already open")
        }
    }

    @Synchronized
    fun release(serialSuffix: String) {
        serialSuffixes.remove(serialSuffix)
    }
}
