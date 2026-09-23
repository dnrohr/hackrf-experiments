package dev.rfnotebook.storage

import java.io.File

data class InterruptedArtifactRecoveryReport(
    val partialFilesRemoved: Int,
    val expiredExportFilesRemoved: Int,
    val stagingDirectoriesRemoved: Int,
)

data class InterruptedCaptureRecoveryReport(
    val completedCaptureIds: List<String>,
    val failedCaptureIds: List<String>,
)

/**
 * Removes only known temporary artifacts below explicitly supplied app-private
 * directories. Final captures and committed imports are never candidates.
 * Reviewed share-cache exports expire after 24 hours; they are transient
 * copies, not the authoritative survey or capture records.
 */
object InterruptedArtifactRecovery {
    /**
     * Resolves the narrow process-death window between the capture file commit
     * marker and the Room COMPLETE transition. A final IQ file is accepted only
     * when its whole artifact group is present, byte-exact, and hash-consistent
     * with the atomically written sidecar. Otherwise only that capture's known
     * app-private files are removed and its durable row is marked FAILED.
     */
    suspend fun recoverCaptures(
        dao: NotebookDao,
        captureDirectory: File,
    ): InterruptedCaptureRecoveryReport {
        val completed = mutableListOf<String>()
        val failed = mutableListOf<String>()
        dao.interruptedIqCaptures().forEach { capture ->
            val artifacts = captureArtifacts(captureDirectory, capture.id)
            val valid = artifacts != null &&
                artifacts.iq.isFile && artifacts.sidecar.isFile && artifacts.preview.isFile &&
                artifacts.iq.length() == capture.expectedByteCount &&
                runCatching {
                    val hash = artifacts.iq.inputStream().use(::sha256Hex)
                    val sidecar = CaptureSidecarValidator.validate(artifacts.sidecar.readText(Charsets.UTF_8))
                    sidecar.id == capture.id &&
                        sidecar.expectedByteCount == capture.expectedByteCount &&
                        sidecar.actualByteCount == capture.expectedByteCount &&
                        sidecar.complexSampleCount == capture.expectedByteCount / 2L &&
                        sidecar.sha256 == hash &&
                        dao.completeIqCapture(capture.id, capture.expectedByteCount, capture.expectedByteCount / 2L, hash) == 1
                }.getOrDefault(false)
            if (valid) {
                completed += capture.id
            } else {
                artifacts?.all?.forEach { it.delete() }
                dao.failIqCapture(capture.id)
                failed += capture.id
            }
        }
        return InterruptedCaptureRecoveryReport(completed, failed)
    }

    fun clean(
        captureDirectory: File,
        exportDirectory: File,
        importCacheDirectory: File,
        importedBundleDirectory: File,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): InterruptedArtifactRecoveryReport {
        var files = 0
        var expiredExports = 0
        var directories = 0

        listOf(captureDirectory, exportDirectory).forEach { root ->
            root.listFiles()?.filter { it.isFile && it.name.endsWith(".part") }?.forEach {
                if (it.delete()) files++
            }
        }
        val captureIds = captureDirectory.listFiles()?.mapNotNull { file ->
            when {
                file.name.endsWith(".cs8") -> file.name.removeSuffix(".cs8")
                file.name.endsWith(".json") -> file.name.removeSuffix(".json")
                file.name.endsWith("-preview.pgm") -> file.name.removeSuffix("-preview.pgm")
                else -> null
            }
        }?.toSet().orEmpty()
        captureIds.forEach { id ->
            val artifacts = listOf(
                captureDirectory.resolve("$id.cs8"),
                captureDirectory.resolve("$id.json"),
                captureDirectory.resolve("$id-preview.pgm"),
            )
            if (!artifacts.all(File::isFile)) {
                artifacts.filter(File::isFile).forEach { if (it.delete()) files++ }
            }
        }
        val expiryCutoff = nowEpochMs - EXPORT_TTL_MS
        exportDirectory.listFiles()?.filter {
            it.isFile && it.extension.equals("zip", ignoreCase = true) && it.lastModified() < expiryCutoff
        }?.forEach { if (it.delete()) expiredExports++ }
        importCacheDirectory.listFiles()?.filter {
            it.isFile && (it.name.endsWith(".part") || (it.name.startsWith("incoming-") && it.name.endsWith(".zip")))
        }?.forEach { if (it.delete()) files++ }
        importedBundleDirectory.listFiles()?.filter { it.isDirectory && it.name.endsWith(".part") }?.forEach {
            if (it.deleteRecursively()) directories++
        }
        return InterruptedArtifactRecoveryReport(files, expiredExports, directories)
    }

    private const val EXPORT_TTL_MS = 24L * 60L * 60L * 1_000L

    private data class CaptureArtifacts(val iq: File, val sidecar: File, val preview: File, val all: List<File>)

    private fun captureArtifacts(directory: File, id: String): CaptureArtifacts? {
        if (!id.matches(Regex("[A-Za-z0-9._-]{1,128}"))) return null
        val iq = directory.resolve("$id.cs8")
        val sidecar = directory.resolve("$id.json")
        val preview = directory.resolve("$id-preview.pgm")
        return CaptureArtifacts(
            iq,
            sidecar,
            preview,
            listOf(
                iq, sidecar, preview,
                directory.resolve("$id.cs8.part"),
                directory.resolve("$id.json.part"),
                directory.resolve("$id-preview.pgm.part"),
            ),
        )
    }
}
