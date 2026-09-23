package dev.rfnotebook.storage

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.io.StringReader
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

enum class CoordinateMode { FULL, ROUNDED, OMITTED }

data class ExportPolicy(
    val includeIq: Boolean = true,
    val includeRoutes: Boolean = true,
    val coordinateMode: CoordinateMode = CoordinateMode.FULL,
    val includeNotes: Boolean = true,
    val includeDeviceIdentifiers: Boolean = true,
)

data class SurveyBundleContent(
    val surveyId: String,
    val surveyName: String,
    val generatedAtEpochMs: Long,
    val appVersion: String,
    val serialSuffix: String?,
    val notes: String,
    val observationsCsv: String,
    val routeGeoJson: String,
    val aggregatesGeoJson: String,
    val captureFiles: List<File>,
    val gapsCsv: String = "reason,started_epoch_ms,ended_epoch_ms,dropped_units,explanation\n",
    val equipment: BundleEquipmentMetadata? = null,
    val band: BundleBandMetadata? = null,
) { companion object }

data class BundleEquipmentMetadata(
    val versionId: String,
    val antennaName: String,
    val adapterNotes: String,
    val sampleRateHz: Int,
    val basebandFilterHz: Int,
    val lnaGainDb: Int,
    val vgaGainDb: Int,
    val rfAmpEnabled: Boolean,
    val antennaPowerEnabled: Boolean,
)

data class BundleBandMetadata(
    val versionId: String,
    val name: String,
    val binWidthHz: Long,
    val targetRevisitMs: Long,
    val thresholdSnrDb: Float,
    val minimumBandwidthHz: Long,
    val ranges: String,
)

data class BundleInspection(val manifest: String, val paths: Set<String>)

object SurveyBundleExporter {
    fun create(destination: File, source: SurveyBundleContent, policy: ExportPolicy): File {
        destination.parentFile?.let { check(it.isDirectory || it.mkdirs()) { "Could not create export directory" } }
        val part = File(destination.parentFile, "${destination.name}.part")
        val byteEntries = LinkedHashMap<String, ByteArray>()
        byteEntries["survey.json"] = surveyJson(source, policy).toByteArray()
        byteEntries["observations.csv"] = redactObservations(source.observationsCsv, policy.coordinateMode).toByteArray()
        byteEntries["geographic-aggregates.geojson"] = redactCoordinates(source.aggregatesGeoJson, policy.coordinateMode).toByteArray()
        byteEntries["gaps.csv"] = source.gapsCsv.toByteArray()
        if (policy.includeRoutes) byteEntries["route.geojson"] = redactCoordinates(source.routeGeoJson, policy.coordinateMode).toByteArray()
        if (policy.includeNotes) byteEntries["notes.txt"] = source.notes.toByteArray()
        val fileEntries = LinkedHashMap<String, File>()
        if (policy.includeIq) source.captureFiles.forEach { file ->
            require(file.isFile && file.extension.lowercase() in setOf("cs8", "json", "pgm")) { "Unsupported capture artifact" }
            val path = "captures/${file.name}"
            require(path !in byteEntries && path !in fileEntries) { "Duplicate capture artifact name" }
            if (file.extension.equals("json", ignoreCase = true)) {
                byteEntries[path] = redactCaptureSidecar(file.readText(), policy).toByteArray()
            } else {
                fileEntries[path] = file
            }
        }
        val byteRecords = byteEntries.map { (path, bytes) ->
            val hash = bytes.inputStream().use(::sha256Hex)
            "{\"path\":${json(path)},\"bytes\":${bytes.size},\"sha256\":\"$hash\"}"
        }
        val captureRecords = fileEntries.map { (path, file) ->
            val hash = file.inputStream().buffered().use(::sha256Hex)
            "{\"path\":${json(path)},\"bytes\":${file.length()},\"sha256\":\"$hash\"}"
        }
        val fileRecords = byteRecords + captureRecords
        val includesIqArtifacts = byteEntries.keys.any { it.startsWith("captures/") } ||
            fileEntries.keys.any { it.startsWith("captures/") }
        val manifest = """{"schemaVersion":"1.0.0","surveyId":${json(source.surveyId)},"generatedAtEpochMs":${source.generatedAtEpochMs},"appVersion":${json(source.appVersion)},"coordinateMode":"${policy.coordinateMode.name.lowercase()}","includesIq":$includesIqArtifacts,"includesRoutes":${policy.includeRoutes},"includesNotes":${policy.includeNotes},"includesDeviceIdentifiers":${policy.includeDeviceIdentifiers},"deviceSerialSuffix":${if (policy.includeDeviceIdentifiers) source.serialSuffix?.let(::json) ?: "null" else "null"},"notes":${if (policy.includeNotes) json(source.notes) else "null"},"files":[${fileRecords.joinToString(",")}]}
"""
        try {
            ZipOutputStream(part.outputStream().buffered()).use { zip ->
                zip.setLevel(6)
                put(zip, "manifest.json", manifest.toByteArray())
                byteEntries.forEach { (path, bytes) -> put(zip, path, bytes) }
                fileEntries.forEach { (path, file) ->
                    zip.putNextEntry(ZipEntry(path).apply { time = 0L })
                    file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } catch (failure: Throwable) {
            part.delete()
            throw failure
        }
        try {
            Files.move(part.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (unsupported: AtomicMoveNotSupportedException) {
            // Never remove a prior completed export to make room for a non-atomic replacement.
            // The complete .part remains available for recovery or a retry on a capable volume.
            throw IllegalStateException("Export volume does not support atomic finalization", unsupported)
        }
        return destination
    }

    private fun put(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path).apply { time = 0L })
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun surveyJson(source: SurveyBundleContent, policy: ExportPolicy): String {
        val equipment = source.equipment?.let { value ->
            """{"versionId":${json(value.versionId)},"antennaName":${if (policy.includeNotes) json(value.antennaName) else "null"},"adapterNotes":${if (policy.includeNotes) json(value.adapterNotes) else "null"},"sampleRateHz":${value.sampleRateHz},"basebandFilterHz":${value.basebandFilterHz},"lnaGainDb":${value.lnaGainDb},"vgaGainDb":${value.vgaGainDb},"rfAmpEnabled":${value.rfAmpEnabled},"antennaPowerEnabled":${value.antennaPowerEnabled}}"""
        } ?: "null"
        val band = source.band?.let { value ->
            """{"versionId":${json(value.versionId)},"name":${if (policy.includeNotes) json(value.name) else "null"},"binWidthHz":${value.binWidthHz},"targetRevisitMs":${value.targetRevisitMs},"thresholdSnrDb":${value.thresholdSnrDb},"minimumBandwidthHz":${value.minimumBandwidthHz},"ranges":${json(value.ranges)}}"""
        } ?: "null"
        return "{\"schemaVersion\":\"1.0.0\",\"id\":${json(source.surveyId)},\"name\":${if (policy.includeNotes) json(source.surveyName) else "null"},\"deviceSerialSuffix\":${if (policy.includeDeviceIdentifiers) source.serialSuffix?.let(::json) ?: "null" else "null"},\"notes\":${if (policy.includeNotes) json(source.notes) else "null"},\"equipment\":$equipment,\"band\":$band}"
    }

    private fun redactObservations(csv: String, mode: CoordinateMode): String {
        if (mode == CoordinateMode.FULL) return csv
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return csv
        val headers = lines.first().split(',')
        val lat = headers.indexOf("latitude")
        val lon = headers.indexOf("longitude")
        if (lat < 0 || lon < 0) return csv
        val kept = headers.indices.filter { mode != CoordinateMode.OMITTED || it !in setOf(lat, lon) }
        return buildString {
            append(kept.joinToString(",") { headers[it] }).append('\n')
            lines.drop(1).forEach { line ->
                val values = line.split(',').toMutableList()
                if (mode == CoordinateMode.ROUNDED) {
                    values[lat] = values[lat].toDoubleOrNull()?.let { "%.3f".format(java.util.Locale.ROOT, it) }.orEmpty()
                    values[lon] = values[lon].toDoubleOrNull()?.let { "%.3f".format(java.util.Locale.ROOT, it) }.orEmpty()
                }
                append(kept.joinToString(",") { values.getOrElse(it) { "" } }).append('\n')
            }
        }
    }

    private fun redactCoordinates(jsonText: String, mode: CoordinateMode): String = when (mode) {
        CoordinateMode.FULL -> jsonText
        CoordinateMode.ROUNDED -> Regex("(\\[\\s*)(-?\\d+(?:\\.\\d+)?)(\\s*,\\s*)(-?\\d+(?:\\.\\d+)?)(\\s*])")
            .replace(jsonText) { match ->
                val longitude = "%.3f".format(java.util.Locale.ROOT, match.groupValues[2].toDouble())
                val latitude = "%.3f".format(java.util.Locale.ROOT, match.groupValues[4].toDouble())
                match.groupValues[1] + longitude + match.groupValues[3] + latitude + match.groupValues[5]
            }
        CoordinateMode.OMITTED -> "{\"type\":\"FeatureCollection\",\"features\":[],\"redaction\":\"coordinates omitted\"}"
    }

    private fun redactCaptureSidecar(jsonText: String, policy: ExportPolicy): String {
        var result = jsonText
        if (!policy.includeDeviceIdentifiers) {
            result = Regex("\"deviceSerialSuffix\"\\s*:\\s*(?:null|\"(?:\\\\.|[^\"])*\")")
                .replace(result, "\"deviceSerialSuffix\":null")
        }
        if (!policy.includeNotes) {
            result = Regex("\"note\"\\s*:\\s*\"(?:\\\\.|[^\"])*\"")
                .replace(result, "\"note\":\"\"")
        }
        result = when (policy.coordinateMode) {
            CoordinateMode.FULL -> result
            CoordinateMode.OMITTED -> Regex("\"location\"\\s*:\\s*(?:null|\\{[^{}]*})")
                .replace(result, "\"location\":null")
            CoordinateMode.ROUNDED -> Regex("(\"(?:latitude|longitude)\"\\s*:\\s*)(-?\\d+(?:\\.\\d+)?)")
                .replace(result) { match ->
                    val rounded = "%.3f".format(java.util.Locale.ROOT, match.groupValues[2].toDouble())
                    match.groupValues[1] + rounded
                }
        }
        return result
    }
}

object SurveyBundleImporter {
    private const val MAX_ENTRIES = 1_000
    private const val MAX_TOTAL_BYTES = 1_000_000_000L
    private const val MAX_MANIFEST_BYTES = 1_000_000L
    private const val MAX_TEXT_LENGTH = 100_000
    private val manifestFields = setOf(
        "schemaVersion", "surveyId", "generatedAtEpochMs", "appVersion", "coordinateMode",
        "includesIq", "includesRoutes", "includesNotes", "includesDeviceIdentifiers",
        "deviceSerialSuffix", "notes", "files",
    )
    private val fileRecordFields = setOf("path", "bytes", "sha256")
    private val sha256Pattern = Regex("[0-9a-f]{64}")

    fun stageIncoming(
        input: InputStream,
        destination: File,
        availableBytes: () -> Long,
        maximumArchiveBytes: Long = 600_000_000L,
        storageReserveBytes: Long = 256L * 1024L * 1024L,
    ): File {
        require(maximumArchiveBytes > 0 && storageReserveBytes >= 0)
        require(!destination.exists()) { "Incoming archive destination already exists" }
        destination.parentFile?.let { check(it.isDirectory || it.mkdirs()) { "Could not create import cache" } }
        val part = File(destination.parentFile, "${destination.name}.part")
        part.delete()
        var total = 0L
        try {
            FileOutputStream(part).use { file ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total = Math.addExact(total, count.toLong())
                    require(total <= maximumArchiveBytes) { "Selected archive exceeds the compressed-size limit" }
                    require(availableBytes() - count >= storageReserveBytes) { "Import would consume the configured storage reserve" }
                    file.write(buffer, 0, count)
                }
                file.fd.sync()
            }
            check(part.renameTo(destination)) { "Could not finalize incoming archive" }
            return destination
        } catch (failure: Throwable) {
            part.delete()
            destination.delete()
            throw failure
        }
    }

    private data class ParsedManifest(
        val records: Map<String, Pair<Long, String>>,
        val includesIq: Boolean,
        val includesRoutes: Boolean,
        val includesNotes: Boolean,
        val includesDeviceIdentifiers: Boolean,
        val deviceSerialSuffix: String?,
    )

    fun inspect(archive: File, maxEntryBytes: Long = 512_000_000L): BundleInspection = ZipFile(archive).use { zip ->
        require(!containsUnixSymlink(archive)) { "Symbolic links are not accepted" }
        val seen = linkedSetOf<String>()
        var total = 0L
        var manifest: String? = null
        val entries = zip.entries().asSequence().toList()
        require(entries.size <= MAX_ENTRIES) { "Archive has too many entries" }
        entries.forEach { entry ->
            validatePath(entry.name)
            require(seen.add(entry.name)) { "Duplicate archive path: ${entry.name}" }
            require(!entry.isDirectory) { "Directory entries are not accepted" }
            require(entry.size in 0..maxEntryBytes) { "Oversized or unknown entry: ${entry.name}" }
            if (entry.name == "manifest.json") {
                require(entry.size <= MAX_MANIFEST_BYTES) { "Manifest is too large" }
            }
            total = Math.addExact(total, entry.size)
            require(total <= MAX_TOTAL_BYTES) { "Archive expands beyond the total size limit" }
            if (entry.compressedSize > 0) {
                require(entry.compressedSize >= (entry.size + 99L) / 100L) { "Suspicious compression ratio" }
            }
            if (entry.name == "manifest.json") manifest = zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }
        val manifestText = requireNotNull(manifest) { "Archive has no manifest" }
        val parsed = parseManifest(manifestText)
        require(parsed.records.keys == seen - "manifest.json") { "Manifest inventory does not match archive contents" }
        require(parsed.includesIq == seen.any { it.startsWith("captures/") }) {
            "IQ inclusion flag does not match archive contents"
        }
        require(parsed.includesRoutes == ("route.geojson" in seen)) { "Route inclusion flag does not match archive contents" }
        require(parsed.includesNotes == ("notes.txt" in seen)) { "Notes inclusion flag does not match archive contents" }
        require(parsed.includesDeviceIdentifiers || parsed.deviceSerialSuffix == null) { "Redacted manifest contains a device identifier" }
        parsed.records.forEach { (path, expected) ->
            require(expected.second.length == 64) { "Invalid hash for $path" }
            val entry = zip.getEntry(path) ?: error("Manifest entry is missing: $path")
            require(entry.size == expected.first) { "Length mismatch for $path" }
            val actual = zip.getInputStream(entry).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                var count = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    count = Math.addExact(count, read.toLong())
                    require(count <= expected.first) { "Entry expands beyond declared length: $path" }
                    digest.update(buffer, 0, read)
                }
                require(count == expected.first) { "Entry length changed while reading: $path" }
                digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
            }
            require(actual == expected.second) { "Hash mismatch for $path" }
        }
        validateCaptureGroups(zip, seen, parsed)
        BundleInspection(manifestText, seen)
    }

    private fun validateCaptureGroups(zip: ZipFile, paths: Set<String>, manifest: ParsedManifest) {
        val capturePaths = paths.filter { it.startsWith("captures/") }
        if (capturePaths.isEmpty()) return
        val groups = linkedMapOf<String, MutableSet<String>>()
        capturePaths.forEach { path ->
            val filename = path.removePrefix("captures/")
            require('/' !in filename && '\\' !in filename) { "Nested capture paths are not supported" }
            val (id, kind) = when {
                filename.endsWith("-preview.pgm") -> filename.removeSuffix("-preview.pgm") to "preview"
                filename.endsWith(".cs8") -> filename.removeSuffix(".cs8") to "iq"
                filename.endsWith(".json") -> filename.removeSuffix(".json") to "sidecar"
                else -> throw IllegalArgumentException("Unsupported capture artifact: $filename")
            }
            require(id.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "Unsafe capture identifier" }
            require(groups.getOrPut(id) { linkedSetOf() }.add(kind)) { "Duplicate capture artifact kind" }
        }
        groups.forEach { (id, kinds) ->
            require(kinds == setOf("iq", "sidecar", "preview")) { "Capture $id does not contain a complete IQ/sidecar/preview set" }
            val iqPath = "captures/$id.cs8"
            val sidecarPath = "captures/$id.json"
            val sidecarEntry = requireNotNull(zip.getEntry(sidecarPath))
            require(sidecarEntry.size in 1..MAX_MANIFEST_BYTES) { "Capture sidecar is too large" }
            val sidecar = zip.getInputStream(sidecarEntry).bufferedReader().use { it.readText() }
            val metadata = CaptureSidecarValidator.validate(sidecar)
            require(metadata.id == id) { "Capture sidecar identifier does not match its filename" }
            val iq = requireNotNull(manifest.records[iqPath])
            require(metadata.expectedByteCount == iq.first && metadata.actualByteCount == iq.first) { "Capture sidecar byte count does not match IQ" }
            require(metadata.sha256 == iq.second) { "Capture sidecar hash does not match IQ" }
        }
    }

    private fun parseManifest(text: String): ParsedManifest {
        var schemaVersion: String? = null
        var surveyId: String? = null
        var generatedAtEpochMs: Long? = null
        var appVersion: String? = null
        var coordinateMode: String? = null
        var includesIq: Boolean? = null
        var includesRoutes: Boolean? = null
        var includesNotes: Boolean? = null
        var includesDeviceIdentifiers: Boolean? = null
        var deviceSerialSuffix: String? = null
        var deviceSerialSeen = false
        var notesSeen = false
        var records: Map<String, Pair<Long, String>>? = null
        val seen = linkedSetOf<String>()

        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            requireToken(reader, JsonToken.BEGIN_OBJECT, "Manifest must be a JSON object")
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                require(name in manifestFields) { "Unknown manifest field: $name" }
                require(seen.add(name)) { "Duplicate manifest field: $name" }
                when (name) {
                    "schemaVersion" -> schemaVersion = nextBoundedString(reader, name)
                    "surveyId" -> surveyId = nextBoundedString(reader, name)
                    "generatedAtEpochMs" -> generatedAtEpochMs = nextNonNegativeLong(reader, name)
                    "appVersion" -> appVersion = nextBoundedString(reader, name)
                    "coordinateMode" -> coordinateMode = nextBoundedString(reader, name)
                    "includesIq" -> includesIq = nextBoolean(reader, name)
                    "includesRoutes" -> includesRoutes = nextBoolean(reader, name)
                    "includesNotes" -> includesNotes = nextBoolean(reader, name)
                    "includesDeviceIdentifiers" -> includesDeviceIdentifiers = nextBoolean(reader, name)
                    "deviceSerialSuffix" -> {
                        deviceSerialSeen = true
                        deviceSerialSuffix = nextNullableString(reader, name)
                    }
                    "notes" -> {
                        notesSeen = true
                        nextNullableString(reader, name)
                    }
                    "files" -> records = parseFileRecords(reader)
                }
            }
            reader.endObject()
            requireToken(reader, JsonToken.END_DOCUMENT, "Trailing content after manifest")
        }
        require(seen == manifestFields && deviceSerialSeen && notesSeen) { "Manifest is missing required fields" }
        require(schemaVersion == "1.0.0") { "Unsupported bundle schema" }
        require(!surveyId.isNullOrBlank()) { "Survey identifier is empty" }
        require(generatedAtEpochMs != null) { "Missing generation time" }
        require(!appVersion.isNullOrBlank()) { "Application version is empty" }
        require(coordinateMode in setOf("full", "rounded", "omitted")) { "Invalid coordinate mode" }
        return ParsedManifest(
            requireNotNull(records), requireNotNull(includesIq), requireNotNull(includesRoutes),
            requireNotNull(includesNotes), requireNotNull(includesDeviceIdentifiers), deviceSerialSuffix,
        )
    }

    private fun parseFileRecords(reader: JsonReader): Map<String, Pair<Long, String>> {
        requireToken(reader, JsonToken.BEGIN_ARRAY, "Manifest files must be an array")
        val records = linkedMapOf<String, Pair<Long, String>>()
        reader.beginArray()
        while (reader.hasNext()) {
            require(records.size < MAX_ENTRIES) { "Manifest has too many file records" }
            requireToken(reader, JsonToken.BEGIN_OBJECT, "File record must be an object")
            var path: String? = null
            var bytes: Long? = null
            var hash: String? = null
            val fields = linkedSetOf<String>()
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                require(name in fileRecordFields) { "Unknown file record field: $name" }
                require(fields.add(name)) { "Duplicate file record field: $name" }
                when (name) {
                    "path" -> path = nextBoundedString(reader, name)
                    "bytes" -> bytes = nextNonNegativeLong(reader, name).also {
                        require(it <= 512_000_000L) { "Manifest file length is too large" }
                    }
                    "sha256" -> hash = nextBoundedString(reader, name).also {
                        require(sha256Pattern.matches(it)) { "Invalid manifest SHA-256" }
                    }
                }
            }
            reader.endObject()
            require(fields == fileRecordFields) { "File record is missing required fields" }
            val resolvedPath = requireNotNull(path)
            validatePath(resolvedPath)
            require(records.put(resolvedPath, requireNotNull(bytes) to requireNotNull(hash)) == null) {
                "Duplicate manifest file record: $resolvedPath"
            }
        }
        reader.endArray()
        return records
    }

    private fun nextBoundedString(reader: JsonReader, field: String): String {
        requireToken(reader, JsonToken.STRING, "$field must be a string")
        return reader.nextString().also { require(it.length <= MAX_TEXT_LENGTH) { "$field is too long" } }
    }

    private fun nextNullableString(reader: JsonReader, field: String): String? = when (reader.peek()) {
        JsonToken.NULL -> { reader.nextNull(); null }
        JsonToken.STRING -> nextBoundedString(reader, field)
        else -> throw IllegalArgumentException("$field must be a string or null")
    }

    private fun nextNonNegativeLong(reader: JsonReader, field: String): Long {
        requireToken(reader, JsonToken.NUMBER, "$field must be an integer")
        return reader.nextString().let {
            require(Regex("0|[1-9][0-9]*").matches(it)) { "$field must be a non-negative integer" }
            it.toLongOrNull() ?: throw IllegalArgumentException("$field is outside the supported range")
        }
    }

    private fun nextBoolean(reader: JsonReader, field: String): Boolean {
        requireToken(reader, JsonToken.BOOLEAN, "$field must be a boolean")
        return reader.nextBoolean()
    }

    private fun requireToken(reader: JsonReader, expected: JsonToken, message: String) {
        require(reader.peek() == expected) { message }
    }

    fun import(
        archive: File,
        destination: File,
        maxEntryBytes: Long = 512_000_000L,
        availableBytes: () -> Long = { Long.MAX_VALUE },
        storageReserveBytes: Long = 256L * 1024L * 1024L,
    ): File {
        require(!destination.exists()) { "Import destination already exists" }
        require(storageReserveBytes >= 0)
        inspect(archive, maxEntryBytes)
        val staging = File(destination.parentFile, "${destination.name}.part")
        if (staging.exists()) staging.deleteRecursively()
        check(staging.mkdirs()) { "Could not create import staging directory" }
        try {
            ZipFile(archive).use { zip -> zip.entries().asSequence().forEach { entry ->
                val target = File(staging, entry.name)
                val rootPath = staging.canonicalFile.toPath()
                require(target.canonicalFile.toPath().startsWith(rootPath)) { "Archive path escapes staging" }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input -> target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        copied = Math.addExact(copied, count.toLong())
                        require(copied <= entry.size) { "Entry expands during import: ${entry.name}" }
                        require(availableBytes() - count >= storageReserveBytes) { "Import would consume the configured storage reserve" }
                        output.write(buffer, 0, count)
                    }
                    require(copied == entry.size) { "Entry length changed during import: ${entry.name}" }
                } }
            } }
            check(staging.renameTo(destination)) { "Could not commit imported bundle" }
            return destination
        } catch (failure: Throwable) {
            staging.deleteRecursively()
            throw failure
        }
    }

    private fun validatePath(path: String) {
        require(path.isNotBlank() && !path.startsWith('/') && !path.startsWith('\\')) { "Absolute archive path" }
        require(!Regex("^[A-Za-z]:").containsMatchIn(path)) { "Drive-qualified archive path" }
        require(path.split('/', '\\').none { it == ".." || it.isBlank() }) { "Unsafe archive path" }
    }

    internal fun validatePathsForTest(paths: List<String>) {
        val seen = mutableSetOf<String>()
        paths.forEach { path -> validatePath(path); require(seen.add(path)) { "Duplicate archive path: $path" } }
    }

    /** ZipEntry does not expose Unix mode bits, so inspect the central directory directly. */
    internal fun containsUnixSymlink(archive: File): Boolean = RandomAccessFile(archive, "r").use { file ->
        val eocd = findEndOfCentralDirectory(file)
        file.seek(eocd + 10)
        val entryCount = file.readLeUShort()
        file.seek(eocd + 16)
        val centralOffset = file.readLeUInt()
        require(entryCount != 0xffff && centralOffset != 0xffffffffL) { "ZIP64 bundles are not supported" }
        var header = centralOffset
        repeat(entryCount) {
            file.seek(header)
            require(file.readLeUInt() == 0x02014b50L) { "Malformed ZIP central directory" }
            val versionMadeBy = file.readLeUShort()
            file.seek(header + 28)
            val nameLength = file.readLeUShort()
            val extraLength = file.readLeUShort()
            val commentLength = file.readLeUShort()
            file.seek(header + 38)
            val externalAttributes = file.readLeUInt()
            val hostSystem = versionMadeBy ushr 8
            val unixMode = (externalAttributes ushr 16).toInt()
            if (hostSystem in setOf(3, 19) && unixMode and 0xf000 == 0xa000) return@use true
            header += 46L + nameLength + extraLength + commentLength
        }
        false
    }

    private fun findEndOfCentralDirectory(file: RandomAccessFile): Long {
        val minimum = maxOf(0L, file.length() - 65_557L)
        var position = file.length() - 22L
        while (position >= minimum) {
            file.seek(position)
            if (file.readLeUInt() == 0x06054b50L) return position
            position--
        }
        error("Archive has no end-of-central-directory record")
    }
}

private fun RandomAccessFile.readLeUShort(): Int {
    val low = read()
    val high = read()
    require(low >= 0 && high >= 0) { "Truncated ZIP metadata" }
    return low or (high shl 8)
}

private fun RandomAccessFile.readLeUInt(): Long {
    val low = readLeUShort()
    val high = readLeUShort()
    return low.toLong() or (high.toLong() shl 16)
}
