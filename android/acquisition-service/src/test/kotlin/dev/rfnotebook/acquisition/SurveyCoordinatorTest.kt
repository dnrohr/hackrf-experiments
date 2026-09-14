package dev.rfnotebook.acquisition

import dev.rfnotebook.domain.GapReason
import dev.rfnotebook.domain.SurveyCommand
import dev.rfnotebook.domain.SurveyRuntimeState
import dev.rfnotebook.domain.SurveyStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyCoordinatorTest {
    @Test fun `pause stops radio before persisting non-active state`() = runBlocking {
        val order = mutableListOf<String>()
        val store = FakeStore(active(), order)
        val radio = FakeRadio(order)
        val coordinator = SurveyCoordinator(store, radio) { SurveyTime(2, 2) }

        coordinator.command("survey", SurveyCommand.Pause)

        assertEquals(listOf("radio.stop", "store.PAUSED"), order)
        assertFalse(radio.streaming)
    }

    @Test fun `resume persists active before starting radio`() = runBlocking {
        val order = mutableListOf<String>()
        val store = FakeStore(active().copy(status = SurveyStatus.PAUSED), order)
        val radio = FakeRadio(order)
        val coordinator = SurveyCoordinator(store, radio) { SurveyTime(2, 2) }

        coordinator.command("survey", SurveyCommand.Resume)

        assertEquals(listOf("store.ACTIVE", "radio.start"), order)
        assertTrue(radio.streaming)
    }

    @Test fun `process death recovery stops radio and records explicit gap`() = runBlocking {
        val order = mutableListOf<String>()
        val store = FakeStore(active(), order)
        val radio = FakeRadio(order).apply { streaming = true }
        val coordinator = SurveyCoordinator(store, radio) { SurveyTime(2, 2) }

        val recovered = coordinator.recover("survey")

        assertEquals(SurveyStatus.PAUSED, recovered.status)
        assertEquals(GapReason.PROCESS_DEATH, store.gap)
        assertEquals(listOf("radio.stop", "store.PAUSED", "gap.PROCESS_DEATH"), order)
    }

    @Test fun `start failure is persisted as failed`() = runBlocking {
        val order = mutableListOf<String>()
        val store = FakeStore(active().copy(status = SurveyStatus.VALIDATING), order)
        val radio = FakeRadio(order, failStart = true)
        val coordinator = SurveyCoordinator(store, radio) { SurveyTime(store.state.revision + 2, store.state.revision + 2) }

        runCatching { coordinator.command("survey", SurveyCommand.Start) }

        assertEquals(SurveyStatus.FAILED, store.state.status)
        assertTrue(store.state.failureExplanation!!.contains("injected"))
    }

    private fun active() = SurveyRuntimeState("survey", SurveyStatus.ACTIVE, 1, 1, 1)

    private class FakeStore(var state: SurveyRuntimeState, private val order: MutableList<String>) : SurveyStateStore {
        var gap: GapReason? = null
        override suspend fun load(surveyId: String) = state
        override suspend fun save(previousRevision: Long, state: SurveyRuntimeState) {
            check(this.state.revision == previousRevision)
            this.state = state
            order += "store.${state.status}"
        }
        override suspend fun recordGap(surveyId: String, reason: GapReason, wallTimeEpochMs: Long, monotonicNs: Long) {
            gap = reason
            order += "gap.$reason"
        }
    }

    private class FakeRadio(private val order: MutableList<String>, private val failStart: Boolean = false) : SurveyRadioController {
        var streaming = false
        override suspend fun start() {
            order += "radio.start"
            if (failStart) error("injected startup failure")
            streaming = true
        }
        override suspend fun stop() {
            order += "radio.stop"
            streaming = false
        }
    }
}
