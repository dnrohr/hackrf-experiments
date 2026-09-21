package dev.rfnotebook.signal.processing

import dev.rfnotebook.domain.Detection
import dev.rfnotebook.domain.DetectionKind
import dev.rfnotebook.domain.QualityFlag
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.abs

data class DetectorConfig(
    val version: String = "detector-v1",
    val thresholdSnrDb: Float = 8f,
    val minimumBandwidthHz: Long = 100_000,
    val binWidthHz: Long = 100_000,
    val closeGapMs: Long = 1_500,
    val persistentAfterMs: Long = 15_000,
    val broadbandFraction: Float = 0.7f,
    val overloadRiseDb: Float = 12f,
) {
    init {
        require(thresholdSnrDb > 0f && thresholdSnrDb.isFinite())
        require(minimumBandwidthHz > 0 && binWidthHz > 0 && closeGapMs >= 0 && persistentAfterMs > 0)
        require(broadbandFraction in 0f..1f)
    }
}

data class AggregateBin(
    val frequencyBinHz: Long,
    val medianPowerDbfs: Float,
    val maximumPowerDbfs: Float,
    val sampleCount: Int,
)

data class AggregateFrame(
    val surveyId: String,
    val equipmentProfileVersionId: String,
    val timeBucketStartEpochMs: Long,
    val bins: List<AggregateBin>,
    val locationFixId: String? = null,
    val hardwareCenterHz: Long? = null,
    val corruptOrIncomplete: Boolean = false,
)

class DetectionPipeline(
    private val config: DetectorConfig,
    private val estimator: RollingNoiseEstimator = RollingNoiseEstimator(),
) {
    private data class Candidate(
        val frame: AggregateFrame,
        val bins: List<AggregateBin>,
        val baseline: Float,
        val flags: Set<QualityFlag>,
    )
    private data class Active(val candidates: MutableList<Candidate>, var lastTime: Long)

    fun process(input: List<AggregateFrame>): List<Detection> {
        val frames = input.sortedWith(
            compareBy<AggregateFrame> { it.timeBucketStartEpochMs }
                .thenBy { it.surveyId }
                .thenBy { it.equipmentProfileVersionId }
                .thenBy { frame -> frame.bins.joinToString(";") { "${it.frequencyBinHz}:${it.medianPowerDbfs}:${it.maximumPowerDbfs}" } },
        )
        val active = mutableListOf<Active>()
        val complete = mutableListOf<Detection>()
        frames.forEach { frame ->
            val candidates = candidates(frame)
            val currentTime = frame.timeBucketStartEpochMs
            val expired = active.filter { currentTime - it.lastTime > config.closeGapMs }
            expired.forEach { complete += close(it) }
            active.removeAll(expired.toSet())

            candidates.forEach { candidate ->
                val match = active.filter { overlaps(it.candidates.last(), candidate) }.minByOrNull { it.lastTime }
                if (match == null) active += Active(mutableListOf(candidate), currentTime)
                else {
                    match.candidates += candidate
                    match.lastTime = currentTime
                }
            }
        }
        complete += active.map(::close)
        return complete.sortedWith(compareBy<Detection> { it.startedAtEpochMs }.thenBy { it.centerFrequencyHz }.thenBy { it.id })
    }

    private fun candidates(frame: AggregateFrame): List<Candidate> {
        if (frame.bins.isEmpty()) return emptyList()
        val sorted = frame.bins.sortedBy { it.frequencyBinHz }
        // A span-wide level above -50 dBFS is treated as possible overload,
        // not as a trustworthy startup floor.
        val spanReference = median(sorted.map { it.medianPowerDbfs }).takeIf { it < -50f }
        val above = sorted.map { bin ->
            val baseline = estimator.observe(
                frame.equipmentProfileVersionId,
                bin.frequencyBinHz,
                bin.medianPowerDbfs,
                spanReference,
            ).powerDbfs
            Triple(bin, baseline, bin.maximumPowerDbfs - baseline >= config.thresholdSnrDb)
        }
        val groups = mutableListOf<List<Pair<AggregateBin, Float>>>()
        var group = mutableListOf<Pair<AggregateBin, Float>>()
        above.forEach { (bin, baseline, detected) ->
            if (detected && (group.isEmpty() || bin.frequencyBinHz - group.last().first.frequencyBinHz <= config.binWidthHz)) {
                group += bin to baseline
            } else {
                if (group.isNotEmpty()) groups += group.toList()
                group = if (detected) mutableListOf(bin to baseline) else mutableListOf()
            }
        }
        if (group.isNotEmpty()) groups += group
        val broadband = above.count { it.third }.toFloat() / sorted.size >= config.broadbandFraction
        val baselines = above.map { it.second }
        val wideRise = median(sorted.map { it.medianPowerDbfs }) - median(baselines) >= config.overloadRiseDb
        return groups.mapNotNull { pairs ->
            val bins = pairs.map { it.first }
            val width = (bins.last().frequencyBinHz - bins.first().frequencyBinHz) + config.binWidthHz
            if (width < config.minimumBandwidthHz) return@mapNotNull null
            val flags = buildSet {
                frame.hardwareCenterHz?.let { center ->
                    if (bins.any { abs(it.frequencyBinHz - center) <= config.binWidthHz / 2 }) add(QualityFlag.CENTER_DC)
                }
                if (broadband) add(QualityFlag.BROADBAND_IMPULSE)
                if (wideRise) add(QualityFlag.OVERLOAD)
                if (frame.corruptOrIncomplete) add(QualityFlag.CORRUPT_FRAME)
            }
            Candidate(frame, bins, pairs.map { it.second }.average().toFloat(), flags)
        }.let(::flagSymmetry)
    }

    private fun flagSymmetry(candidates: List<Candidate>): List<Candidate> = candidates.map { candidate ->
        val center = candidate.frame.hardwareCenterHz ?: return@map candidate
        val candidateCenter = (candidate.bins.first().frequencyBinHz + candidate.bins.last().frequencyBinHz) / 2
        val mirrored = candidates.any { other ->
            other !== candidate && abs(((other.bins.first().frequencyBinHz + other.bins.last().frequencyBinHz) / 2) - center) ==
                abs(candidateCenter - center)
        }
        if (mirrored) candidate.copy(flags = candidate.flags + QualityFlag.SYMMETRIC_CANDIDATE) else candidate
    }

    private fun overlaps(left: Candidate, right: Candidate): Boolean =
        left.frame.surveyId == right.frame.surveyId &&
            left.frame.equipmentProfileVersionId == right.frame.equipmentProfileVersionId &&
            left.bins.first().frequencyBinHz <= right.bins.last().frequencyBinHz + config.binWidthHz &&
            right.bins.first().frequencyBinHz <= left.bins.last().frequencyBinHz + config.binWidthHz

    private fun close(active: Active): Detection {
        val candidates = active.candidates
        val first = candidates.first()
        val started = first.frame.timeBucketStartEpochMs
        val frameDuration = if (candidates.size > 1) {
            candidates.zipWithNext().map { (a, b) -> b.frame.timeBucketStartEpochMs - a.frame.timeBucketStartEpochMs }
                .filter { it > 0 }.sorted().let { if (it.isEmpty()) 1_000L else it[it.size / 2] }
        } else 1_000L
        val ended = candidates.last().frame.timeBucketStartEpochMs + frameDuration
        val allBins = candidates.flatMap { it.bins }
        val startFrequency = allBins.minOf { it.frequencyBinHz }
        val endFrequency = allBins.maxOf { it.frequencyBinHz } + config.binWidthHz
        val peak = allBins.maxOf { it.maximumPowerDbfs }
        val medianPower = median(allBins.map { it.medianPowerDbfs })
        val baseline = candidates.map { it.baseline }.average().toFloat()
        val stableKey = listOf(first.frame.surveyId, started, endFrequency, startFrequency, config.version).joinToString(":")
        return Detection(
            id = UUID.nameUUIDFromBytes(stableKey.toByteArray(StandardCharsets.UTF_8)).toString(),
            surveyId = first.frame.surveyId,
            equipmentProfileVersionId = first.frame.equipmentProfileVersionId,
            startedAtEpochMs = started,
            endedAtEpochMs = ended,
            centerFrequencyHz = (startFrequency + endFrequency) / 2,
            bandwidthHz = endFrequency - startFrequency,
            peakPowerDbfs = peak,
            medianPowerDbfs = medianPower,
            snrDb = peak - baseline,
            detectorVersion = config.version,
            kind = if (ended - started >= config.persistentAfterMs) DetectionKind.PERSISTENT_CARRIER else DetectionKind.DISCRETE_BURST,
            locationFixId = candidates.mapNotNull { it.frame.locationFixId }.firstOrNull(),
            qualityFlags = candidates.flatMap { it.flags }.toSet(),
        )
    }
}

private fun median(values: List<Float>): Float {
    if (values.isEmpty()) return Float.NaN
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2f
}
