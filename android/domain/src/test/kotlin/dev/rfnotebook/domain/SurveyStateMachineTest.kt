package dev.rfnotebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyStateMachineTest {
    private val draft = SurveyRuntimeState(
        surveyId = "survey-1",
        status = SurveyStatus.DRAFT,
        revision = 0,
        lastWallTimeEpochMs = 100,
        lastMonotonicNs = 1_000,
    )

    @Test
    fun completeLifecycleOnlyStreamsWhileActive() {
        val validating = SurveyStateMachine.transition(draft, SurveyCommand.Validate, 101, 1_001)
        val active = SurveyStateMachine.transition(validating.state, SurveyCommand.Start, 102, 1_002)
        val paused = SurveyStateMachine.transition(active.state, SurveyCommand.Pause, 103, 1_003)
        val resumed = SurveyStateMachine.transition(paused.state, SurveyCommand.Resume, 104, 1_004)
        val finalizing = SurveyStateMachine.transition(resumed.state, SurveyCommand.Stop, 105, 1_005)
        val complete = SurveyStateMachine.transition(finalizing.state, SurveyCommand.Finalize, 106, 1_006)

        assertFalse(validating.shouldStream)
        assertTrue(active.shouldStream)
        assertFalse(paused.shouldStream)
        assertTrue(resumed.shouldStream)
        assertFalse(finalizing.shouldStream)
        assertEquals(SurveyStatus.COMPLETE, complete.state.status)
    }

    @Test
    fun repeatedStartAndFinalizeAreIdempotent() {
        val validating = SurveyStateMachine.transition(draft, SurveyCommand.Validate, 101, 1_001).state
        val active = SurveyStateMachine.transition(validating, SurveyCommand.Start, 102, 1_002).state
        val repeatedStart = SurveyStateMachine.transition(active, SurveyCommand.Start, 999, 9_999)

        assertFalse(repeatedStart.changed)
        assertEquals(active, repeatedStart.state)

        val finalizing = SurveyStateMachine.transition(active, SurveyCommand.Stop, 103, 1_003).state
        val complete = SurveyStateMachine.transition(finalizing, SurveyCommand.Finalize, 104, 1_004).state
        val repeatedFinalize = SurveyStateMachine.transition(complete, SurveyCommand.Finalize, 999, 9_999)

        assertFalse(repeatedFinalize.changed)
        assertEquals(complete, repeatedFinalize.state)
    }

    @Test(expected = InvalidSurveyTransition::class)
    fun draftCannotJumpDirectlyToActive() {
        SurveyStateMachine.transition(draft, SurveyCommand.Start, 101, 1_001)
    }

    @Test
    fun interruptedStreamingSurveyRecoversPausedWithGap() {
        val active = draft.copy(status = SurveyStatus.ACTIVE)

        val recovery = SurveyStateMachine.recoverAfterProcessDeath(active, 200, 2_000)

        assertEquals(SurveyStatus.PAUSED, recovery.state.status)
        assertFalse(recovery.shouldStream)
        assertEquals(GapReason.PROCESS_DEATH, recovery.gapReason)
    }
}
