package dev.rfnotebook.signal.processing

import kotlin.math.ceil

data class NoiseEstimate(
    val powerDbfs: Float,
    val sampleSupport: Int,
    val ageFrames: Int,
    val confidence: Float,
)

class RollingNoiseEstimator(
    private val windowSize: Int = 32,
    private val startupNoiseDbfs: Float = -90f,
    private val occupiedMarginDb: Float = 6f,
) {
    init { require(windowSize >= 4); require(startupNoiseDbfs.isFinite()) }

    private data class State(
        val values: ArrayDeque<Float> = ArrayDeque(),
        var age: Int = 0,
        var coherentRiseFrames: Int = 0,
    )
    private val states = mutableMapOf<Pair<String, Long>, State>()

    fun observe(
        equipmentProfileVersionId: String,
        frequencyBinHz: Long,
        powerDbfs: Float,
        spanReferenceFloorDbfs: Float? = null,
    ): NoiseEstimate {
        require(powerDbfs.isFinite())
        val state = states.getOrPut(equipmentProfileVersionId to frequencyBinHz) {
            State(ArrayDeque<Float>().apply { add(startupNoiseDbfs) })
        }
        state.age++
        val current = quantile(state.values)
        val reference = spanReferenceFloorDbfs?.takeIf(Float::isFinite)
        if (reference != null && reference > current + occupiedMarginDb) {
            state.coherentRiseFrames++
            if (state.coherentRiseFrames >= 3) {
                state.values.clear()
                state.values.add(reference)
                state.coherentRiseFrames = 0
            }
        } else {
            state.coherentRiseFrames = 0
        }
        if (state.age == 1 && reference != null) {
            state.values.add(reference)
        } else if (powerDbfs <= quantile(state.values) + occupiedMarginDb) {
            state.values.add(powerDbfs)
        }
        while (state.values.size > windowSize) state.values.removeFirst()
        return estimate(equipmentProfileVersionId, frequencyBinHz)
    }

    fun estimate(equipmentProfileVersionId: String, frequencyBinHz: Long): NoiseEstimate {
        val state = states[equipmentProfileVersionId to frequencyBinHz]
            ?: return NoiseEstimate(startupNoiseDbfs, 0, 0, 0f)
        val support = (state.values.size - 1).coerceAtLeast(0)
        return NoiseEstimate(
            quantile(state.values),
            support,
            state.age,
            (support.toFloat() / windowSize).coerceIn(0f, 1f),
        )
    }

    fun reset(equipmentProfileVersionId: String) {
        states.keys.removeAll { it.first == equipmentProfileVersionId }
    }

    private fun quantile(values: Collection<Float>): Float {
        val sorted = values.sorted()
        val index = ceil((sorted.size - 1) * 0.25).toInt().coerceIn(sorted.indices)
        return sorted[index]
    }
}
