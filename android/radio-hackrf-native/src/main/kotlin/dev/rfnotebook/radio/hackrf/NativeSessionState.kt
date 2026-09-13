package dev.rfnotebook.radio.hackrf

internal class NativeSessionState {
    enum class State { CLOSED, OPEN, STREAMING }
    var state: State = State.CLOSED
        private set

    fun open() { check(state == State.CLOSED); state = State.OPEN }
    fun start() { check(state == State.OPEN); state = State.STREAMING }
    fun stop() { if (state == State.STREAMING) state = State.OPEN }
    fun close() { state = State.CLOSED }
    fun onUsbDetach() = close()
}
