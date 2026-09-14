package dev.rfnotebook.domain

import kotlin.math.ceil

data class FrequencyRange(val startHz: Long, val endHz: Long) {
    init {
        require(startHz >= 0) { "Frequency start must not be negative" }
        require(endHz > startHz) { "Frequency end must be greater than start" }
    }

    val widthHz: Long get() = endHz - startHz

    fun overlaps(other: FrequencyRange): Boolean = startHz < other.endHz && other.startHz < endHz

    fun contains(other: FrequencyRange): Boolean = startHz <= other.startHz && endHz >= other.endHz
}

data class EquipmentProfile(
    val id: String,
    val name: String,
    val version: Int,
    val radioDeviceId: String,
    val antennaName: String,
    val antennaBands: List<FrequencyRange>,
    val adapterNotes: String,
    val sampleRateHz: Int,
    val basebandFilterHz: Int,
    val lnaGainDb: Int,
    val vgaGainDb: Int,
    val rfAmpEnabled: Boolean,
    val antennaPowerEnabled: Boolean,
    val createdAtEpochMs: Long,
    val retiredAtEpochMs: Long? = null,
    val notes: String = "",
    val photoReference: String? = null,
    val isCalibrated: Boolean = false,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(version > 0)
        require(sampleRateHz > 0)
        require(basebandFilterHz > 0)
    }

    fun edit(
        referencedBySurvey: Boolean,
        nowEpochMs: Long,
        changes: EquipmentProfileChanges,
    ): EquipmentProfile = copy(
        version = if (referencedBySurvey) version + 1 else version,
        name = changes.name ?: name,
        antennaName = changes.antennaName ?: antennaName,
        antennaBands = changes.antennaBands ?: antennaBands,
        adapterNotes = changes.adapterNotes ?: adapterNotes,
        sampleRateHz = changes.sampleRateHz ?: sampleRateHz,
        basebandFilterHz = changes.basebandFilterHz ?: basebandFilterHz,
        lnaGainDb = changes.lnaGainDb ?: lnaGainDb,
        vgaGainDb = changes.vgaGainDb ?: vgaGainDb,
        rfAmpEnabled = changes.rfAmpEnabled ?: rfAmpEnabled,
        antennaPowerEnabled = changes.antennaPowerEnabled ?: antennaPowerEnabled,
        notes = changes.notes ?: notes,
        photoReference = changes.photoReference ?: photoReference,
        createdAtEpochMs = if (referencedBySurvey) nowEpochMs else createdAtEpochMs,
    )

    fun compareWith(other: EquipmentProfile): ProfileComparability {
        val reasons = buildSet {
            if (radioDeviceId != other.radioDeviceId) add(IncomparabilityReason.RADIO)
            if (antennaName != other.antennaName || antennaBands != other.antennaBands) {
                add(IncomparabilityReason.ANTENNA)
            }
            if (adapterNotes != other.adapterNotes) add(IncomparabilityReason.ADAPTERS)
            if (sampleRateHz != other.sampleRateHz) add(IncomparabilityReason.SAMPLE_RATE)
            if (basebandFilterHz != other.basebandFilterHz) add(IncomparabilityReason.BASEBAND_FILTER)
            if (lnaGainDb != other.lnaGainDb) add(IncomparabilityReason.LNA_GAIN)
            if (vgaGainDb != other.vgaGainDb) add(IncomparabilityReason.VGA_GAIN)
            if (rfAmpEnabled != other.rfAmpEnabled) add(IncomparabilityReason.RF_AMPLIFIER)
            if (antennaPowerEnabled != other.antennaPowerEnabled) add(IncomparabilityReason.ANTENNA_POWER)
        }
        return ProfileComparability(reasons.isEmpty(), reasons)
    }

    companion object {
        fun recommendedBasebandFilterHz(sampleRateHz: Int): Int = when (sampleRateHz) {
            2_000_000 -> 1_750_000
            4_000_000 -> 3_500_000
            8_000_000 -> 7_000_000
            else -> error("No measured baseband filter for sample rate $sampleRateHz Hz")
        }

        fun conservativeDefault(id: String, radioDeviceId: String) = EquipmentProfile(
            id = id,
            name = "Conservative receive-only",
            version = 1,
            radioDeviceId = radioDeviceId,
            antennaName = "Uncalibrated antenna",
            antennaBands = emptyList(),
            adapterNotes = "",
            sampleRateHz = 4_000_000,
            basebandFilterHz = recommendedBasebandFilterHz(4_000_000),
            lnaGainDb = 16,
            vgaGainDb = 16,
            rfAmpEnabled = false,
            antennaPowerEnabled = false,
            createdAtEpochMs = 0,
            notes = "Relative observations only; this profile is not calibrated.",
            isCalibrated = false,
        )
    }
}

data class EquipmentProfileChanges(
    val name: String? = null,
    val antennaName: String? = null,
    val antennaBands: List<FrequencyRange>? = null,
    val adapterNotes: String? = null,
    val sampleRateHz: Int? = null,
    val basebandFilterHz: Int? = null,
    val lnaGainDb: Int? = null,
    val vgaGainDb: Int? = null,
    val rfAmpEnabled: Boolean? = null,
    val antennaPowerEnabled: Boolean? = null,
    val notes: String? = null,
    val photoReference: String? = null,
)

enum class IncomparabilityReason {
    RADIO,
    ANTENNA,
    ADAPTERS,
    SAMPLE_RATE,
    BASEBAND_FILTER,
    LNA_GAIN,
    VGA_GAIN,
    RF_AMPLIFIER,
    ANTENNA_POWER,
}

data class ProfileComparability(
    val isComparable: Boolean,
    val reasons: Set<IncomparabilityReason>,
)

data class RadioCapabilities(
    val minimumFrequencyHz: Long,
    val maximumFrequencyHz: Long,
    val supportedSampleRatesHz: Set<Int>,
)

data class BandProfile(
    val id: String,
    val name: String,
    val version: Int,
    val ranges: List<FrequencyRange>,
    val excludedRanges: List<FrequencyRange>,
    val binWidthHz: Long,
    val targetRevisitMs: Long,
    val thresholdSnrDb: Float,
    val minimumBandwidthHz: Long,
    val equipmentProfileId: String,
    val explorationAidDisclaimer: String = EXPLORATION_DISCLAIMER,
) {
    fun validate(capabilities: RadioCapabilities, equipment: EquipmentProfile): List<BandValidationError> {
        val errors = mutableListOf<BandValidationError>()
        if (ranges.isEmpty()) errors += BandValidationError(BandValidationCode.EMPTY_RANGES, "At least one range is required")
        if (ranges.sortedBy { it.startHz }.zipWithNext().any { (a, b) -> a.overlaps(b) }) {
            errors += BandValidationError(BandValidationCode.OVERLAPPING_RANGES, "Survey ranges must not overlap")
        }
        if (ranges.any { it.startHz < capabilities.minimumFrequencyHz || it.endHz > capabilities.maximumFrequencyHz }) {
            errors += BandValidationError(BandValidationCode.OUT_OF_DEVICE_RANGE, "A range is outside the radio limits")
        }
        if (excludedRanges.sortedBy { it.startHz }.zipWithNext().any { (a, b) -> a.overlaps(b) }) {
            errors += BandValidationError(BandValidationCode.OVERLAPPING_EXCLUSIONS, "Excluded ranges must not overlap")
        }
        if (excludedRanges.any { excluded -> ranges.none { it.contains(excluded) } }) {
            errors += BandValidationError(BandValidationCode.EXCLUSION_OUTSIDE_RANGE, "Every exclusion must be inside one survey range")
        }
        if (binWidthHz <= 0) errors += BandValidationError(BandValidationCode.INVALID_BIN_WIDTH, "Bin width must be positive")
        if (binWidthHz > 0 && (equipment.sampleRateHz % binWidthHz != 0L ||
                equipment.sampleRateHz / binWidthHz !in 4L..1_024L ||
                equipment.sampleRateHz / binWidthHz % 4 != 0L)
        ) {
            errors += BandValidationError(
                BandValidationCode.INVALID_RESOLUTION,
                "Bin width must divide the sample rate into 4–1024 bins, in a multiple of four",
            )
        }
        if (targetRevisitMs <= 0) errors += BandValidationError(BandValidationCode.INVALID_REVISIT, "Revisit target must be positive")
        if (!thresholdSnrDb.isFinite() || thresholdSnrDb <= 0f) {
            errors += BandValidationError(BandValidationCode.INVALID_THRESHOLD, "Threshold must be finite and positive")
        }
        if (minimumBandwidthHz <= 0 || (binWidthHz > 0 && minimumBandwidthHz < binWidthHz)) {
            errors += BandValidationError(BandValidationCode.INVALID_MINIMUM_BANDWIDTH, "Minimum bandwidth must be at least one bin")
        }
        if (equipment.sampleRateHz !in capabilities.supportedSampleRatesHz) {
            errors += BandValidationError(
                BandValidationCode.UNSUPPORTED_SAMPLE_RATE,
                "${equipment.sampleRateHz} Hz is not supported; choose ${capabilities.supportedSampleRatesHz.sorted().joinToString()} Hz",
            )
        }
        return errors
    }

    fun edit(referencedBySurvey: Boolean, changes: BandProfileChanges): BandProfile = copy(
        version = if (referencedBySurvey) version + 1 else version,
        name = changes.name ?: name,
        ranges = changes.ranges ?: ranges,
        excludedRanges = changes.excludedRanges ?: excludedRanges,
        binWidthHz = changes.binWidthHz ?: binWidthHz,
        targetRevisitMs = changes.targetRevisitMs ?: targetRevisitMs,
        thresholdSnrDb = changes.thresholdSnrDb ?: thresholdSnrDb,
        minimumBandwidthHz = changes.minimumBandwidthHz ?: minimumBandwidthHz,
    )

    fun estimateCycleDuration(measuredBinsPerSecond: Double): CycleEstimate {
        require(measuredBinsPerSecond > 0.0 && measuredBinsPerSecond.isFinite())
        require(binWidthHz > 0)
        val includedWidth = ranges.sumOf { it.widthHz } - excludedRanges.sumOf { it.widthHz }
        val bins = ceil(includedWidth.toDouble() / binWidthHz).toLong()
        return CycleEstimate(bins, ceil(bins / measuredBinsPerSecond * 1_000.0).toLong())
    }

    companion object {
        const val EXPLORATION_DISCLAIMER = "Receive-only exploration aid; not authorization to transmit."
    }
}

data class BandProfileChanges(
    val name: String? = null,
    val ranges: List<FrequencyRange>? = null,
    val excludedRanges: List<FrequencyRange>? = null,
    val binWidthHz: Long? = null,
    val targetRevisitMs: Long? = null,
    val thresholdSnrDb: Float? = null,
    val minimumBandwidthHz: Long? = null,
)

enum class BandValidationCode {
    EMPTY_RANGES,
    OVERLAPPING_RANGES,
    OUT_OF_DEVICE_RANGE,
    OVERLAPPING_EXCLUSIONS,
    EXCLUSION_OUTSIDE_RANGE,
    INVALID_BIN_WIDTH,
    INVALID_RESOLUTION,
    INVALID_REVISIT,
    INVALID_THRESHOLD,
    INVALID_MINIMUM_BANDWIDTH,
    UNSUPPORTED_SAMPLE_RATE,
}

data class BandValidationError(val code: BandValidationCode, val explanation: String)
data class CycleEstimate(val includedBinCount: Long, val estimatedDurationMs: Long)

object StarterBandProfiles {
    private data class Starter(val name: String, val startHz: Long, val endHz: Long)

    fun create(equipmentProfileId: String): List<BandProfile> = listOf(
        Starter("315 MHz", 314_000_000, 316_000_000),
        Starter("433 MHz", 433_000_000, 435_000_000),
        Starter("150–174 MHz", 150_000_000, 174_000_000),
        Starter("450–470 MHz", 450_000_000, 470_000_000),
        Starter("902–928 MHz", 902_000_000, 928_000_000),
    ).mapIndexed { index, starter ->
        BandProfile(
            id = "starter-${index + 1}",
            name = starter.name,
            version = 1,
            ranges = listOf(FrequencyRange(starter.startHz, starter.endHz)),
            excludedRanges = emptyList(),
            binWidthHz = 100_000,
            targetRevisitMs = 1_000,
            thresholdSnrDb = 8f,
            minimumBandwidthHz = 100_000,
            equipmentProfileId = equipmentProfileId,
        )
    }
}
