package dev.rfnotebook.storage

import java.io.File
import java.io.RandomAccessFile
import java.util.LinkedHashMap
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
) { companion object }

data class BundleInspection(val manifest: String, val paths: Set<String>)

object SurveyBundleExporter {
    fun create(destination: File, source: SurveyBundleContent, policy: ExportPolicy): File {
        destination.parentFile?.let { check(it.isDirectory || it.mkdirs()) { "Could not create export directory" } }
        val part = File(destination.parentFile, "${destination.name}.part")
        val entries = LinkedHashMap<String, ByteArray>()
        entries["survey.json"] = surveyJson(source, policy).toByteArray()
        entries["observations.csv"] = redactObservations(source.observationsCsv, policy.coordinateMode).toByteArray()
        entries["geographic-aggregates.geojson"] = redactCoordinates(source.aggregatesGeoJson, policy.coordinateMode).toByteArray()
        if (policy.includeRoutes) entries["route.geojson"] = redactCoordinates(source.routeGeoJson, policy.coordinateMode).toByteArray()
        if (policy.includeNotes) entries["notes.txt"] = source.notes.toByteArray()
        if (policy.includeIq) source.captureFiles.forEach { file ->
            require(file.isFile && file.extension.lowercase() in setOf("cs8", "json", "pgm")) { "Unsupported capture artifact" }
            entries["captures/${file.name}"] = file.readBytes()
        }
        val fileRecords = entries.map { (path, bytes) ->
            val hash = bytes.inputStream().use(::sha256Hex)
            "{\"path\":${json(path)},\"bytes\":${bytes.size},\"sha256\":\"$hash\"}"
        }
        val manifest = """{"schemaVersion":"1.0.0","surveyId":${json(source.surveyId)},"generatedAtEpochMs":${source.generatedAtEpochMs},"appVersion":${json(source.appVersion)},"coordinateMode":"${policy.coordinateMode.name.lowercase()}","includesIq":${policy.includeIq},"includesRoutes":${policy.includeRoutes},"includesNotes":${policy.includeNotes},"includesDeviceIdentifiers":${policy.includeDeviceIdentifiers},"deviceSerialSuffix":${if (policy.includeDeviceIdentifiers) source.serialSuffix?.let(::json) ?: "null" else "null"},"notes":${if (policy.includeNotes) json(source.notes) else "null"},"files":[${fileRecords.joinToString(",")}]}
"""
        ZipOutputStream(part.outputStream().buffered()).use { zip ->
            zip.setLevel(6)
            put(zip, "manifest.json", manifest.toByteArray())
            entries.forEach { (path, bytes) -> put(zip, path, bytes) }
        }
        if (destination.exists()) destination.delete()
        check(part.renameTo(destination)) { "Could not finalize export" }
        return destination
    }

    private fun put(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path).apply { time = 0L })
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun surveyJson(source: SurveyBundleContent, policy: ExportPolicy) =
        "{\"schemaVersion\":\"1.0.0\",\"id\":${json(source.surveyId)},\"name\":${json(source.surveyName)},\"deviceSerialSuffix\":${if (policy.includeDeviceIdentifiers) source.serialSuffix?.let(::json) ?: "null" else "null"},\"notes\":${if (policy.includeNotes) json(source.notes) else "null"}}"

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
        CoordinateMode.ROUNDED -> Regex("-?\\d+\\.\\d{4,}").replace(jsonText) { match -> "%.3f".format(java.util.Locale.ROOT, match.value.toDouble()) }
        CoordinateMode.OMITTED -> "{\"type\":\"FeatureCollection\",\"features\":[],\"redaction\":\"coordinates omitted\"}"
    }
}

object SurveyBundleImporter {
    private const val MAX_ENTRIES = 1_000
    private const val MAX_TOTAL_BYTES = 1_000_000_000L
    private val hashRecord = Regex("\\{\\\"path\\\":\\\"([^\\\"]+)\\\",\\\"bytes\\\":(\\d+),\\\"sha256\\\":\\\"([0-9a-f]+)\\\"}")

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
            total = Math.addExact(total, entry.size)
            require(total <= MAX_TOTAL_BYTES) { "Archive expands beyond the total size limit" }
            if (entry.compressedSize > 0) require(entry.size / entry.compressedSize.coerceAtLeast(1) <= 100) { "Suspicious compression ratio" }
            if (entry.name == "manifest.json") manifest = zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }
        val manifestText = requireNotNull(manifest) { "Archive has no manifest" }
        require(Regex("\"schemaVersion\"\\s*:\\s*\"1\\.0\\.0\"").containsMatchIn(manifestText)) { "Unsupported bundle schema" }
        val records = hashRecord.findAll(manifestText).associate { it.groupValues[1] to (it.groupValues[2].toLong() to it.groupValues[3]) }
        require(records.keys == seen - "manifest.json") { "Manifest inventory does not match archive contents" }
        records.forEach { (path, expected) ->
            require(expected.second.length == 64) { "Invalid hash for $path" }
            val entry = zip.getEntry(path) ?: error("Manifest entry is missing: $path")
            require(entry.size == expected.first) { "Length mismatch for $path" }
            val actual = zip.getInputStream(entry).use(::sha256Hex)
            require(actual == expected.second) { "Hash mismatch for $path" }
        }
        BundleInspection(manifestText, seen)
    }

    fun import(archive: File, destination: File, maxEntryBytes: Long = 512_000_000L): File {
        require(!destination.exists()) { "Import destination already exists" }
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
                zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
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
