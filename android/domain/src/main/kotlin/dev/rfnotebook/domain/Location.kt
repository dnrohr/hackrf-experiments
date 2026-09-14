package dev.rfnotebook.domain

import kotlin.math.abs

data class LocationFix(
    val id: String,
    val surveyId: String,
    val wallTimeEpochMs: Long,
    val monotonicNs: Long,
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyM: Float,
    val altitudeM: Double?,
    val speedMps: Float?,
    val bearingDegrees: Float?,
    val provider: String,
    val isInterpolated: Boolean,
) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
        require(horizontalAccuracyM >= 0f && horizontalAccuracyM.isFinite())
    }
}

data class LocationAssociationPolicy(
    val maxFixAgeNs: Long,
    val maxInterpolationGapNs: Long,
) {
    init {
        require(maxFixAgeNs >= 0)
        require(maxInterpolationGapNs >= 0)
    }
}

enum class LocationAssociationKind { DIRECT, INTERPOLATED, MISSING, STALE }

data class LocationAssociation(
    val kind: LocationAssociationKind,
    val fix: LocationFix?,
    val fixAgeNs: Long?,
)

object LocationAssociator {
    fun associate(
        observationMonotonicNs: Long,
        fixes: List<LocationFix>,
        policy: LocationAssociationPolicy,
    ): LocationAssociation {
        if (fixes.isEmpty()) return LocationAssociation(LocationAssociationKind.MISSING, null, null)
        val sorted = fixes.sortedBy { it.monotonicNs }
        val closest = sorted.minBy { absDifference(it.monotonicNs, observationMonotonicNs) }
        val closestAge = absDifference(closest.monotonicNs, observationMonotonicNs)
        if (closestAge <= policy.maxFixAgeNs) {
            return LocationAssociation(LocationAssociationKind.DIRECT, closest, closestAge)
        }

        val before = sorted.lastOrNull { it.monotonicNs < observationMonotonicNs }
        val after = sorted.firstOrNull { it.monotonicNs > observationMonotonicNs }
        if (before != null && after != null) {
            val gap = after.monotonicNs - before.monotonicNs
            if (gap <= policy.maxInterpolationGapNs) {
                val ratio = (observationMonotonicNs - before.monotonicNs).toDouble() / gap.toDouble()
                val interpolated = before.copy(
                    id = "interpolated:${before.id}:${after.id}:$observationMonotonicNs",
                    wallTimeEpochMs = interpolate(before.wallTimeEpochMs, after.wallTimeEpochMs, ratio),
                    monotonicNs = observationMonotonicNs,
                    latitude = interpolate(before.latitude, after.latitude, ratio),
                    longitude = interpolate(before.longitude, after.longitude, ratio),
                    horizontalAccuracyM = maxOf(before.horizontalAccuracyM, after.horizontalAccuracyM),
                    altitudeM = interpolateNullable(before.altitudeM, after.altitudeM, ratio),
                    speedMps = interpolateNullable(before.speedMps, after.speedMps, ratio),
                    bearingDegrees = null,
                    provider = "interpolated:${before.provider}:${after.provider}",
                    isInterpolated = true,
                )
                return LocationAssociation(LocationAssociationKind.INTERPOLATED, interpolated, 0)
            }
        }
        return LocationAssociation(LocationAssociationKind.STALE, null, closestAge)
    }

    private fun absDifference(a: Long, b: Long): Long = if (a >= b) a - b else b - a
    private fun interpolate(a: Double, b: Double, ratio: Double): Double = a + (b - a) * ratio
    private fun interpolate(a: Long, b: Long, ratio: Double): Long = (a + (b - a) * ratio).toLong()
    private fun interpolateNullable(a: Double?, b: Double?, ratio: Double): Double? =
        if (a != null && b != null) interpolate(a, b, ratio) else null
    private fun interpolateNullable(a: Float?, b: Float?, ratio: Double): Float? =
        if (a != null && b != null) interpolate(a.toDouble(), b.toDouble(), ratio).toFloat() else null
}
