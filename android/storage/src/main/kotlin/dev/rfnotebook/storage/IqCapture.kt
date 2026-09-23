package dev.rfnotebook.storage

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

data class CaptureAssessment(val allowed: Boolean, val expectedBytes: Long, val explanation: String)

object CapturePreflight {
    const val MIN_DURATION_MS = 250L
    const val MAX_DURATION_MS = 30_000L
    const val DEFAULT_RESERVE_BYTES = 256L * 1024 * 1024

    fun expectedBytes(durationMs: Long, sampleRateHz: Int): Long =
        Math.multiplyExact(Math.multiplyExact(durationMs, sampleRateHz.toLong()), 2L) / 1_000L

    fun assess(
        durationMs: Long,
        sampleRateHz: Int,
        measuredSamplesPerSecond: Long,
        availableBytes: Long,
        reserveBytes: Long = DEFAULT_RESERVE_BYTES,
    ): CaptureAssessment {
        val expected = runCatching { expectedBytes(durationMs, sampleRateHz) }.getOrElse { Long.MAX_VALUE }
        val problem = when {
            durationMs !in MIN_DURATION_MS..MAX_DURATION_MS -> "Duration must be 0.25–30 seconds"
            sampleRateHz !in setOf(2_000_000, 4_000_000, 8_000_000) -> "Unsupported sample rate"
            measuredSamplesPerSecond < sampleRateHz * 95L / 100L -> "Measured USB capacity is below 95% of the requested sample rate"
            availableBytes < expected + reserveBytes -> "Capture would consume the configured storage reserve"
            else -> null
        }
        return CaptureAssessment(problem == null, expected, problem ?: "Preflight passed")
    }
}

data class CaptureMetadata(
    val id: String,
    val fingerprintId: String?,
    val surveyId: String?,
    val startedAtEpochMs: Long,
    val startedMonotonicNs: Long,
    val durationMs: Long,
    val centerFrequencyHz: Long,
    val sampleRateHz: Int,
    val basebandFilterHz: Int,
    val lnaGainDb: Int,
    val vgaGainDb: Int,
    val rfAmpEnabled: Boolean,
    val antennaPowerEnabled: Boolean,
    val equipmentProfileVersionId: String,
    val serialSuffix: String?,
    val latitude: Double?,
    val longitude: Double?,
    val horizontalAccuracyM: Float?,
    val locationAgeMs: Long?,
    val note: String,
    val appVersion: String,
) { companion object }

data class CaptureResult(
    val iqFile: File,
    val sidecarFile: File,
    val previewFile: File,
    val byteCount: Long,
    val complexSampleCount: Long,
    val sha256: String,
)

class AtomicIqCapture(
    private val directory: File,
    private val metadata: CaptureMetadata,
    private val expectedBytes: Long = CapturePreflight.expectedBytes(metadata.durationMs, metadata.sampleRateHz),
) : AutoCloseable {
    private val iqPart: File
    private val fileOutput: FileOutputStream
    private val output: BufferedOutputStream
    private val preview = PreviewAccumulator(expectedBytes)
    private var bytesWritten = 0L
    private var gaps = 0L
    private var overruns = 0L
    private var cancelled = false
    private var closed = false

    init {
        require(metadata.id.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "Capture identifier is unsafe" }
        require(metadata.note.length <= 4_096) { "Capture note is too long" }
        require(metadata.latitude == null || metadata.latitude.isFinite() && metadata.latitude in -90.0..90.0) {
            "Capture latitude is invalid"
        }
        require(metadata.longitude == null || metadata.longitude.isFinite() && metadata.longitude in -180.0..180.0) {
            "Capture longitude is invalid"
        }
        require((metadata.latitude == null) == (metadata.longitude == null)) { "Capture location must contain both coordinates" }
        require(metadata.latitude != null || metadata.horizontalAccuracyM == null && metadata.locationAgeMs == null) {
            "Capture location quality requires coordinates"
        }
        require(metadata.horizontalAccuracyM == null || metadata.horizontalAccuracyM.isFinite() && metadata.horizontalAccuracyM >= 0f) {
            "Capture location accuracy is invalid"
        }
        require(metadata.locationAgeMs == null || metadata.locationAgeMs >= 0L) { "Capture location age is invalid" }
        require(expectedBytes > 0 && expectedBytes % 2L == 0L) { "Expected IQ byte count must be a positive even number" }
        check(directory.mkdirs() || directory.isDirectory) { "Could not create capture directory" }
        iqPart = File(directory, "${metadata.id}.cs8.part")
        check(!iqPart.exists()) { "Capture already exists" }
        fileOutput = FileOutputStream(iqPart)
        output = BufferedOutputStream(fileOutput, 256 * 1024)
    }

    @Synchronized fun append(samples: ByteArray): Int {
        check(!closed && !cancelled) { "Capture is not writable" }
        val remaining = expectedBytes - bytesWritten
        if (remaining <= 0) return 0
        val count = minOf(samples.size.toLong(), remaining).toInt().let { it - (it % 2) }
        if (count > 0) {
            output.write(samples, 0, count)
            preview.accept(samples, count, bytesWritten)
            bytesWritten += count
        }
        return count
    }

    @Synchronized fun recordGap(count: Long = 1) { gaps += count.coerceAtLeast(0) }
    @Synchronized fun recordOverrun(count: Long = 1) { overruns += count.coerceAtLeast(0) }

    @Synchronized fun cancel() {
        if (closed) return
        cancelled = true
        output.close()
        closed = true
        iqPart.delete()
    }

    @Synchronized fun complete(): CaptureResult {
        check(!cancelled) { "Capture was cancelled" }
        check(!closed) { "Capture is already closed" }
        output.flush()
        fileOutput.fd.sync()
        output.close()
        closed = true
        if (bytesWritten != expectedBytes || gaps != 0L || overruns != 0L) {
            iqPart.delete()
            error("Incomplete capture: $bytesWritten/$expectedBytes bytes, $gaps gaps, $overruns overruns")
        }
        val sha = iqPart.inputStream().use(::sha256Hex)
        val iq = File(directory, "${metadata.id}.cs8")
        val sidecarPart = File(directory, "${metadata.id}.json.part")
        val sidecar = File(directory, "${metadata.id}.json")
        val previewPart = File(directory, "${metadata.id}-preview.pgm.part")
        val previewFile = File(directory, "${metadata.id}-preview.pgm")
        sidecarPart.writeText(sidecarJson(sha), Charsets.UTF_8)
        preview.writePgm(previewPart)
        try {
            atomicRename(sidecarPart, sidecar)
            atomicRename(previewPart, previewFile)
            // IQ is the commit marker. If the process dies between renames,
            // startup cleanup removes the sidecar/preview pair that has no IQ.
            atomicRename(iqPart, iq)
        } catch (failure: Throwable) {
            iq.delete(); sidecar.delete(); previewFile.delete(); iqPart.delete(); sidecarPart.delete(); previewPart.delete()
            throw failure
        }
        return CaptureResult(iq, sidecar, previewFile, bytesWritten, bytesWritten / 2L, sha)
    }

    override fun close() { cancel() }

    private fun sidecarJson(sha: String): String = """{
  "schemaVersion":"1.0.0",
  "id":${json(metadata.id)},
  "fingerprintId":${nullableJson(metadata.fingerprintId)},
  "surveyId":${nullableJson(metadata.surveyId)},
  "sampleFormat":"signed-int8-interleaved-iq",
  "componentOrder":"I,Q",
  "byteOrder":"not-applicable-single-byte-components",
  "centerFrequencyHz":${metadata.centerFrequencyHz},
  "sampleRateHz":${metadata.sampleRateHz},
  "requestedDurationMs":${metadata.durationMs},
  "startedAtEpochMs":${metadata.startedAtEpochMs},
  "startedMonotonicNs":${metadata.startedMonotonicNs},
  "expectedByteCount":$expectedBytes,
  "actualByteCount":$bytesWritten,
  "complexSampleCount":${bytesWritten / 2L},
  "gapCount":$gaps,
  "overrunCount":$overruns,
  "sha256":"$sha",
  "radio":{"basebandFilterHz":${metadata.basebandFilterHz},"lnaGainDb":${metadata.lnaGainDb},"vgaGainDb":${metadata.vgaGainDb},"rfAmpEnabled":${metadata.rfAmpEnabled},"antennaPowerEnabled":${metadata.antennaPowerEnabled}},
  "equipmentProfileVersionId":${json(metadata.equipmentProfileVersionId)},
  "deviceSerialSuffix":${nullableJson(metadata.serialSuffix)},
  "location":${locationJson()},
  "note":${json(metadata.note)},
  "versions":{"app":${json(metadata.appVersion)},"detector":"m2-detector-v1","clustering":"m2-fingerprint-v1","aggregation":"m3-grid-v1","profile":${json(metadata.equipmentProfileVersionId)}}
}
"""

    private fun locationJson(): String = if (metadata.latitude == null || metadata.longitude == null) "null" else
        "{\"crs\":\"EPSG:4326\",\"latitude\":${metadata.latitude},\"longitude\":${metadata.longitude},\"horizontalAccuracyM\":${metadata.horizontalAccuracyM},\"ageMs\":${metadata.locationAgeMs}}"
}

private class PreviewAccumulator(private val expectedBytes: Long, private val width: Int = 64, private val height: Int = 32) {
    private val pixels = ByteArray(width * height)
    private val rowWritten = BooleanArray(height)

    fun accept(bytes: ByteArray, count: Int, offset: Long) {
        if (count < 2) return
        val row = ((offset * height) / expectedBytes).toInt().coerceIn(0, height - 1)
        if (rowWritten[row]) return
        val pairs = count / 2
        for (x in 0 until width) {
            val pair = (x.toLong() * pairs / width).toInt().coerceAtMost(pairs - 1)
            val i = bytes[pair * 2].toInt()
            val q = bytes[pair * 2 + 1].toInt()
            val magnitude = kotlin.math.sqrt((i * i + q * q).toDouble()).coerceIn(0.0, 180.0)
            pixels[row * width + x] = (magnitude / 180.0 * 255.0).toInt().toByte()
        }
        rowWritten[row] = true
    }

    fun writePgm(file: File) {
        file.outputStream().buffered().use { out ->
            out.write("P5\n$width $height\n255\n".toByteArray(Charsets.US_ASCII))
            out.write(pixels)
        }
    }
}

fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count > 0) digest.update(buffer, 0, count)
    }
    return digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
}

private fun atomicRename(source: File, destination: File) {
    check(!destination.exists()) { "Refusing to replace ${destination.name}" }
    check(source.renameTo(destination)) { "Could not finalize ${destination.name}" }
}

internal fun json(value: String): String = buildString {
    append('"')
    value.forEach { character -> when (character) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
    } }
    append('"')
}

private fun nullableJson(value: String?): String = value?.let(::json) ?: "null"
