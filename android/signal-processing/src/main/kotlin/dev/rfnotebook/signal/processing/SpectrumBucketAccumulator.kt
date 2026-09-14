package dev.rfnotebook.signal.processing

data class SpectrumSummary(
    val timeBucketStartEpochMs: Long,
    val representativeMonotonicNs: Long,
    val frequencyBinHz: Long,
    val minimumPowerDbfs: Float,
    val medianPowerDbfs: Float,
    val maximumPowerDbfs: Float,
    val noiseEstimateDbfs: Float,
    val sampleCount: Int,
)

class SpectrumBucketAccumulator(private val bucketDurationMs: Long) {
    private data class Key(val bucket: Long, val frequency: Long)
    private data class Values(val powers: MutableList<Float>, var monotonicNs: Long)
    private val values = linkedMapOf<Key, Values>()

    init { require(bucketDurationMs > 0) }

    @Synchronized
    fun add(wallTimeEpochMs: Long, monotonicNs: Long, frame: ParsedSweepFrame): List<SpectrumSummary> {
        val bucket = wallTimeEpochMs / bucketDurationMs * bucketDurationMs
        val completed = flushBeforeLocked(bucket)
        frame.bins.forEach { bin ->
            val stored = values.getOrPut(Key(bucket, bin.frequencyHz)) { Values(mutableListOf(), monotonicNs) }
            stored.powers += bin.powerDbfs
            stored.monotonicNs = monotonicNs
        }
        return completed
    }

    @Synchronized
    fun flushBefore(wallTimeEpochMs: Long): List<SpectrumSummary> =
        flushBeforeLocked(wallTimeEpochMs / bucketDurationMs * bucketDurationMs)

    @Synchronized
    fun flushAll(): List<SpectrumSummary> = flushKeys(values.keys.toList())

    private fun flushBeforeLocked(bucket: Long): List<SpectrumSummary> = flushKeys(values.keys.filter { it.bucket < bucket })

    private fun flushKeys(keys: List<Key>): List<SpectrumSummary> = keys.map { key ->
        val stored = requireNotNull(values.remove(key))
        val sorted = stored.powers.sorted()
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2f
        SpectrumSummary(
            key.bucket,
            stored.monotonicNs,
            key.frequency,
            sorted.first(),
            median,
            sorted.last(),
            median,
            sorted.size,
        )
    }
}
