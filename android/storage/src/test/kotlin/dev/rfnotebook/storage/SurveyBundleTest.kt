package dev.rfnotebook.storage

import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyBundleTest {
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

    private fun validManifest(schema: String = "1.0.0", files: String = "[]") =
        "{\"schemaVersion\":\"$schema\",\"files\":$files}"

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
        surveyId = "survey-1", surveyName = "Field", generatedAtEpochMs = 1_790_000_000_000,
        appVersion = "0.4.0-m4", serialSuffix = "1234abcd", notes = "private note",
        observationsCsv = "frequency_hz,latitude,longitude\\n433920000,42.123456,-71.654321\\n",
        routeGeoJson = "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[-71.654321,42.123456]}}",
        aggregatesGeoJson = "{\"type\":\"FeatureCollection\",\"features\":[]}", captureFiles = listOf(iq),
    )
}
