package dev.rfnotebook.storage

import dev.rfnotebook.domain.EquipmentProfile
import dev.rfnotebook.domain.FrequencyRange
import dev.rfnotebook.domain.RadioCapabilities
import dev.rfnotebook.domain.StarterBandProfiles
import dev.rfnotebook.domain.SurveyStatus
import java.util.UUID

data class SurveyLaunch(
    val surveyId: String,
    val serialSuffix: String,
    val startFrequencyHz: Long,
    val endFrequencyHz: Long,
    val binWidthHz: Int,
    val sampleRateHz: Int,
    val basebandFilterHz: Int,
    val lnaGainDb: Int,
    val vgaGainDb: Int,
    val rfAmpEnabled: Boolean,
    val antennaPowerEnabled: Boolean,
    val scanRanges: List<FrequencyRange>,
)

data class SurveySummary(
    val survey: SurveyEntity,
    val aggregateCount: Long,
    val locationFixCount: Long,
    val gapCount: Long,
    val gaps: List<AcquisitionGapEntity>,
    val latestHealth: HealthSnapshotEntity?,
)

class NotebookSetupRepository(private val dao: NotebookDao) {
    suspend fun recoverableLaunch(): SurveyLaunch? {
        val resumableStatuses = setOf(
            SurveyStatus.VALIDATING.name,
            SurveyStatus.ACTIVE.name,
            SurveyStatus.PAUSED.name,
        )
        val survey = dao.interruptedSurveys().firstOrNull { it.status in resumableStatuses } ?: return null
        val band = requireNotNull(dao.bandProfile(survey.bandProfileVersionId))
        val equipment = requireNotNull(dao.equipmentProfile(survey.equipmentProfileVersionId))
        val radio = requireNotNull(dao.radioDevice(equipment.radioDeviceId))
        val ranges = dao.bandRanges(band.versionId).filter { it.kind == "INCLUDE" }
        val exclusions = dao.bandRanges(band.versionId).filter { it.kind == "EXCLUDE" }
        val scanRanges = effectiveRanges(ranges, exclusions)
        return SurveyLaunch(
            survey.id,
            radio.serialSuffix,
            ranges.minOf { it.startHz },
            ranges.maxOf { it.endHz },
            band.binWidthHz.toInt(),
            equipment.sampleRateHz,
            equipment.basebandFilterHz,
            equipment.lnaGainDb,
            equipment.vgaGainDb,
            equipment.rfAmpEnabled,
            equipment.antennaPowerEnabled,
            scanRanges,
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
        antennaName: String = "Uncalibrated antenna",
        adapterNotes: String = "",
        equipmentNotes: String = "Relative observations only; this profile is not calibrated.",
        photoReference: String? = null,
        bandStartHz: Long? = null,
        bandEndHz: Long? = null,
        binWidthHz: Long = 100_000,
        targetRevisitMs: Long = 1_000,
        thresholdSnrDb: Float = 8f,
        minimumBandwidthHz: Long = 100_000,
        excludedRanges: List<FrequencyRange> = emptyList(),
        includedRanges: List<FrequencyRange>? = null,
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
        val requestedRanges = includedRanges ?: if (bandStartHz != null && bandEndHz != null) {
            listOf(FrequencyRange(bandStartHz, bandEndHz))
        } else null
        val requestedEquipment = EquipmentProfile.conservativeDefault(profileId, radioId).copy(
            createdAtEpochMs = nowEpochMs,
            sampleRateHz = sampleRateHz,
            basebandFilterHz = EquipmentProfile.recommendedBasebandFilterHz(sampleRateHz),
            lnaGainDb = lnaGainDb,
            vgaGainDb = vgaGainDb,
            antennaName = antennaName.ifBlank { "Uncalibrated antenna" },
            adapterNotes = adapterNotes,
            notes = equipmentNotes,
            photoReference = photoReference?.takeIf { it.isNotBlank() },
            antennaBands = requestedRanges.orEmpty(),
        )
        val priorEquipment = dao.activeEquipmentProfiles().filter { it.profileId == profileId }.maxByOrNull { it.version }
        val sameEquipment = priorEquipment?.toDomain()?.let { prior ->
            prior.copy(version = 1, createdAtEpochMs = nowEpochMs, retiredAtEpochMs = null) == requestedEquipment
        } == true
        val equipmentEntity = if (priorEquipment == null) {
            requestedEquipment.toEntity("$profileId:v1")
        } else if (sameEquipment) {
            priorEquipment
        } else {
            dao.retireEquipmentProfile(priorEquipment.versionId, nowEpochMs)
            requestedEquipment.copy(version = priorEquipment.version + 1).toEntity("$profileId:v${priorEquipment.version + 1}")
        }
        dao.insertEquipmentProfile(equipmentEntity)
        StarterBandProfiles.create(profileId).map { profile ->
            if (profile.name == bandName && requestedRanges != null) profile.copy(
                ranges = requestedRanges,
                binWidthHz = binWidthHz,
                targetRevisitMs = targetRevisitMs,
                thresholdSnrDb = thresholdSnrDb,
                minimumBandwidthHz = minimumBandwidthHz,
                excludedRanges = excludedRanges,
            ) else profile
        }.forEach { profile ->
            if (profile.name == bandName) {
                val errors = profile.validate(
                    RadioCapabilities(1_000_000, 6_000_000_000, setOf(2_000_000, 4_000_000, 8_000_000)),
                    requestedEquipment,
                )
                require(errors.isEmpty()) { errors.joinToString("; ") { it.explanation } }
            }
            val bandProfileId = "${profile.id}:$serialSuffix"
            val priorBand = dao.activeBandProfiles().filter { it.profileId == bandProfileId }.maxByOrNull { it.version }
            val desiredRanges = profile.ranges.mapIndexed { index, range ->
                BandRangeEntity("", "INCLUDE", index, range.startHz, range.endHz)
            } + profile.excludedRanges.mapIndexed { index, range ->
                BandRangeEntity("", "EXCLUDE", index, range.startHz, range.endHz)
            }
            val priorRanges = priorBand?.let { dao.bandRanges(it.versionId) }.orEmpty()
            val reuse = priorBand?.takeIf {
                it.equipmentProfileVersionId == equipmentEntity.versionId &&
                    it.binWidthHz == profile.binWidthHz && it.targetRevisitMs == profile.targetRevisitMs &&
                    it.thresholdSnrDb == profile.thresholdSnrDb && it.minimumBandwidthHz == profile.minimumBandwidthHz &&
                    priorRanges.map { range -> Triple(range.kind, range.startHz, range.endHz) } ==
                    desiredRanges.map { range -> Triple(range.kind, range.startHz, range.endHz) }
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
            } + profile.excludedRanges.mapIndexed { index, range ->
                BandRangeEntity(bandEntity.versionId, "EXCLUDE", index, range.startHz, range.endHz)
            })
        }
        val band = dao.activeBandProfiles().first { it.name == bandName }
        val ranges = dao.bandRanges(band.versionId).filter { it.kind == "INCLUDE" }
        val exclusions = dao.bandRanges(band.versionId).filter { it.kind == "EXCLUDE" }
        val scanRanges = effectiveRanges(ranges, exclusions)
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
            equipmentEntity.basebandFilterHz,
            equipmentEntity.lnaGainDb,
            equipmentEntity.vgaGainDb,
            equipmentEntity.rfAmpEnabled,
            equipmentEntity.antennaPowerEnabled,
            scanRanges,
        )
    }

    suspend fun summary(surveyId: String): SurveySummary = SurveySummary(
        requireNotNull(dao.survey(surveyId)),
        dao.aggregateCount(surveyId),
        dao.locationFixCount(surveyId),
        dao.gapCount(surveyId),
        dao.surveyGaps(surveyId),
        dao.latestHealth(surveyId),
    )

    private fun effectiveRanges(
        included: List<BandRangeEntity>,
        excluded: List<BandRangeEntity>,
    ): List<FrequencyRange> {
        val exclusions = excluded.map { FrequencyRange(it.startHz, it.endHz) }
        return included
            .map { FrequencyRange(it.startHz, it.endHz) }
            .flatMap { range -> range.excluding(exclusions.filter(range::contains)) }
            .sortedBy { it.startHz }
            .also { require(it.isNotEmpty() && it.size <= 10) { "Band must produce 1–10 hardware sweep ranges" } }
    }

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
