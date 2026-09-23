package dev.rfnotebook.storage

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

internal data class ValidatedCaptureSidecar(
    val id: String,
    val expectedByteCount: Long,
    val actualByteCount: Long,
    val complexSampleCount: Long,
    val sha256: String,
)

/** Strict, bounded schema-1 validation for untrusted capture metadata. */
internal object CaptureSidecarValidator {
    private const val MAX_TEXT = 100_000
    private val topFields = setOf(
        "schemaVersion", "id", "fingerprintId", "surveyId", "sampleFormat", "componentOrder", "byteOrder",
        "centerFrequencyHz", "sampleRateHz", "requestedDurationMs", "startedAtEpochMs", "startedMonotonicNs",
        "expectedByteCount", "actualByteCount", "complexSampleCount", "gapCount", "overrunCount", "sha256",
        "radio", "equipmentProfileVersionId", "deviceSerialSuffix", "location", "note", "versions",
    )
    private val requiredTopFields = topFields - setOf("fingerprintId", "surveyId", "deviceSerialSuffix")
    private val radioFields = setOf("basebandFilterHz", "lnaGainDb", "vgaGainDb", "rfAmpEnabled", "antennaPowerEnabled")
    private val locationFields = setOf("crs", "latitude", "longitude", "horizontalAccuracyM", "ageMs")
    private val versionFields = setOf("app", "detector", "clustering", "aggregation", "profile")

    fun validate(text: String): ValidatedCaptureSidecar {
        require(text.length <= MAX_TEXT) { "Capture sidecar is too large" }
        var schema: String? = null
        var id: String? = null
        var sampleFormat: String? = null
        var componentOrder: String? = null
        var byteOrder: String? = null
        var centerHz: Long? = null
        var sampleRate: Long? = null
        var durationMs: Long? = null
        var startedEpoch: Long? = null
        var startedMonotonic: Long? = null
        var expectedBytes: Long? = null
        var actualBytes: Long? = null
        var complexSamples: Long? = null
        var gaps: Long? = null
        var overruns: Long? = null
        var hash: String? = null
        var equipment: String? = null
        val seen = linkedSetOf<String>()

        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            token(reader, JsonToken.BEGIN_OBJECT, "Capture sidecar must be a JSON object")
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                require(name in topFields) { "Unknown capture sidecar field: $name" }
                require(seen.add(name)) { "Duplicate capture sidecar field: $name" }
                when (name) {
                    "schemaVersion" -> schema = string(reader, name)
                    "id" -> id = string(reader, name)
                    "fingerprintId", "surveyId", "deviceSerialSuffix" -> nullableString(reader, name)
                    "sampleFormat" -> sampleFormat = string(reader, name)
                    "componentOrder" -> componentOrder = string(reader, name)
                    "byteOrder" -> byteOrder = string(reader, name)
                    "centerFrequencyHz" -> centerHz = integer(reader, name)
                    "sampleRateHz" -> sampleRate = integer(reader, name)
                    "requestedDurationMs" -> durationMs = integer(reader, name)
                    "startedAtEpochMs" -> startedEpoch = integer(reader, name)
                    "startedMonotonicNs" -> startedMonotonic = integer(reader, name)
                    "expectedByteCount" -> expectedBytes = integer(reader, name)
                    "actualByteCount" -> actualBytes = integer(reader, name)
                    "complexSampleCount" -> complexSamples = integer(reader, name)
                    "gapCount" -> gaps = integer(reader, name)
                    "overrunCount" -> overruns = integer(reader, name)
                    "sha256" -> hash = string(reader, name)
                    "radio" -> radio(reader)
                    "equipmentProfileVersionId" -> equipment = string(reader, name)
                    "location" -> location(reader)
                    "note" -> string(reader, name)
                    "versions" -> versions(reader)
                }
            }
            reader.endObject()
            token(reader, JsonToken.END_DOCUMENT, "Trailing content after capture sidecar")
        }
        require(seen.containsAll(requiredTopFields)) { "Capture sidecar is missing required fields" }
        require(schema == "1.0.0") { "Unsupported capture sidecar schema" }
        require(!id.isNullOrBlank()) { "Capture identifier is empty" }
        require(sampleFormat == "signed-int8-interleaved-iq") { "Unsupported capture sample format" }
        require(componentOrder == "I,Q") { "Unsupported capture component order" }
        require(byteOrder == "not-applicable-single-byte-components") { "Unsupported capture byte order" }
        require(centerHz in 1_000_000L..6_000_000_000L) { "Capture center frequency is out of range" }
        require(sampleRate in setOf(2_000_000L, 4_000_000L, 8_000_000L)) { "Capture sample rate is unsupported" }
        require(durationMs in 250L..30_000L) { "Capture duration is out of range" }
        require(startedEpoch != null && startedEpoch >= 0 && startedMonotonic != null && startedMonotonic >= 0) {
            "Capture timestamps are invalid"
        }
        require(expectedBytes != null && expectedBytes > 0 && actualBytes == expectedBytes) { "Capture byte counts are incomplete" }
        require(complexSamples != null && complexSamples > 0 && complexSamples * 2L == actualBytes) { "Capture sample count is inconsistent" }
        require(gaps == 0L && overruns == 0L) { "Completed capture reports loss" }
        require(hash != null && Regex("[0-9a-f]{64}").matches(hash)) { "Capture SHA-256 is invalid" }
        require(!equipment.isNullOrBlank()) { "Capture equipment version is empty" }
        return ValidatedCaptureSidecar(id, expectedBytes, actualBytes, complexSamples, hash)
    }

    private fun radio(reader: JsonReader) {
        val fields = objectFields(reader, "radio") { name ->
            when (name) {
                "basebandFilterHz" -> require(integer(reader, name) >= 0) { "Invalid baseband filter" }
                "lnaGainDb" -> require(integer(reader, name) in 0L..40L) { "Invalid LNA gain" }
                "vgaGainDb" -> require(integer(reader, name) in 0L..62L) { "Invalid VGA gain" }
                "rfAmpEnabled", "antennaPowerEnabled" -> boolean(reader, name)
            }
        }
        require(fields == radioFields) { "Capture radio metadata is incomplete" }
    }

    private fun location(reader: JsonReader) {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return
        }
        val fields = objectFields(reader, "location") { name ->
            when (name) {
                "crs" -> require(string(reader, name) == "EPSG:4326") { "Unsupported capture CRS" }
                "latitude" -> require(number(reader, name) in -90.0..90.0) { "Invalid capture latitude" }
                "longitude" -> require(number(reader, name) in -180.0..180.0) { "Invalid capture longitude" }
                "horizontalAccuracyM" -> nullableNumber(reader, name)?.let { require(it >= 0.0) { "Invalid capture accuracy" } }
                "ageMs" -> nullableInteger(reader, name)?.let { require(it >= 0) { "Invalid capture location age" } }
            }
        }
        require(fields == locationFields) { "Capture location metadata is incomplete" }
    }

    private fun versions(reader: JsonReader) {
        val fields = objectFields(reader, "versions") { name -> require(string(reader, name).isNotBlank()) { "$name version is empty" } }
        require(fields == versionFields) { "Capture version metadata is incomplete" }
    }

    private inline fun objectFields(reader: JsonReader, label: String, read: (String) -> Unit): Set<String> {
        token(reader, JsonToken.BEGIN_OBJECT, "$label must be an object")
        val allowed = when (label) { "radio" -> radioFields; "location" -> locationFields; else -> versionFields }
        val fields = linkedSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            require(name in allowed) { "Unknown $label field: $name" }
            require(fields.add(name)) { "Duplicate $label field: $name" }
            read(name)
        }
        reader.endObject()
        return fields
    }

    private fun string(reader: JsonReader, field: String): String {
        token(reader, JsonToken.STRING, "$field must be a string")
        return reader.nextString().also { require(it.length <= MAX_TEXT) { "$field is too long" } }
    }

    private fun nullableString(reader: JsonReader, field: String): String? = when (reader.peek()) {
        JsonToken.NULL -> { reader.nextNull(); null }
        JsonToken.STRING -> string(reader, field)
        else -> throw IllegalArgumentException("$field must be a string or null")
    }

    private fun integer(reader: JsonReader, field: String): Long {
        token(reader, JsonToken.NUMBER, "$field must be an integer")
        val encoded = reader.nextString()
        require(Regex("-?(?:0|[1-9][0-9]*)").matches(encoded)) { "$field must be an integer" }
        return encoded.toLongOrNull() ?: throw IllegalArgumentException("$field is outside the supported range")
    }

    private fun nullableInteger(reader: JsonReader, field: String): Long? = when (reader.peek()) {
        JsonToken.NULL -> { reader.nextNull(); null }
        JsonToken.NUMBER -> integer(reader, field)
        else -> throw IllegalArgumentException("$field must be an integer or null")
    }

    private fun number(reader: JsonReader, field: String): Double {
        token(reader, JsonToken.NUMBER, "$field must be a number")
        return reader.nextDouble().also { require(it.isFinite()) { "$field must be finite" } }
    }

    private fun nullableNumber(reader: JsonReader, field: String): Double? = when (reader.peek()) {
        JsonToken.NULL -> { reader.nextNull(); null }
        JsonToken.NUMBER -> number(reader, field)
        else -> throw IllegalArgumentException("$field must be a number or null")
    }

    private fun boolean(reader: JsonReader, field: String): Boolean {
        token(reader, JsonToken.BOOLEAN, "$field must be a boolean")
        return reader.nextBoolean()
    }

    private fun token(reader: JsonReader, expected: JsonToken, message: String) {
        require(reader.peek() == expected) { message }
    }
}
