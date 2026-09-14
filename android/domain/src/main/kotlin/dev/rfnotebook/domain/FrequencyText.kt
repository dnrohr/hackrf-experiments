package dev.rfnotebook.domain

import java.math.BigDecimal
import java.math.RoundingMode

object FrequencyText {
    private val pattern = Regex("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(hz|khz|mhz|ghz)?\\s*$", RegexOption.IGNORE_CASE)

    fun parseHz(value: String): Long {
        val match = pattern.matchEntire(value) ?: throw IllegalArgumentException("Enter a frequency such as 915 MHz")
        val multiplier = when (match.groupValues[2].lowercase()) {
            "", "hz" -> BigDecimal.ONE
            "khz" -> BigDecimal(1_000)
            "mhz" -> BigDecimal(1_000_000)
            "ghz" -> BigDecimal(1_000_000_000)
            else -> error("Unreachable frequency unit")
        }
        return try {
            match.groupValues[1].toBigDecimal().multiply(multiplier).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("Frequency must resolve to a whole number of Hz")
        }
    }

    fun parseRanges(value: String): List<FrequencyRange> {
        if (value.isBlank()) return emptyList()
        return value.split(',', ';').map { segment ->
            val endpoints = segment.trim().split(Regex("\\s*[-–—]\\s*"), limit = 2)
            require(endpoints.size == 2) { "Enter ranges as start-end, separated by commas" }
            FrequencyRange(parseHz(endpoints[0]), parseHz(endpoints[1]))
        }
    }
}
