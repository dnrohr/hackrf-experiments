package dev.rfnotebook.maps

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

const val MAP_AGGREGATION_VERSION = "m3-grid-v1"

enum class MapLocationKind { DIRECT, INTERPOLATED, MISSING, STALE }
enum class MapConfidence { LOW, MEDIUM, HIGH }

data class MapObservation(
    val id: String,
    val fingerprintId: String,
    val surveyId: String,
    val equipmentProfileVersionId: String,
    val timestampEpochMs: Long,
    val centerFrequencyHz: Long,
    val bandwidthHz: Long,
    val detectionType: String,
    val relativeStrengthDb: Float,
    val detectionConfidence: Float,
    val latitude: Double?,
    val longitude: Double?,
    val horizontalAccuracyM: Float?,
    val locationKind: MapLocationKind,
)

data class MapFilters(
    val surveyIds: Set<String> = emptySet(),
    val startEpochMs: Long? = null,
    val endEpochMs: Long? = null,
    val minimumFrequencyHz: Long? = null,
    val maximumFrequencyHz: Long? = null,
    val minimumBandwidthHz: Long? = null,
    val maximumBandwidthHz: Long? = null,
    val detectionTypes: Set<String> = emptySet(),
    val equipmentProfileVersionIds: Set<String> = emptySet(),
    val minimumDetectionConfidence: Float = 0f,
) {
    fun matches(value: MapObservation): Boolean =
        (surveyIds.isEmpty() || value.surveyId in surveyIds) &&
            (startEpochMs == null || value.timestampEpochMs >= startEpochMs) &&
            (endEpochMs == null || value.timestampEpochMs <= endEpochMs) &&
            (minimumFrequencyHz == null || value.centerFrequencyHz >= minimumFrequencyHz) &&
            (maximumFrequencyHz == null || value.centerFrequencyHz <= maximumFrequencyHz) &&
            (minimumBandwidthHz == null || value.bandwidthHz >= minimumBandwidthHz) &&
            (maximumBandwidthHz == null || value.bandwidthHz <= maximumBandwidthHz) &&
            (detectionTypes.isEmpty() || value.detectionType in detectionTypes) &&
            (equipmentProfileVersionIds.isEmpty() || value.equipmentProfileVersionId in equipmentProfileVersionIds) &&
            value.detectionConfidence >= minimumDetectionConfidence
}

data class GeographicCell(
    val id: String,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val cellSizeM: Double,
    val medianRelativeDb: Float,
    val upperQuartileRelativeDb: Float,
    val spreadDb: Float,
    val sampleCount: Int,
    val surveyCount: Int,
    val maximumAccuracyM: Float,
    val interpolatedSampleCount: Int,
    val confidence: MapConfidence,
    val firstSeenEpochMs: Long,
    val lastSeenEpochMs: Long,
    val equipmentProfileVersionIds: Set<String>,
    val algorithmVersion: String = MAP_AGGREGATION_VERSION,
)

data class GeographicAggregationResult(
    val cells: List<GeographicCell>,
    val visibleObservations: List<MapObservation>,
    val missingLocationCount: Int,
    val staleLocationCount: Int,
    val interpolatedLocationCount: Int,
    val incompatibleEquipmentCount: Int = 0,
    val comparabilityWarning: String? = null,
    val algorithmVersion: String = MAP_AGGREGATION_VERSION,
)

object GeographicAggregator {
    fun prepare(
        observations: List<MapObservation>,
        filters: MapFilters,
        comparableEquipmentProfileVersionId: String?,
        includeIncompatibleEquipment: Boolean,
        zoom: Double,
    ): GeographicAggregationResult {
        val filtered = observations.filter(filters::matches)
        val incompatible = if (comparableEquipmentProfileVersionId == null) emptyList() else {
            filtered.filter { it.equipmentProfileVersionId != comparableEquipmentProfileVersionId }
        }
        val comparable = if (includeIncompatibleEquipment) filtered else filtered - incompatible.toSet()
        val aggregate = aggregate(comparable, zoom)
        val equipment = incompatible.map { it.equipmentProfileVersionId }.distinct().sorted()
        return aggregate.copy(
            incompatibleEquipmentCount = incompatible.size,
            comparabilityWarning = equipment.takeIf { it.isNotEmpty() }?.let {
                "Excluded ${incompatible.size} observations recorded with incompatible equipment: ${it.joinToString()}. Compare them as separate layers."
            },
        )
    }

    fun aggregate(observations: List<MapObservation>, zoom: Double): GeographicAggregationResult {
        val missing = observations.count { it.locationKind == MapLocationKind.MISSING || it.latitude == null || it.longitude == null }
        val stale = observations.count { it.locationKind == MapLocationKind.STALE }
        val interpolated = observations.count { it.locationKind == MapLocationKind.INTERPOLATED }
        val located = observations.filter {
            it.latitude != null && it.longitude != null && it.locationKind != MapLocationKind.MISSING && it.locationKind != MapLocationKind.STALE
        }
        if (located.isEmpty()) return GeographicAggregationResult(emptyList(), observations, missing, stale, interpolated)

        val representativeLatitude = located.mapNotNull { it.latitude }.sorted()[located.size / 2]
        val viewportCellM = metersPerPixel(representativeLatitude, zoom) * TARGET_CELL_PIXELS
        val accuracyFloorM = located.maxOf { (it.horizontalAccuracyM ?: 0f).toDouble() * ACCURACY_DIAMETER }
        val cellSizeM = nextPowerOfTwo(max(MINIMUM_CELL_M, max(viewportCellM, accuracyFloorM)))
        val grouped = located.groupBy { observation ->
            val projected = project(observation.latitude!!, observation.longitude!!)
            GridKey(floor(projected.first / cellSizeM).toLong(), floor(projected.second / cellSizeM).toLong())
        }
        val cells = grouped.map { (key, values) -> createCell(key, values, cellSizeM) }
            .sortedWith(compareByDescending<GeographicCell> { it.medianRelativeDb }.thenBy { it.id })
        return GeographicAggregationResult(cells, observations, missing, stale, interpolated)
    }

    private fun createCell(key: GridKey, values: List<MapObservation>, cellSizeM: Double): GeographicCell {
        val strengths = values.map { it.relativeStrengthDb }.sorted()
        val center = unproject((key.x + 0.5) * cellSizeM, (key.y + 0.5) * cellSizeM)
        val surveys = values.map { it.surveyId }.toSet()
        val confidence = when {
            values.size >= HIGH_CONFIDENCE_SAMPLES && surveys.size >= HIGH_CONFIDENCE_SURVEYS -> MapConfidence.HIGH
            values.size >= MEDIUM_CONFIDENCE_SAMPLES -> MapConfidence.MEDIUM
            else -> MapConfidence.LOW
        }
        return GeographicCell(
            id = "${cellSizeM.toLong()}:${key.x}:${key.y}",
            centerLatitude = center.first,
            centerLongitude = center.second,
            cellSizeM = cellSizeM,
            medianRelativeDb = percentile(strengths, 0.5),
            upperQuartileRelativeDb = percentile(strengths, 0.75),
            spreadDb = percentile(strengths, 0.75) - percentile(strengths, 0.25),
            sampleCount = values.size,
            surveyCount = surveys.size,
            maximumAccuracyM = values.maxOf { it.horizontalAccuracyM ?: 0f },
            interpolatedSampleCount = values.count { it.locationKind == MapLocationKind.INTERPOLATED },
            confidence = confidence,
            firstSeenEpochMs = values.minOf { it.timestampEpochMs },
            lastSeenEpochMs = values.maxOf { it.timestampEpochMs },
            equipmentProfileVersionIds = values.map { it.equipmentProfileVersionId }.toSet(),
        )
    }

    private fun percentile(sorted: List<Float>, fraction: Double): Float {
        if (sorted.isEmpty()) return Float.NaN
        val index = (ceil(fraction * sorted.size).toInt() - 1).coerceIn(sorted.indices)
        return sorted[index]
    }

    private fun metersPerPixel(latitude: Double, zoom: Double): Double =
        cos(Math.toRadians(latitude)) * EARTH_CIRCUMFERENCE_M / (TILE_SIZE * 2.0.pow(zoom))

    private fun nextPowerOfTwo(value: Double): Double = 2.0.pow(ceil(ln(value) / ln(2.0)))

    private fun project(latitude: Double, longitude: Double): Pair<Double, Double> {
        val x = EARTH_RADIUS_M * Math.toRadians(longitude)
        val clamped = latitude.coerceIn(-WEB_MERCATOR_LIMIT, WEB_MERCATOR_LIMIT)
        val y = EARTH_RADIUS_M * ln(tan(PI / 4.0 + Math.toRadians(clamped) / 2.0))
        return x to y
    }

    private fun unproject(x: Double, y: Double): Pair<Double, Double> {
        val longitude = Math.toDegrees(x / EARTH_RADIUS_M)
        val latitude = Math.toDegrees(2.0 * kotlin.math.atan(kotlin.math.exp(y / EARTH_RADIUS_M)) - PI / 2.0)
        return latitude to longitude
    }

    private data class GridKey(val x: Long, val y: Long)

    private const val EARTH_RADIUS_M = 6_378_137.0
    private const val EARTH_CIRCUMFERENCE_M = 40_075_016.68557849
    private const val WEB_MERCATOR_LIMIT = 85.05112878
    private const val TILE_SIZE = 512.0
    private const val TARGET_CELL_PIXELS = 24.0
    private const val MINIMUM_CELL_M = 8.0
    private const val ACCURACY_DIAMETER = 2.0
    private const val MEDIUM_CONFIDENCE_SAMPLES = 3
    private const val HIGH_CONFIDENCE_SAMPLES = 5
    private const val HIGH_CONFIDENCE_SURVEYS = 2
}
