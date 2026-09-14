package dev.rfnotebook.storage

import dev.rfnotebook.domain.EquipmentProfile
import dev.rfnotebook.domain.FrequencyRange
import dev.rfnotebook.domain.StarterBandProfiles
import java.util.UUID

data class SurveyLaunch(
    val surveyId: String,
    val serialSuffix: String,
    val startFrequencyHz: Long,
    val endFrequencyHz: Long,
    val binWidthHz: Int,
    val sampleRateHz: Int,
)

data class SurveySummary(
    val survey: SurveyEntity,
    val aggregateCount: Long,
    val locationFixCount: Long,
    val gapCount: Long,
)

class NotebookSetupRepository(private val dao: NotebookDao) {
    suspend fun recoverableLaunch(): SurveyLaunch? {
        val survey = dao.interruptedSurveys().firstOrNull() ?: return null
        val band = requireNotNull(dao.bandProfile(survey.bandProfileVersionId))
        val equipment = requireNotNull(dao.equipmentProfile(survey.equipmentProfileVersionId))
        val radio = requireNotNull(dao.radioDevice(equipment.radioDeviceId))
        val ranges = dao.bandRanges(band.versionId).filter { it.kind == "INCLUDE" }
        return SurveyLaunch(
            survey.id,
            radio.serialSuffix,
            ranges.minOf { it.startHz },
            ranges.maxOf { it.endHz },
            band.binWidthHz.toInt(),
            equipment.sampleRateHz,
        )
    }

    suspend fun createStarterSurvey(
        serialSuffix: String,
        productName: String,
        nowEpochMs: Long,
        nowMonotonicNs: Long,
        surveyName: String,
        bandName: String = "902–928 MHz",
        sampleRateHz: Int = 4_000_000,
        lnaGainDb: Int = 16,
        vgaGainDb: Int = 16,
    ): SurveyLaunch {
        require(serialSuffix.isNotBlank())
        val radioId = "radio:$serialSuffix"
        val priorRadio = dao.radioDevice(radioId)
        dao.insertRadioDevice(
            RadioDeviceEntity(
                id = radioId,
                model = productName.ifBlank { "HackRF" },
                serialSuffix = serialSuffix,
                hardwareRevision = "Pending receive test",
                firmwareVersion = "Pending receive test",
                usbApiVersion = "Pending receive test",
                firstSeenAtEpochMs = priorRadio?.firstSeenAtEpochMs ?: nowEpochMs,
                lastSeenAtEpochMs = nowEpochMs,
            ),
        )
        val profileId = "equipment:$serialSuffix"
        val requestedEquipment = EquipmentProfile.conservativeDefault(profileId, radioId).copy(
            createdAtEpochMs = nowEpochMs,
            sampleRateHz = sampleRateHz,
            lnaGainDb = lnaGainDb,
            vgaGainDb = vgaGainDb,
        )
        val priorEquipment = dao.activeEquipmentProfiles().filter { it.profileId == profileId }.maxByOrNull { it.version }
        val equipmentEntity = if (priorEquipment == null) {
            requestedEquipment.toEntity("$profileId:v1")
        } else if (priorEquipment.toDomain().compareWith(requestedEquipment).isComparable) {
            priorEquipment
        } else {
            dao.retireEquipmentProfile(priorEquipment.versionId, nowEpochMs)
            requestedEquipment.copy(version = priorEquipment.version + 1).toEntity("$profileId:v${priorEquipment.version + 1}")
        }
        dao.insertEquipmentProfile(equipmentEntity)
        StarterBandProfiles.create(profileId).forEach { profile ->
            val bandProfileId = "${profile.id}:$serialSuffix"
            val priorBand = dao.activeBandProfiles().filter { it.profileId == bandProfileId }.maxByOrNull { it.version }
            val reuse = priorBand?.takeIf {
                it.equipmentProfileVersionId == equipmentEntity.versionId &&
                    it.binWidthHz == profile.binWidthHz && it.targetRevisitMs == profile.targetRevisitMs &&
                    it.thresholdSnrDb == profile.thresholdSnrDb && it.minimumBandwidthHz == profile.minimumBandwidthHz
            }
            val bandEntity = reuse ?: run {
                priorBand?.let { dao.retireBandProfile(it.versionId, nowEpochMs) }
                val version = (priorBand?.version ?: 0) + 1
                BandProfileEntity(
                    versionId = "$bandProfileId:v$version",
                    profileId = bandProfileId,
                    name = profile.name,
                    version = version,
                    equipmentProfileVersionId = equipmentEntity.versionId,
                    binWidthHz = profile.binWidthHz,
                    targetRevisitMs = profile.targetRevisitMs,
                    thresholdSnrDb = profile.thresholdSnrDb,
                    minimumBandwidthHz = profile.minimumBandwidthHz,
                    explorationAidDisclaimer = profile.explorationAidDisclaimer,
                    createdAtEpochMs = nowEpochMs,
                    retiredAtEpochMs = null,
                )
            }
            dao.insertBandProfile(bandEntity)
            dao.insertBandRanges(profile.ranges.mapIndexed { index, range ->
                BandRangeEntity(bandEntity.versionId, "INCLUDE", index, range.startHz, range.endHz)
            })
        }
        val band = dao.activeBandProfiles().first { it.name == bandName }
        val ranges = dao.bandRanges(band.versionId).filter { it.kind == "INCLUDE" }
        val surveyId = UUID.randomUUID().toString()
        dao.insertSurvey(
            SurveyEntity(
                id = surveyId,
                name = surveyName.ifBlank { "902–928 MHz field survey" },
                bandProfileVersionId = band.versionId,
                equipmentProfileVersionId = equipmentEntity.versionId,
                status = "DRAFT",
                revision = 0,
                startedAtEpochMs = null,
                endedAtEpochMs = null,
                lastWallTimeEpochMs = nowEpochMs,
                lastMonotonicNs = nowMonotonicNs,
                distanceMeters = 0.0,
                locationCoverageRatio = 0.0,
                droppedFrameCount = 0,
                overrunCount = 0,
                malformedFrameCount = 0,
                staleFixCount = 0,
                unlocatedObservationCount = 0,
                appVersion = "0.1.0-m1",
                detectorVersion = "not-enabled-m1",
                notes = "Relative observations only; continuous wideband IQ is disabled.",
                failureExplanation = null,
            ),
        )
        return SurveyLaunch(
            surveyId,
            serialSuffix,
            ranges.minOf { it.startHz },
            ranges.maxOf { it.endHz },
            band.binWidthHz.toInt(),
            equipmentEntity.sampleRateHz,
        )
    }

    suspend fun summary(surveyId: String): SurveySummary = SurveySummary(
        requireNotNull(dao.survey(surveyId)),
        dao.aggregateCount(surveyId),
        dao.locationFixCount(surveyId),
        dao.gapCount(surveyId),
    )

    private fun EquipmentProfile.toEntity(versionId: String) = EquipmentProfileEntity(
        versionId = versionId,
        profileId = id,
        name = name,
        version = version,
        radioDeviceId = radioDeviceId,
        antennaName = antennaName,
        antennaBands = antennaBands.joinToString(";") { "${it.startHz}-${it.endHz}" },
        adapterNotes = adapterNotes,
        sampleRateHz = sampleRateHz,
        basebandFilterHz = basebandFilterHz,
        lnaGainDb = lnaGainDb,
        vgaGainDb = vgaGainDb,
        rfAmpEnabled = rfAmpEnabled,
        antennaPowerEnabled = antennaPowerEnabled,
        isCalibrated = isCalibrated,
        photoReference = photoReference,
        notes = notes,
        createdAtEpochMs = createdAtEpochMs,
        retiredAtEpochMs = retiredAtEpochMs,
    )

    private fun EquipmentProfileEntity.toDomain() = EquipmentProfile(
        id = profileId,
        name = name,
        version = version,
        radioDeviceId = radioDeviceId,
        antennaName = antennaName,
        antennaBands = antennaBands.split(';').filter { it.isNotBlank() }.map { encoded ->
            val parts = encoded.split('-')
            FrequencyRange(parts[0].toLong(), parts[1].toLong())
        },
        adapterNotes = adapterNotes,
        sampleRateHz = sampleRateHz,
        basebandFilterHz = basebandFilterHz,
        lnaGainDb = lnaGainDb,
        vgaGainDb = vgaGainDb,
        rfAmpEnabled = rfAmpEnabled,
        antennaPowerEnabled = antennaPowerEnabled,
        createdAtEpochMs = createdAtEpochMs,
        retiredAtEpochMs = retiredAtEpochMs,
        notes = notes,
        photoReference = photoReference,
        isCalibrated = isCalibrated,
    )
}
