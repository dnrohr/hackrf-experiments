package dev.rfnotebook.storage

import java.io.File

/** Builds a complete, schema-1 survey export from authoritative Room rows. */
class SurveyBundleContentFactory(private val dao: NotebookDao) {
    suspend fun create(surveyId: String, appVersion: String): SurveyBundleContent {
        val survey = requireNotNull(dao.survey(surveyId)) { "Survey not found: $surveyId" }
        require(survey.status == "COMPLETE") { "Only completed surveys can be exported" }
        val equipment = requireNotNull(dao.equipmentProfile(survey.equipmentProfileVersionId))
        val radio = requireNotNull(dao.radioDevice(equipment.radioDeviceId))
        val band = requireNotNull(dao.bandProfile(survey.bandProfileVersionId))
        val ranges = dao.bandRanges(band.versionId)
        val fixes = dao.surveyLocationFixes(surveyId)
        val fixesById = fixes.associateBy { it.id }
        val aggregates = dao.surveyAggregates(surveyId)
        val gaps = dao.surveyGaps(surveyId)
        val captures = dao.surveyIqCaptures(surveyId).flatMap { capture ->
            listOf(capture.filePath, capture.sidecarPath, capture.previewPath).map(::File)
        }

        val observationsCsv = buildString {
            append("time_epoch_ms,frequency_hz,minimum_dbfs,median_dbfs,maximum_dbfs,noise_estimate_dbfs,sample_count,location_state,latitude,longitude,horizontal_accuracy_m,location_interpolated\n")
            aggregates.forEach { aggregate ->
                val fix = aggregate.locationFixId?.let(fixesById::get)
                append(aggregate.timeBucketStartEpochMs).append(',')
                    .append(aggregate.frequencyBinHz).append(',')
                    .append(aggregate.minimumPowerDbfs).append(',')
                    .append(aggregate.medianPowerDbfs).append(',')
                    .append(aggregate.maximumPowerDbfs).append(',')
                    .append(aggregate.noiseEstimateDbfs).append(',')
                    .append(aggregate.sampleCount).append(',')
                    .append(csv(aggregate.locationState)).append(',')
                    .append(fix?.latitude ?: "").append(',')
                    .append(fix?.longitude ?: "").append(',')
                    .append(fix?.horizontalAccuracyM ?: "").append(',')
                    .append(fix?.isInterpolated ?: "").append('\n')
            }
        }
        val routeGeoJson = if (fixes.isEmpty()) {
            "{\"type\":\"FeatureCollection\",\"features\":[]}"
        } else {
            val coordinates = fixes.joinToString(",") { "[${it.longitude},${it.latitude}]" }
            "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"crs\":\"EPSG:4326\",\"accuracyMMin\":${fixes.minOf { it.horizontalAccuracyM }},\"accuracyMMax\":${fixes.maxOf { it.horizontalAccuracyM }}},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$coordinates]}}]}"
        }
        val powerByFix = linkedMapOf<String, MutableList<Float>>()
        aggregates.forEach { aggregate -> aggregate.locationFixId?.let { id ->
            powerByFix.getOrPut(id) { mutableListOf() }.add(aggregate.medianPowerDbfs)
        } }
        val geographicFeatures = powerByFix.mapNotNull { (fixId, values) ->
            val fix = fixesById[fixId] ?: return@mapNotNull null
            values.sort()
            val median = values[values.size / 2]
            "{\"type\":\"Feature\",\"properties\":{\"observedRelativeStrengthDbfs\":$median,\"sampleCount\":${values.size},\"horizontalAccuracyM\":${fix.horizontalAccuracyM},\"uncertainty\":\"GPS accuracy and RF propagation apply\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[${fix.longitude},${fix.latitude}]}}"
        }
        val geographicGeoJson = "{\"type\":\"FeatureCollection\",\"features\":[${geographicFeatures.joinToString(",")}] }"
        val gapsCsv = buildString {
            append("reason,started_epoch_ms,ended_epoch_ms,dropped_units,explanation\n")
            gaps.forEach { gap ->
                append(csv(gap.reason)).append(',').append(gap.startedWallTimeEpochMs).append(',')
                    .append(gap.endedWallTimeEpochMs ?: "").append(',').append(gap.droppedUnitCount).append(',')
                    .append(csv(gap.explanation)).append('\n')
            }
        }

        return SurveyBundleContent(
            surveyId = survey.id,
            surveyName = survey.name,
            generatedAtEpochMs = System.currentTimeMillis(),
            appVersion = appVersion,
            serialSuffix = radio.serialSuffix,
            notes = survey.notes,
            observationsCsv = observationsCsv,
            routeGeoJson = routeGeoJson,
            aggregatesGeoJson = geographicGeoJson,
            captureFiles = captures,
            gapsCsv = gapsCsv,
            equipment = BundleEquipmentMetadata(
                equipment.versionId, equipment.antennaName, equipment.adapterNotes,
                equipment.sampleRateHz, equipment.basebandFilterHz, equipment.lnaGainDb,
                equipment.vgaGainDb, equipment.rfAmpEnabled, equipment.antennaPowerEnabled,
            ),
            band = BundleBandMetadata(
                band.versionId, band.name, band.binWidthHz, band.targetRevisitMs,
                band.thresholdSnrDb, band.minimumBandwidthHz,
                ranges.joinToString(";") { "${it.kind}:${it.startHz}-${it.endHz}" },
            ),
        )
    }

    private fun csv(value: String): String = if (value.any { it in setOf(',', '"', '\n', '\r') }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else value
}
