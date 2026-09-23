package dev.rfnotebook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusedCaptureStateTest {
    @Test fun `preview opening preserves originating equipment while clearing transient evidence`() {
        val equipment = FocusedCaptureEquipment(
            versionId = "equipment:v7",
            sampleRateHz = 4_000_000,
            basebandFilterHz = 3_500_000,
            lnaGainDb = 24,
            vgaGainDb = 30,
            safetyDifference = "RF amplifier forced off",
        )
        val opening = FocusedCaptureState(
            phase = "Complete",
            measuredSamplesPerSecond = 4_000_000,
            relativePowerDbfs = -42f,
            waterfallRows = listOf("row"),
            progressBytes = 10,
            expectedBytes = 10,
            problem = "old",
            equipment = equipment,
            settingsReady = true,
        ).openingPreview()

        assertEquals("Opening receive-only radio", opening.phase)
        assertEquals(equipment, opening.equipment)
        assertEquals(true, opening.settingsReady)
        assertEquals(0, opening.measuredSamplesPerSecond)
        assertEquals(emptyList<String>(), opening.waterfallRows)
        assertNull(opening.problem)
    }
}
