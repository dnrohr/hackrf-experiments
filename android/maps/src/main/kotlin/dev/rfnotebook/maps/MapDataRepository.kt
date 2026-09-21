package dev.rfnotebook.maps

import dev.rfnotebook.storage.AcquisitionGapEntity
import dev.rfnotebook.storage.LocationFixEntity
import dev.rfnotebook.storage.NotebookDao
import kotlin.math.max

data class MapRoutePoint(
    val surveyId: String,
    val timestampEpochMs: Long,
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyM: Float,
    val isInterpolated: Boolean,
)

data class MapGapSegment(
    val surveyId: String,
    val reason: String,
    val explanation: String,
    val droppedUnitCount: Long,
    val startedEpochMs: Long,
    val endedEpochMs: Long?,
    val before: MapRoutePoint?,
    val after: MapRoutePoint?,
)

data class MapSurveyContext(
    val id: String,
    val name: String,
    val equipmentProfileVersionId: String,
)

data class MapFingerprintLayer(
    val fingerprintId: String,
    val label: String,
    val comparableEquipmentProfileVersionId: String,
    val observations: List<MapObservation>,
)

data class MapDataset(
    val layers: List<MapFingerprintLayer>,
    val routes: List<MapRoutePoint>,
    val gaps: List<MapGapSegment>,
    val surveys: List<MapSurveyContext>,
) {
    val isEmpty: Boolean get() = layers.all { it.observations.isEmpty() }
}

class MapDataRepository(private val dao: NotebookDao) {
    suspend fun load(fingerprintIds: Set<String>): MapDataset {
        val layers = fingerprintIds.mapNotNull { fingerprintId ->
            val fingerprint = dao.fingerprint(fingerprintId) ?: return@mapNotNull null
            val detections = dao.fingerprintDetections(fingerprintId)
            val locationIds = detections.mapNotNull { it.locationFixId }.distinct()
            val locations = if (locationIds.isEmpty()) emptyMap() else dao.locationFixesByIds(locationIds).associateBy { it.id }
            MapFingerprintLayer(
                fingerprintId = fingerprintId,
                label = fingerprint.userLabel.ifBlank { "${fingerprint.nominalFrequencyHz / 1_000_000.0} MHz" },
                comparableEquipmentProfileVersionId = fingerprint.equipmentProfileVersionId,
                observations = detections.map { detection ->
                    val location = detection.locationFixId?.let(locations::get)
                    MapObservation(
                        id = detection.id,
                        fingerprintId = fingerprintId,
                        surveyId = detection.surveyId,
                        equipmentProfileVersionId = detection.equipmentProfileVersionId,
                        timestampEpochMs = detection.startedAtEpochMs,
                        centerFrequencyHz = detection.centerFrequencyHz,
                        bandwidthHz = detection.bandwidthHz,
                        detectionType = detection.kind,
                        relativeStrengthDb = detection.medianPowerDbfs,
                        detectionConfidence = confidenceFromSnr(detection.snrDb),
                        latitude = location?.latitude,
                        longitude = location?.longitude,
                        horizontalAccuracyM = location?.horizontalAccuracyM,
                        locationKind = when {
                            location == null -> MapLocationKind.MISSING
                            location.isInterpolated -> MapLocationKind.INTERPOLATED
                            else -> MapLocationKind.DIRECT
                        },
                    )
                },
            )
        }
        val surveyIds = layers.flatMap { layer -> layer.observations.map { it.surveyId } }.distinct()
        val surveys = surveyIds.mapNotNull { id -> dao.survey(id)?.let { MapSurveyContext(it.id, it.name, it.equipmentProfileVersionId) } }
        val routesBySurvey = surveyIds.associateWith { id -> dao.surveyLocationFixes(id).map { it.toRoutePoint() } }
        val gaps = surveyIds.flatMap { surveyId ->
            val route = routesBySurvey[surveyId].orEmpty()
            dao.surveyGaps(surveyId).map { it.toMapGap(route) }
        }
        return MapDataset(layers, routesBySurvey.values.flatten(), gaps, surveys)
    }

    private fun confidenceFromSnr(snrDb: Float): Float = (max(0f, snrDb) / 20f).coerceIn(0f, 1f)

    private fun LocationFixEntity.toRoutePoint() = MapRoutePoint(
        surveyId, wallTimeEpochMs, latitude, longitude, horizontalAccuracyM, isInterpolated,
    )

    private fun AcquisitionGapEntity.toMapGap(route: List<MapRoutePoint>): MapGapSegment {
        val before = route.lastOrNull { it.timestampEpochMs <= startedWallTimeEpochMs }
        val end = endedWallTimeEpochMs
        val after = end?.let { route.firstOrNull { point -> point.timestampEpochMs >= it } }
        return MapGapSegment(
            surveyId, reason, explanation, droppedUnitCount,
            startedWallTimeEpochMs, endedWallTimeEpochMs, before, after,
        )
    }
}
