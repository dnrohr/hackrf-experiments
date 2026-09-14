package dev.rfnotebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTest {
    private val equipment = EquipmentProfile(
        id = "equipment-1",
        name = "Field whip",
        version = 1,
        radioDeviceId = "radio-1",
        antennaName = "Uncalibrated whip",
        antennaBands = listOf(FrequencyRange(100_000_000, 1_000_000_000)),
        adapterNotes = "USB adapter",
        sampleRateHz = 8_000_000,
        basebandFilterHz = 7_000_000,
        lnaGainDb = 16,
        vgaGainDb = 20,
        rfAmpEnabled = false,
        antennaPowerEnabled = false,
        createdAtEpochMs = 1,
    )

    @Test
    fun conservativeDefaultsNeverEnablePoweredRfFeatures() {
        val profile = EquipmentProfile.conservativeDefault("equipment", "radio")

        assertFalse(profile.rfAmpEnabled)
        assertFalse(profile.antennaPowerEnabled)
        assertFalse(profile.isCalibrated)
    }

    @Test
    fun editingReferencedEquipmentCreatesANewVersion() {
        val edited = equipment.edit(
            referencedBySurvey = true,
            nowEpochMs = 2,
            changes = EquipmentProfileChanges(vgaGainDb = 24),
        )

        assertEquals(2, edited.version)
        assertEquals("equipment-1", edited.id)
        assertEquals(24, edited.vgaGainDb)
        assertEquals(2, edited.createdAtEpochMs)
    }

    @Test
    fun comparabilityReturnsEveryMeasurementChangingReason() {
        val changed = equipment.copy(
            sampleRateHz = 4_000_000,
            lnaGainDb = 24,
            antennaPowerEnabled = true,
        )

        val result = equipment.compareWith(changed)

        assertFalse(result.isComparable)
        assertEquals(
            setOf(
                IncomparabilityReason.SAMPLE_RATE,
                IncomparabilityReason.LNA_GAIN,
                IncomparabilityReason.ANTENNA_POWER,
            ),
            result.reasons,
        )
    }

    @Test
    fun bandValidationRejectsOverlapAndUnsupportedRate() {
        val profile = BandProfile(
            id = "band",
            name = "Invalid",
            version = 1,
            ranges = listOf(
                FrequencyRange(902_000_000, 915_000_000),
                FrequencyRange(914_000_000, 928_000_000),
            ),
            excludedRanges = emptyList(),
            binWidthHz = 100_000,
            targetRevisitMs = 1_000,
            thresholdSnrDb = 8f,
            minimumBandwidthHz = 50_000,
            equipmentProfileId = equipment.id,
        )

        val errors = profile.validate(
            RadioCapabilities(1_000_000, 6_000_000_000, setOf(2_000_000, 4_000_000)),
            equipment.copy(sampleRateHz = 8_000_000),
        )

        assertTrue(errors.any { it.code == BandValidationCode.OVERLAPPING_RANGES })
        assertTrue(errors.any { it.code == BandValidationCode.UNSUPPORTED_SAMPLE_RATE })
    }

    @Test
    fun starterProfilesIncludeAllSpecifiedExplorationRegions() {
        val starters = StarterBandProfiles.create(equipment.id)

        assertEquals(5, starters.size)
        assertEquals(
            listOf("315 MHz", "433 MHz", "150–174 MHz", "450–470 MHz", "902–928 MHz"),
            starters.map { it.name },
        )
        assertTrue(starters.all { it.explorationAidDisclaimer.isNotBlank() })
    }

    @Test
    fun cycleEstimatorAccountsForExcludedSpectrum() {
        val profile = StarterBandProfiles.create(equipment.id).last().copy(
            excludedRanges = listOf(FrequencyRange(910_000_000, 912_000_000)),
            binWidthHz = 100_000,
        )

        val estimate = profile.estimateCycleDuration(measuredBinsPerSecond = 2_000.0)

        assertEquals(120L, estimate.estimatedDurationMs)
        assertEquals(240L, estimate.includedBinCount)
    }
}
