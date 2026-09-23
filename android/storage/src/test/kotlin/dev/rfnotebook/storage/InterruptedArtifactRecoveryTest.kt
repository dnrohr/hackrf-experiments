package dev.rfnotebook.storage

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterruptedArtifactRecoveryTest {
    @Test fun `process death cleanup removes only temporary artifacts`() {
        val root = Files.createTempDirectory("rfnotebook-recovery").toFile()
        try {
            val captures = root.resolve("captures").apply { mkdirs() }
            val exports = root.resolve("cache/exports").apply { mkdirs() }
            val importCache = root.resolve("cache/imports").apply { mkdirs() }
            val imports = root.resolve("imports").apply { mkdirs() }
            val capturePart = captures.resolve("capture.cs8.part").apply { writeBytes(byteArrayOf(1)) }
            val interruptedSidecar = captures.resolve("interrupted.json").apply { writeText("{}") }
            val interruptedPreview = captures.resolve("interrupted-preview.pgm").apply { writeBytes(byteArrayOf(1)) }
            val completeIq = captures.resolve("complete.cs8").apply { writeBytes(byteArrayOf(1, 2)) }
            val completeSidecar = captures.resolve("complete.json").apply { writeText("{}") }
            val completePreview = captures.resolve("complete-preview.pgm").apply { writeBytes(byteArrayOf(1)) }
            val finalExport = exports.resolve("reviewed.zip").apply { writeBytes(byteArrayOf(3)) }
            val expiredExport = exports.resolve("expired.zip").apply {
                writeBytes(byteArrayOf(2))
                setLastModified(1_000L)
            }
            val exportPart = exports.resolve("reviewed.zip.part").apply { writeBytes(byteArrayOf(4)) }
            val incoming = importCache.resolve("incoming-test.zip").apply { writeBytes(byteArrayOf(5)) }
            val committedImport = imports.resolve("committed").apply { mkdirs(); resolve("manifest.json").writeText("{}") }
            val stagedImport = imports.resolve("interrupted.part").apply { mkdirs(); resolve("manifest.json").writeText("{}") }

            val report = InterruptedArtifactRecovery.clean(
                captures, exports, importCache, imports,
                nowEpochMs = 24L * 60L * 60L * 1_000L + 2_000L,
            )

            assertEquals(5, report.partialFilesRemoved)
            assertEquals(1, report.expiredExportFilesRemoved)
            assertEquals(1, report.stagingDirectoriesRemoved)
            assertTrue(finalExport.isFile)
            assertFalse(expiredExport.exists())
            assertTrue(committedImport.isDirectory)
            assertFalse(capturePart.exists())
            assertFalse(interruptedSidecar.exists())
            assertFalse(interruptedPreview.exists())
            assertTrue(completeIq.isFile)
            assertTrue(completeSidecar.isFile)
            assertTrue(completePreview.isFile)
            assertFalse(exportPart.exists())
            assertFalse(incoming.exists())
            assertFalse(stagedImport.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
