package dev.rfnotebook.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class SurveyDurationCalculatorTest {
    @Test fun `collection time freezes while paused and excludes process death gap`() {
        val events = listOf(
            event("VALIDATING", "ACTIVE", 1_000_000_000L),
            event("ACTIVE", "PAUSED", 6_000_000_000L),
            event("PAUSED", "ACTIVE", 10_000_000_000L),
            event("ACTIVE", "PAUSED", 20_000_000_000L),
        )
        val processGap = AcquisitionGapEntity(
            "gap", "survey", "PROCESS_DEATH", 0, 16_000_000_000L,
            0, 20_000_000_000L, 0, "process interruption",
        )

        assertEquals(
            11_000L,
            SurveyDurationCalculator.activeMilliseconds(events, listOf(processGap), 30_000_000_000L, "PAUSED"),
        )
        assertEquals(
            11_000L,
            SurveyDurationCalculator.activeMilliseconds(events, listOf(processGap), 60_000_000_000L, "PAUSED"),
        )
    }

    @Test fun `active collection advances only current active segment`() {
        val events = listOf(event("VALIDATING", "ACTIVE", 2_000_000_000L))

        assertEquals(
            3_000L,
            SurveyDurationCalculator.activeMilliseconds(events, emptyList(), 5_000_000_000L, "ACTIVE"),
        )
    }

    @Test fun `event order and wall clock fallback survive monotonic reset after reboot`() {
        val events = listOf(
            event("VALIDATING", "ACTIVE", 9_000_000_000L, wallMs = 1_000, id = 1),
            event("ACTIVE", "PAUSED", 1_000_000_000L, wallMs = 6_000, id = 2),
            event("PAUSED", "ACTIVE", 2_000_000_000L, wallMs = 7_000, id = 3),
            event("ACTIVE", "PAUSED", 4_000_000_000L, wallMs = 9_000, id = 4),
        )

        assertEquals(
            7_000L,
            SurveyDurationCalculator.activeMilliseconds(events, emptyList(), 8_000_000_000L, "PAUSED"),
        )
    }

    private fun event(
        from: String,
        to: String,
        monotonicNs: Long,
        wallMs: Long = 0,
        id: Long = 0,
    ) = SurveyStateEventEntity(
        id = id,
        surveyId = "survey", fromStatus = from, toStatus = to,
        wallTimeEpochMs = wallMs, monotonicNs = monotonicNs, explanation = null,
    )
}
