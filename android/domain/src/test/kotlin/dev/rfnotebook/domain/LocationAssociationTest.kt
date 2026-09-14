package dev.rfnotebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationAssociationTest {
    private fun fix(timeNs: Long, latitude: Double) = LocationFix(
        id = "fix-$timeNs",
        surveyId = "survey",
        wallTimeEpochMs = timeNs / 1_000_000,
        monotonicNs = timeNs,
        latitude = latitude,
        longitude = -77.0,
        horizontalAccuracyM = 6f,
        altitudeM = null,
        speedMps = 1f,
        bearingDegrees = 90f,
        provider = "gps",
        isInterpolated = false,
    )

    @Test
    fun choosesClosestFreshFixUsingMonotonicTime() {
        val result = LocationAssociator.associate(
            observationMonotonicNs = 1_900_000_000,
            fixes = listOf(fix(1_000_000_000, 1.0), fix(2_000_000_000, 2.0)),
            policy = LocationAssociationPolicy(maxFixAgeNs = 500_000_000, maxInterpolationGapNs = 0),
        )

        assertEquals(LocationAssociationKind.DIRECT, result.kind)
        assertEquals(2.0, result.fix!!.latitude, 0.0)
        assertEquals(100_000_000L, result.fixAgeNs)
    }

    @Test
    fun interpolatesOnlyAcrossBoundedShortGapAndMarksFix() {
        val result = LocationAssociator.associate(
            observationMonotonicNs = 2_000_000_000,
            fixes = listOf(fix(1_000_000_000, 1.0), fix(3_000_000_000, 3.0)),
            policy = LocationAssociationPolicy(maxFixAgeNs = 100_000_000, maxInterpolationGapNs = 3_000_000_000),
        )

        assertEquals(LocationAssociationKind.INTERPOLATED, result.kind)
        assertTrue(result.fix!!.isInterpolated)
        assertEquals(2.0, result.fix.latitude, 0.000_001)
    }

    @Test
    fun staleFixLeavesObservationUnlocated() {
        val result = LocationAssociator.associate(
            observationMonotonicNs = 10_000_000_000,
            fixes = listOf(fix(1_000_000_000, 1.0)),
            policy = LocationAssociationPolicy(maxFixAgeNs = 1_000_000_000, maxInterpolationGapNs = 2_000_000_000),
        )

        assertEquals(LocationAssociationKind.STALE, result.kind)
        assertNull(result.fix)
        assertEquals(9_000_000_000, result.fixAgeNs)
    }
}
