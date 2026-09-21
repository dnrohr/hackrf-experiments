package dev.rfnotebook.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeographicAggregationTest {
    @Test
    fun cellSizeAdaptsToZoomButNeverDropsBelowAccuracyDiameter() {
        val values = listOf(observation("a", accuracyM = 15f), observation("b", latitude = 40.00001, accuracyM = 15f))

        val overview = GeographicAggregator.aggregate(values, zoom = 11.0).cells.single()
        val detail = GeographicAggregator.aggregate(values, zoom = 19.0).cells.single()

        assertTrue(overview.cellSizeM > detail.cellSizeM)
        assertTrue(detail.cellSizeM >= 30.0)
    }

    @Test
    fun offlineMetadataRoundTripsBoundedRequest() {
        val request = OfflineRegionRequest(
            "Repeat route", OfflineBounds(40.72, -73.99, 40.70, -74.02), 11.0, 16.0,
        )

        assertEquals(request, OfflineRegionMetadata.decode(OfflineRegionMetadata.encode(request)))
    }

    @Test
    fun deterministicRepeatRouteFixturePreservesChangedGainAndMissingEvidence() {
        val observations = requireNotNull(javaClass.classLoader?.getResourceAsStream("m3-repeat-route.csv"))
            .bufferedReader().readLines().drop(1).map { row ->
                val value = row.split(',', ignoreCase = false, limit = 14)
                MapObservation(
                    id = value[0], fingerprintId = value[1], surveyId = value[2],
                    equipmentProfileVersionId = value[3], timestampEpochMs = value[4].toLong(),
                    centerFrequencyHz = value[5].toLong(), bandwidthHz = value[6].toLong(),
                    detectionType = value[7], relativeStrengthDb = value[8].toFloat(),
                    detectionConfidence = value[9].toFloat(),
                    latitude = value[10].toDoubleOrNull(), longitude = value[11].toDoubleOrNull(),
                    horizontalAccuracyM = value[12].toFloatOrNull(), locationKind = MapLocationKind.valueOf(value[13]),
                )
            }
        val result = GeographicAggregator.prepare(observations, MapFilters(), "equipment:v1", false, 16.0)

        assertEquals(1, result.missingLocationCount)
        assertEquals(1, result.interpolatedLocationCount)
        assertEquals(1, result.incompatibleEquipmentCount)
        assertEquals(6, result.cells.sumOf { it.sampleCount })
    }

    @Test
    fun accuracyLimitsCellSizeAndSingleOutlierCannotBecomeHighConfidence() {
        val observations = listOf(
            observation("a", accuracyM = 42f, strength = -72f),
            observation("b", latitude = 40.00002, accuracyM = 38f, strength = -70f),
            observation("c", latitude = 40.00004, accuracyM = 40f, strength = -18f),
        )

        val cell = GeographicAggregator.aggregate(observations, zoom = 18.0).cells.single()

        assertTrue(cell.cellSizeM >= 80.0)
        assertEquals(-70f, cell.medianRelativeDb, 0.01f)
        assertEquals(-18f, cell.upperQuartileRelativeDb, 0.01f)
        assertEquals(MapConfidence.MEDIUM, cell.confidence)
        assertFalse(cell.confidence == MapConfidence.HIGH)
    }

    @Test
    fun repeatedSupportAcrossSurveysCanBeHighConfidence() {
        val observations = (0 until 6).map { index ->
            observation(
                id = "p$index",
                survey = if (index < 3) "survey-a" else "survey-b",
                latitude = 40.0 + index * 0.000001,
                accuracyM = 5f,
                strength = -60f + index,
            )
        }

        val cell = GeographicAggregator.aggregate(observations, zoom = 16.0).cells.single()

        assertEquals(6, cell.sampleCount)
        assertEquals(2, cell.surveyCount)
        assertEquals(MapConfidence.HIGH, cell.confidence)
    }

    @Test
    fun missingAndInterpolatedLocationsRemainExplicit() {
        val result = GeographicAggregator.aggregate(
            listOf(
                observation("direct"),
                observation("interpolated", locationKind = MapLocationKind.INTERPOLATED),
                observation("missing", latitude = null, longitude = null, locationKind = MapLocationKind.MISSING),
            ),
            zoom = 15.0,
        )

        assertEquals(1, result.missingLocationCount)
        assertEquals(1, result.interpolatedLocationCount)
        assertTrue(result.cells.any { it.interpolatedSampleCount == 1 })
    }

    @Test
    fun everyRequiredFilterDimensionIsApplied() {
        val matching = observation("match", confidence = 0.9f)
        val rows = listOf(
            matching,
            observation("survey", survey = "other"),
            observation("time", timestamp = 50),
            observation("frequency", frequencyHz = 433_920_000),
            observation("bandwidth", bandwidthHz = 300_000),
            observation("type", detectionType = "PERSISTENT_CARRIER"),
            observation("equipment", equipment = "equipment:v2"),
            observation("confidence", confidence = 0.2f),
        )
        val filters = MapFilters(
            surveyIds = setOf("survey-a"),
            startEpochMs = 100,
            endEpochMs = 200,
            minimumFrequencyHz = 914_900_000,
            maximumFrequencyHz = 915_100_000,
            minimumBandwidthHz = 50_000,
            maximumBandwidthHz = 150_000,
            detectionTypes = setOf("DISCRETE_BURST"),
            equipmentProfileVersionIds = setOf("equipment:v1"),
            minimumDetectionConfidence = 0.5f,
        )

        assertEquals(listOf("match"), rows.filter(filters::matches).map { it.id })
    }

    @Test
    fun incompatibleEquipmentIsExcludedAndExplainedByDefault() {
        val result = GeographicAggregator.prepare(
            observations = listOf(
                observation("same"),
                observation("changed-gain", equipment = "equipment:v2"),
            ),
            filters = MapFilters(),
            comparableEquipmentProfileVersionId = "equipment:v1",
            includeIncompatibleEquipment = false,
            zoom = 15.0,
        )

        assertEquals(1, result.incompatibleEquipmentCount)
        assertTrue(result.comparabilityWarning!!.contains("equipment:v2"))
        assertEquals(1, result.cells.sumOf { it.sampleCount })
    }

    private fun observation(
        id: String,
        survey: String = "survey-a",
        timestamp: Long = 150,
        frequencyHz: Long = 915_000_000,
        bandwidthHz: Long = 100_000,
        detectionType: String = "DISCRETE_BURST",
        equipment: String = "equipment:v1",
        latitude: Double? = 40.0,
        longitude: Double? = -74.0,
        accuracyM: Float = 5f,
        strength: Float = -60f,
        confidence: Float = 0.8f,
        locationKind: MapLocationKind = MapLocationKind.DIRECT,
    ) = MapObservation(
        id, "fingerprint-a", survey, equipment, timestamp, frequencyHz, bandwidthHz,
        detectionType, strength, confidence, latitude, longitude, accuracyM, locationKind,
    )
}
