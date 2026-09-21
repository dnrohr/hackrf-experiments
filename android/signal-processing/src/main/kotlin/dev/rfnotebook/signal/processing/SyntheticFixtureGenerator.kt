package dev.rfnotebook.signal.processing

object SyntheticFixtureGenerator {
    const val EQUIPMENT = "fixture-equipment:v1"
    private val frequencies = (0..9).map { 914_500_000L + it * 100_000L }

    fun noiseOnly(seconds: Int = 20): List<AggregateFrame> = (0 until seconds).map { second ->
        frame(second, emptyMap())
    }

    fun continuousCarrier(seconds: Int = 20): List<AggregateFrame> = (0 until seconds).map { second ->
        frame(second, mapOf(915_000_000L to -55f))
    }

    fun repeatingOok(): List<AggregateFrame> = (0 until 12).map { second ->
        frame(second, if (second in setOf(1, 2, 5, 6, 9, 10)) mapOf(915_000_000L to -50f) else emptyMap())
    }

    fun twoLevelFsk(): List<AggregateFrame> = (0 until 8).map { second ->
        frame(second, mapOf((if (second % 2 == 0) 914_900_000L else 915_100_000L) to -52f))
    }

    fun centerArtifact(): List<AggregateFrame> = listOf(frame(0, mapOf(915_000_000L to -40f), center = 915_000_000L))

    fun broadbandOverload(): List<AggregateFrame> = listOf(frame(0, frequencies.associateWith { -38f }))

    fun missingAndReordered(): List<AggregateFrame> = listOf(
        frame(3, mapOf(915_000_000L to -50f), corrupt = true),
        frame(0, mapOf(915_000_000L to -51f)),
        frame(1, mapOf(915_000_000L to -52f)),
    )

    private fun frame(second: Int, signal: Map<Long, Float>, center: Long? = null, corrupt: Boolean = false) = AggregateFrame(
        surveyId = "fixture-survey",
        equipmentProfileVersionId = EQUIPMENT,
        timeBucketStartEpochMs = second * 1_000L,
        bins = frequencies.map { frequency ->
            val power = signal[frequency] ?: -90f
            AggregateBin(frequency, power, power, 10)
        },
        hardwareCenterHz = center,
        corruptOrIncomplete = corrupt,
    )
}
