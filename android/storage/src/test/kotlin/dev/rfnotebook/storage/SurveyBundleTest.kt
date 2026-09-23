package dev.rfnotebook.storage

import java.io.File
import java.io.ByteArrayInputStream
import java.io.RandomAccessFile
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyBundleTest {
    @Test fun `incoming archive staging enforces compressed size and storage reserve atomically`() {
        val root = createTempDirectory("bundle-stage").toFile()
        val destination = File(root, "incoming-test.zip")

        assertTrue(runCatching {
            SurveyBundleImporter.stageIncoming(ByteArrayInputStream(ByteArray(9)), destination, { 1_000 }, maximumArchiveBytes = 8, storageReserveBytes = 0)
        }.isFailure)
        assertFalse(destination.exists())
        assertFalse(File(root, "incoming-test.zip.part").exists())

        assertTrue(runCatching {
            SurveyBundleImporter.stageIncoming(ByteArrayInputStream(ByteArray(8)), destination, { 7 }, maximumArchiveBytes = 8, storageReserveBytes = 0)
        }.isFailure)
        assertFalse(destination.exists())

        SurveyBundleImporter.stageIncoming(ByteArrayInputStream(ByteArray(8)), destination, { 8 }, maximumArchiveBytes = 8, storageReserveBytes = 0)
        assertEquals(8, destination.length())
    }

    @Test fun `full and redacted exports round trip independently`() {
        val root = createTempDirectory("bundle-roundtrip").toFile()
        val iq = File(root, "input.cs8").apply { writeBytes(byteArrayOf(1, -1, 2, -2)) }
        val source = SurveyBundleContent.fixture(iq)
        val full = SurveyBundleExporter.create(File(root, "full.zip"), source, ExportPolicy())
        val redacted = SurveyBundleExporter.create(
            File(root, "redacted.zip"), source,
            ExportPolicy(includeIq = false, includeRoutes = false, coordinateMode = CoordinateMode.OMITTED, includeNotes = false, includeDeviceIdentifiers = false),
        )

        val fullResult = SurveyBundleImporter.inspect(full)
        val redactedResult = SurveyBundleImporter.inspect(redacted)
        assertTrue(fullResult.paths.contains("captures/input.cs8"))
        assertTrue(fullResult.manifest.contains("1234abcd"))
        assertFalse(redactedResult.paths.any { it.startsWith("captures/") })
        assertFalse(redactedResult.paths.contains("route.geojson"))
        assertFalse(redactedResult.manifest.contains("1234abcd"))
        assertFalse(redactedResult.manifest.contains("private note"))
        assertFalse(redactedResult.manifest.contains("42.123456"))
        ZipFile(redacted).use { zip ->
            val survey = zip.getInputStream(zip.getEntry("survey.json")).bufferedReader().use { it.readText() }
            assertFalse(survey.contains("Private route name"))
            assertFalse(survey.contains("Home antenna"))
            assertFalse(survey.contains("Private band label"))
            assertFalse(survey.contains("garage adapter"))
        }

        val blockedDestination = File(root, "blocked-import")
        assertTrue(runCatching {
            SurveyBundleImporter.import(redacted, blockedDestination, availableBytes = { 0 }, storageReserveBytes = 1)
        }.isFailure)
        assertFalse(blockedDestination.exists())
        assertFalse(File(root, "blocked-import.part").exists())
    }

    @Test fun `IQ manifest flag describes actual capture content`() {
        val root = createTempDirectory("bundle-empty-iq").toFile()
        val placeholder = File(root, "unused.cs8").apply { writeBytes(byteArrayOf(1)) }
        val archive = SurveyBundleExporter.create(
            File(root, "empty-iq.zip"),
            SurveyBundleContent.fixture(placeholder).copy(captureFiles = emptyList()),
            ExportPolicy(includeIq = true),
        )

        val inspection = SurveyBundleImporter.inspect(archive)
        assertTrue(inspection.manifest.contains("\"includesIq\":false"))
        assertFalse(inspection.paths.any { it.startsWith("captures/") })

        val legacy = File(root, "legacy-reviewed-empty-iq.zip")
        ZipOutputStream(legacy.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("manifest.json"))
            out.write(validManifest().replace("\"includesIq\":false", "\"includesIq\":true").toByteArray())
            out.closeEntry()
        }
        assertTrue(runCatching { SurveyBundleImporter.inspect(legacy) }.isFailure)
    }

    @Test fun `export streams a multi-megabyte capture and preserves its hash`() {
        val root = createTempDirectory("bundle-streaming").toFile()
        val iq = File(root, "long.cs8")
        val random = Random(42)
        iq.outputStream().buffered().use { output ->
            val block = ByteArray(1_048_576)
            repeat(32) { random.nextBytes(block); output.write(block) }
        }

        val archive = SurveyBundleExporter.create(
            File(root, "streamed.zip"),
            SurveyBundleContent.fixture(iq),
            ExportPolicy(),
        )
        val inspection = SurveyBundleImporter.inspect(archive)

        assertTrue(inspection.paths.contains("captures/long.cs8"))
        assertTrue(inspection.manifest.contains("\"bytes\":33554432"))
        assertTrue(inspection.manifest.contains(iq.inputStream().use(::sha256Hex)))
        assertFalse(File(root, "streamed.zip.part").exists())
    }

    @Test fun `redacted export sanitizes protected capture sidecar fields while retaining IQ`() {
        val root = createTempDirectory("bundle-sidecar-redaction").toFile()
        val iq = File(root, "capture.cs8").apply { writeBytes(byteArrayOf(1, 2)) }
        val sidecar = File(root, "capture.json").apply { writeText(
            validCaptureSidecar(iq, "capture", "secret-serial", "secret note", location = true),
        ) }
        val preview = File(root, "capture-preview.pgm").apply { writeBytes("P5\n1 1\n255\n\u0000".toByteArray()) }
        val source = SurveyBundleContent.fixture(iq).copy(captureFiles = listOf(iq, sidecar, preview))
        val archive = SurveyBundleExporter.create(
            File(root, "redacted-with-iq.zip"), source,
            ExportPolicy(includeIq = true, includeRoutes = false, coordinateMode = CoordinateMode.OMITTED, includeNotes = false, includeDeviceIdentifiers = false),
        )

        val exportedSidecar = ZipFile(archive).use { zip ->
            zip.getInputStream(zip.getEntry("captures/capture.json")).bufferedReader().use { it.readText() }
        }
        assertFalse(exportedSidecar.contains("secret-serial"))
        assertFalse(exportedSidecar.contains("secret note"))
        assertFalse(exportedSidecar.contains("42.123456"))
        assertTrue(exportedSidecar.contains("\"deviceSerialSuffix\":null"))
        assertTrue(exportedSidecar.contains("\"location\":null"))
        SurveyBundleImporter.inspect(archive)
    }

    @Test fun `rounded geographic export changes coordinate pairs only`() {
        val root = createTempDirectory("bundle-coordinate-rounding").toFile()
        val iq = File(root, "capture.cs8").apply { writeBytes(byteArrayOf(1, 2)) }
        val source = SurveyBundleContent.fixture(iq).copy(
            aggregatesGeoJson = """{"type":"FeatureCollection","features":[{"geometry":{"type":"Point","coordinates":[-71.654321, 42.123456]},"properties":{"observedRelativeStrengthDbfs":-67.89123,"horizontalAccuracyM":4.56789}}]}""",
        )
        val archive = SurveyBundleExporter.create(
            File(root, "rounded.zip"), source,
            ExportPolicy(coordinateMode = CoordinateMode.ROUNDED),
        )

        ZipFile(archive).use { zip ->
            val aggregate = zip.getInputStream(zip.getEntry("geographic-aggregates.geojson")).bufferedReader().use { it.readText() }
            val route = zip.getInputStream(zip.getEntry("route.geojson")).bufferedReader().use { it.readText() }
            assertTrue(aggregate.contains("[-71.654, 42.123]"))
            assertTrue(route.contains("[-71.654,42.123]"))
            assertTrue(aggregate.contains("-67.89123"))
            assertTrue(aggregate.contains("4.56789"))
        }
        SurveyBundleImporter.inspect(archive)
    }

    @Test fun `atomic replacement produces a complete inspectable export`() {
        val root = createTempDirectory("bundle-replace").toFile()
        val iq = File(root, "capture.cs8").apply { writeBytes(byteArrayOf(1, 2)) }
        val destination = File(root, "survey.zip")
        SurveyBundleExporter.create(destination, SurveyBundleContent.fixture(iq), ExportPolicy())
        SurveyBundleExporter.create(
            destination,
            SurveyBundleContent.fixture(iq).copy(notes = "replacement"),
            ExportPolicy(),
        )

        assertTrue(SurveyBundleImporter.inspect(destination).manifest.contains("replacement"))
        assertFalse(File(root, "survey.zip.part").exists())
    }

    @Test fun `import rejects traversal duplicate oversized invalid hash and schema without mutation`() {
        val root = createTempDirectory("bundle-reject").toFile()
        val destination = File(root, "imports")
        val cases = mapOf(
            "traversal.zip" to listOf("../escape" to "x", "manifest.json" to validManifest()),
            "unsupported.zip" to listOf("manifest.json" to validManifest(schema = "99.0.0")),
            "invalid-hash.zip" to listOf("manifest.json" to validManifest(files = "[{\"path\":\"data.txt\",\"bytes\":1,\"sha256\":\"00\"}]"), "data.txt" to "x"),
            "oversized.zip" to listOf("manifest.json" to validManifest(), "large.bin" to "x".repeat(2_048)),
        )
        cases.forEach { (name, entries) ->
            val zip = File(root, name)
            ZipOutputStream(zip.outputStream()).use { out -> entries.forEach { (path, body) ->
                out.putNextEntry(ZipEntry(path)); out.write(body.toByteArray()); out.closeEntry()
            } }
            assertTrue(name, runCatching { SurveyBundleImporter.import(zip, destination, maxEntryBytes = 1_024) }.isFailure)
            assertFalse("$name partially mutated destination", destination.exists())
        }
        assertTrue(runCatching { SurveyBundleImporter.validatePathsForTest(listOf("manifest.json", "manifest.json")) }.isFailure)
    }

    @Test fun `import rejects unix symbolic links`() {
        val root = createTempDirectory("bundle-symlink").toFile()
        val zip = File(root, "symlink.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("manifest.json")); out.write(validManifest().toByteArray()); out.closeEntry()
        }
        markFirstCentralEntryAsUnixSymlink(zip)

        assertTrue(SurveyBundleImporter.containsUnixSymlink(zip))
        assertTrue(runCatching { SurveyBundleImporter.inspect(zip) }.isFailure)
    }

    @Test fun `archive fuzz rejects path variants and extreme compression`() {
        val maliciousPaths = buildList {
            addAll(listOf("", "/absolute", "\\absolute", "C:/drive", "C:\\drive", "a//b", "a\\\\b", "../escape", "a/../escape", "a\\..\\escape"))
            repeat(128) { index ->
                add("level-$index/../escape-$index")
                add("level-$index\\..\\escape-$index")
            }
        }
        maliciousPaths.forEach { path ->
            assertTrue(path, runCatching { SurveyBundleImporter.validatePathsForTest(listOf(path)) }.isFailure)
        }

        val root = createTempDirectory("bundle-compression").toFile()
        val zip = File(root, "compression-bomb.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("manifest.json"))
            out.write(validManifest().toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("zeros.bin"))
            out.write(ByteArray(200_000))
            out.closeEntry()
        }
        assertTrue(runCatching { SurveyBundleImporter.inspect(zip) }.isFailure)
    }

    @Test fun `manifest parser rejects unknown missing duplicate and mistyped fields`() {
        val valid = validManifest()
        val malformed = listOf(
            valid.replaceFirst("\"schemaVersion\"", "\"unknown\":1,\"schemaVersion\""),
            valid.replaceFirst(Regex("\"surveyId\":\"[^\"]+\","), ""),
            valid.replaceFirst("\"schemaVersion\":\"1.0.0\"", "\"schemaVersion\":\"1.0.0\",\"schemaVersion\":\"1.0.0\""),
            valid.replaceFirst("\"includesIq\":false", "\"includesIq\":\"false\""),
            valid.replaceFirst("\"coordinateMode\":\"full\"", "\"coordinateMode\":\"precise-ish\""),
            valid.replaceFirst("\"generatedAtEpochMs\":1", "\"generatedAtEpochMs\":1.5"),
        )
        malformed.forEachIndexed { index, manifest ->
            val root = createTempDirectory("bundle-manifest-$index").toFile()
            val zip = File(root, "invalid.zip")
            ZipOutputStream(zip.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("manifest.json")); out.write(manifest.toByteArray()); out.closeEntry()
            }
            assertTrue("malformed manifest $index", runCatching { SurveyBundleImporter.inspect(zip) }.isFailure)
        }
    }

    @Test fun `import rejects malformed or internally inconsistent capture sidecar`() {
        val root = createTempDirectory("bundle-invalid-sidecar").toFile()
        val iq = File(root, "capture.cs8").apply { writeBytes(byteArrayOf(1, -1)) }
        val fixture = SurveyBundleContent.fixture(iq)
        val sidecar = File(root, "capture.json").apply {
            writeText(validCaptureSidecar(iq, "wrong-id", null, "").replace(iq.inputStream().use(::sha256Hex), "0".repeat(64)))
        }
        val preview = File(root, "capture-preview.pgm").apply { writeBytes(byteArrayOf(0)) }
        val archive = SurveyBundleExporter.create(
            File(root, "invalid-sidecar.zip"),
            fixture.copy(captureFiles = listOf(iq, sidecar, preview)),
            ExportPolicy(),
        )

        assertTrue(runCatching { SurveyBundleImporter.inspect(archive) }.isFailure)
    }

    private fun validManifest(schema: String = "1.0.0", files: String = "[]") =
        "{\"schemaVersion\":\"$schema\",\"surveyId\":\"survey-1\",\"generatedAtEpochMs\":1,\"appVersion\":\"test\",\"coordinateMode\":\"full\",\"includesIq\":false,\"includesRoutes\":false,\"includesNotes\":false,\"includesDeviceIdentifiers\":false,\"deviceSerialSuffix\":null,\"notes\":null,\"files\":$files}"

    private fun markFirstCentralEntryAsUnixSymlink(zip: File) {
        RandomAccessFile(zip, "rw").use { file ->
            var position = 0L
            while (position <= file.length() - 4) {
                file.seek(position)
                if (file.read() == 0x50 && file.read() == 0x4b && file.read() == 0x01 && file.read() == 0x02) break
                position++
            }
            require(position <= file.length() - 46)
            file.seek(position + 5); file.write(3) // Unix host system.
            val attributes = 0xa1ff shl 16
            file.seek(position + 38)
            repeat(4) { shift -> file.write(attributes ushr (shift * 8) and 0xff) }
        }
    }

    private fun SurveyBundleContent.Companion.fixture(iq: File) = SurveyBundleContent(
        surveyId = "survey-1", surveyName = "Private route name", generatedAtEpochMs = 1_790_000_000_000,
        appVersion = "0.4.0-m4", serialSuffix = "1234abcd", notes = "private note",
        observationsCsv = "frequency_hz,latitude,longitude\\n433920000,42.123456,-71.654321\\n",
        routeGeoJson = "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[-71.654321,42.123456]}}",
        aggregatesGeoJson = "{\"type\":\"FeatureCollection\",\"features\":[]}", captureFiles = captureArtifacts(iq),
        equipment = BundleEquipmentMetadata(
            "equipment:v1", "Home antenna", "garage adapter", 2_000_000, 1_750_000,
            16, 16, false, false,
        ),
        band = BundleBandMetadata(
            "band:v1", "Private band label", 100_000, 1_000, 8f, 100_000,
            "INCLUDE:902000000-928000000",
        ),
    )

    private fun captureArtifacts(iq: File): List<File> {
        val id = iq.nameWithoutExtension
        val sidecar = File(iq.parentFile, "$id.json").apply { writeText(validCaptureSidecar(iq, id, "1234abcd", "capture note")) }
        val preview = File(iq.parentFile, "$id-preview.pgm").apply { writeBytes("P5\n1 1\n255\n\u0000".toByteArray()) }
        return listOf(iq, sidecar, preview)
    }

    private fun validCaptureSidecar(
        iq: File,
        id: String,
        serial: String?,
        note: String,
        location: Boolean = false,
    ): String {
        val bytes = iq.length()
        val locationJson = if (location) {
            """{"crs":"EPSG:4326","latitude":42.123456,"longitude":-71.654321,"horizontalAccuracyM":4.0,"ageMs":0}"""
        } else "null"
        return """{"schemaVersion":"1.0.0","id":"$id","fingerprintId":null,"surveyId":"survey-1","sampleFormat":"signed-int8-interleaved-iq","componentOrder":"I,Q","byteOrder":"not-applicable-single-byte-components","centerFrequencyHz":433920000,"sampleRateHz":2000000,"requestedDurationMs":1000,"startedAtEpochMs":1,"startedMonotonicNs":1,"expectedByteCount":$bytes,"actualByteCount":$bytes,"complexSampleCount":${bytes / 2},"gapCount":0,"overrunCount":0,"sha256":"${iq.inputStream().use(::sha256Hex)}","radio":{"basebandFilterHz":1750000,"lnaGainDb":16,"vgaGainDb":16,"rfAmpEnabled":false,"antennaPowerEnabled":false},"equipmentProfileVersionId":"equipment:v1","deviceSerialSuffix":${serial?.let { "\"$it\"" } ?: "null"},"location":$locationJson,"note":"$note","versions":{"app":"test","detector":"m2-detector-v1","clustering":"m2-fingerprint-v1","aggregation":"m3-grid-v1","profile":"equipment:v1"}}"""
    }
}
