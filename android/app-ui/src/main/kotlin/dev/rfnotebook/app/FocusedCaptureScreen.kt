package dev.rfnotebook.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.rfnotebook.radio.api.RxConfig
import dev.rfnotebook.radio.api.SampleSink
import dev.rfnotebook.radio.hackrf.AndroidHackrfRadio
import dev.rfnotebook.radio.hackrf.NativeRadioSession
import dev.rfnotebook.storage.AtomicIqCapture
import dev.rfnotebook.storage.CaptureMetadata
import dev.rfnotebook.storage.CapturePreflight
import dev.rfnotebook.storage.CaptureResult
import dev.rfnotebook.storage.CoordinateMode
import dev.rfnotebook.storage.ExportPolicy
import dev.rfnotebook.storage.IQCaptureEntity
import dev.rfnotebook.storage.NotebookDatabase
import dev.rfnotebook.storage.SurveyBundleContent
import dev.rfnotebook.storage.SurveyBundleExporter
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class FocusedCaptureState(
    val phase: String = "Idle",
    val measuredSamplesPerSecond: Long = 0,
    val relativePowerDbfs: Float? = null,
    val waterfallRows: List<String> = emptyList(),
    val progressBytes: Long = 0,
    val expectedBytes: Long = 0,
    val result: CaptureResult? = null,
    val problem: String? = null,
    val equipment: FocusedCaptureEquipment = FocusedCaptureEquipment(),
    val settingsReady: Boolean = true,
)

data class FocusedCaptureEquipment(
    val versionId: String = "focused-fixed-16db-v1",
    val sampleRateHz: Int = 2_000_000,
    val basebandFilterHz: Int = 1_750_000,
    val lnaGainDb: Int = 16,
    val vgaGainDb: Int = 16,
    val safetyDifference: String? = null,
    val sampleRateLocked: Boolean = false,
)

internal fun FocusedCaptureState.openingPreview(): FocusedCaptureState = copy(
    phase = "Opening receive-only radio",
    measuredSamplesPerSecond = 0,
    relativePowerDbfs = null,
    waterfallRows = emptyList(),
    progressBytes = 0,
    expectedBytes = 0,
    result = null,
    problem = null,
)

class FocusedCaptureController(
    context: Context,
    private val serialSuffix: String,
    private val fingerprintId: String?,
    private val surveyId: String?,
    private val originatingEquipmentProfileVersionId: String?,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(FocusedCaptureState(settingsReady = originatingEquipmentProfileVersionId == null))
    val state: StateFlow<FocusedCaptureState> = mutableState.asStateFlow()
    private var session: NativeRadioSession? = null
    private var operation: Job? = null
    private val lastUiNanos = AtomicLong()
    private val appVersion = appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "unknown"

    init {
        expireOldExports()
        originatingEquipmentProfileVersionId?.let { versionId ->
            scope.launch {
                runCatching {
                    requireNotNull(NotebookDatabase.open(appContext).notebookDao().equipmentProfile(versionId)) {
                        "Originating equipment profile is unavailable"
                    }.also { require(it.sampleRateHz in setOf(2_000_000, 4_000_000, 8_000_000)) {
                        "Originating equipment sample rate is unsupported for focused capture"
                    } }
                }.onSuccess { profile ->
                    val difference = buildList {
                        if (profile.rfAmpEnabled) add("RF amplifier forced off")
                        if (profile.antennaPowerEnabled) add("antenna-port power forced off")
                    }.takeIf { it.isNotEmpty() }?.joinToString("; ")
                    mutableState.value = mutableState.value.copy(
                        equipment = FocusedCaptureEquipment(
                            versionId = profile.versionId,
                            sampleRateHz = profile.sampleRateHz,
                            basebandFilterHz = profile.basebandFilterHz,
                            lnaGainDb = profile.lnaGainDb,
                            vgaGainDb = profile.vgaGainDb,
                            safetyDifference = difference,
                            sampleRateLocked = true,
                        ),
                        settingsReady = true,
                    )
                }.onFailure { failure ->
                    mutableState.value = mutableState.value.copy(settingsReady = false, problem = failure.message)
                }
            }
        }
    }

    fun startPreview(frequencyHz: Long, sampleRateHz: Int) {
        if (!mutableState.value.settingsReady) return
        if (mutableState.value.equipment.sampleRateLocked && sampleRateHz != mutableState.value.equipment.sampleRateHz) {
            mutableState.value = mutableState.value.copy(problem = "Revisit sample rate must match the originating equipment profile")
            return
        }
        replaceOperation {
            stopRadio()
            mutableState.value = mutableState.value.openingPreview()
            runCatching {
                val opened = AndroidHackrfRadio(appContext).open(serialSuffix) as NativeRadioSession
                session = opened
                val config = rxConfig(frequencyHz, sampleRateHz)
                opened.startRx(config, SampleSink(::onPreviewSamples))
                val startStats = opened.stats()
                val startNs = SystemClock.elapsedRealtimeNanos()
                mutableState.value = mutableState.value.copy(phase = "Focused RX preview")
                delay(1_000)
                val elapsed = (SystemClock.elapsedRealtimeNanos() - startNs).coerceAtLeast(1)
                val received = (opened.stats().byteCount - startStats.byteCount).coerceAtLeast(0)
                val measured = received * 1_000_000_000L / elapsed / 2L
                mutableState.value = mutableState.value.copy(measuredSamplesPerSecond = measured)
            }.onFailure { failure ->
                stopRadio()
                mutableState.value = mutableState.value.copy(phase = "Idle", problem = failure.message ?: "Could not start preview")
            }
        }
    }

    fun capture(frequencyHz: Long, sampleRateHz: Int, durationMs: Long, note: String) {
        replaceOperation {
            if (mutableState.value.equipment.sampleRateLocked && sampleRateHz != mutableState.value.equipment.sampleRateHz) {
                mutableState.value = mutableState.value.copy(problem = "Revisit sample rate must match the originating equipment profile")
                return@replaceOperation
            }
            val available = StatFs(appContext.filesDir.absolutePath).availableBytes
            val measured = mutableState.value.measuredSamplesPerSecond
            val preflight = CapturePreflight.assess(durationMs, sampleRateHz, measured, available)
            if (!preflight.allowed) {
                mutableState.value = mutableState.value.copy(problem = preflight.explanation)
                return@replaceOperation
            }
            stopRadio()
            val captureId = UUID.randomUUID().toString()
            val startedAtEpochMs = System.currentTimeMillis()
            val startedMonotonicNs = SystemClock.elapsedRealtimeNanos()
            val captureDirectory = File(appContext.filesDir, "captures")
            val equipment = mutableState.value.equipment
            val location = latestLocation(startedMonotonicNs)
            val metadata = CaptureMetadata(
                id = captureId, fingerprintId = fingerprintId, surveyId = surveyId,
                startedAtEpochMs = startedAtEpochMs, startedMonotonicNs = startedMonotonicNs,
                durationMs = durationMs, centerFrequencyHz = frequencyHz, sampleRateHz = sampleRateHz,
                basebandFilterHz = filterForCapture(sampleRateHz),
                lnaGainDb = equipment.lnaGainDb,
                vgaGainDb = equipment.vgaGainDb,
                rfAmpEnabled = false, antennaPowerEnabled = false,
                equipmentProfileVersionId = equipment.versionId, serialSuffix = serialSuffix,
                latitude = location?.latitude, longitude = location?.longitude,
                horizontalAccuracyM = location?.accuracy, locationAgeMs = location?.ageMs,
                note = note, appVersion = appVersion,
            )
            val dao = NotebookDatabase.open(appContext).notebookDao()
            val writer = try {
                dao.insertIqCapture(IQCaptureEntity(
                    id = captureId, fingerprintId = fingerprintId, surveyId = surveyId,
                    filePath = captureDirectory.resolve("$captureId.cs8").absolutePath,
                    sidecarPath = captureDirectory.resolve("$captureId.json").absolutePath,
                    previewPath = captureDirectory.resolve("$captureId-preview.pgm").absolutePath,
                    startedAtEpochMs = startedAtEpochMs, durationMs = durationMs,
                    centerFrequencyHz = frequencyHz, sampleRateHz = sampleRateHz,
                    sampleFormat = "signed-int8-interleaved-iq", equipmentProfileVersionId = equipment.versionId,
                    locationFixId = null, expectedByteCount = preflight.expectedBytes, actualByteCount = 0,
                    complexSampleCount = 0, gapCount = 0, overrunCount = 0, sha256 = "", notes = note,
                    status = "CAPTURING",
                ))
                AtomicIqCapture(captureDirectory, metadata)
            } catch (failure: Throwable) {
                withContext(NonCancellable) { dao.failIqCapture(captureId) }
                mutableState.value = mutableState.value.copy(
                    phase = "Idle",
                    result = null,
                    problem = failure.message ?: "Capture could not be prepared",
                )
                return@replaceOperation
            }
            var artifactsFinalized = false
            try {
                val done = CompletableDeferred<Unit>()
                val opened = AndroidHackrfRadio(appContext).open(serialSuffix) as NativeRadioSession
                session = opened
                val before = opened.stats()
                mutableState.value = mutableState.value.copy(
                    phase = "Capturing", progressBytes = 0, expectedBytes = preflight.expectedBytes,
                    result = null, problem = null,
                )
                opened.startRx(rxConfig(frequencyHz, sampleRateHz), SampleSink { bytes, _ ->
                    val accepted = writer.append(bytes)
                    val progress = mutableState.value.progressBytes + accepted
                    mutableState.value = mutableState.value.copy(progressBytes = progress)
                    onPreviewSamples(bytes, 0)
                    if (progress >= preflight.expectedBytes) done.complete(Unit)
                })
                withTimeout(durationMs + 5_000L) { done.await() }
                val after = opened.stats()
                if (after.droppedBufferCount > before.droppedBufferCount) writer.recordOverrun(after.droppedBufferCount - before.droppedBufferCount)
                if (after.callbackErrorCount > before.callbackErrorCount) writer.recordGap(after.callbackErrorCount - before.callbackErrorCount)
                opened.stop()
                val result = writer.complete()
                artifactsFinalized = true
                check(dao.completeIqCapture(captureId, result.byteCount, result.complexSampleCount, result.sha256) == 1) {
                    "Capture files were finalized but their index transition must be recovered after restart"
                }
                stopRadio()
                mutableState.value = mutableState.value.copy(phase = "Complete", result = result, problem = null)
            } catch (failure: Throwable) {
                withContext(NonCancellable) {
                    if (!artifactsFinalized) {
                        writer.cancel()
                        dao.failIqCapture(captureId)
                    }
                    stopRadio()
                    mutableState.value = mutableState.value.copy(
                        phase = "Idle",
                        result = null,
                        problem = if (failure is CancellationException) null else failure.message ?: "Capture failed",
                    )
                }
            }
        }
    }

    fun export(policy: ExportPolicy): File {
        val capture = requireNotNull(mutableState.value.result) { "Complete a capture first" }
        val exports = File(appContext.cacheDir, "exports")
        val source = SurveyBundleContent(
            surveyId = "focused-${capture.iqFile.nameWithoutExtension}", surveyName = "Focused IQ capture",
            generatedAtEpochMs = System.currentTimeMillis(), appVersion = appVersion,
            serialSuffix = serialSuffix, notes = "User-reviewed focused capture export",
            observationsCsv = "frequency_hz,latitude,longitude\n",
            routeGeoJson = "{\"type\":\"FeatureCollection\",\"features\":[]}",
            aggregatesGeoJson = "{\"type\":\"FeatureCollection\",\"features\":[]}",
            captureFiles = listOf(capture.iqFile, capture.sidecarFile, capture.previewFile),
        )
        return SurveyBundleExporter.create(File(exports, "rf-field-notebook-${capture.iqFile.nameWithoutExtension}.zip"), source, policy)
    }

    fun stop() = replaceOperation { stopRadio(); mutableState.value = mutableState.value.copy(phase = "Idle") }

    override fun close() {
        operation?.cancel()
        kotlinx.coroutines.runBlocking { operation?.join(); stopRadio() }
        scope.cancel()
    }

    private fun replaceOperation(block: suspend () -> Unit) {
        val previous = operation
        operation = scope.launch {
            previous?.cancelAndJoin()
            block()
        }
    }

    private suspend fun stopRadio() {
        val current = session
        session = null
        if (current != null) {
            runCatching { current.stop() }
            runCatching { current.close() }
        }
    }

    private fun onPreviewSamples(samples: ByteArray, ignoredTimestamp: Long) {
        if (samples.size < 2) return
        val now = SystemClock.elapsedRealtimeNanos()
        val previous = lastUiNanos.get()
        if (now - previous < 125_000_000L || !lastUiNanos.compareAndSet(previous, now)) return
        var sum = 0.0
        var count = 0
        var index = 0
        while (index + 1 < samples.size && count < 4_096) {
            val i = samples[index].toDouble()
            val q = samples[index + 1].toDouble()
            sum += i * i + q * q
            count++
            index += 2
        }
        val rms = sqrt(sum / count.coerceAtLeast(1))
        val dbfs = (20.0 * log10((rms / 128.0).coerceAtLeast(1e-6))).toFloat()
        val levels = " .:-=+*#%@"
        val row = buildString {
            repeat(48) { column ->
                val pair = ((column.toLong() * count / 48).toInt().coerceAtMost(count - 1)) * 2
                val magnitude = kotlin.math.abs(samples[pair].toInt()) + kotlin.math.abs(samples[pair + 1].toInt())
                append(levels[(magnitude * levels.lastIndex / 64).coerceIn(0, levels.lastIndex)])
            }
        }
        val current = mutableState.value
        mutableState.value = current.copy(relativePowerDbfs = dbfs, waterfallRows = (current.waterfallRows + row).takeLast(12))
    }

    private fun rxConfig(frequencyHz: Long, sampleRateHz: Int) = RxConfig(
        centerFrequencyHz = frequencyHz, sampleRateHz = sampleRateHz,
        basebandFilterHz = filterForCapture(sampleRateHz),
        lnaGainDb = mutableState.value.equipment.lnaGainDb,
        vgaGainDb = mutableState.value.equipment.vgaGainDb,
        rfAmpEnabled = false, antennaPowerEnabled = false,
    )

    private fun filterForCapture(rate: Int): Int = mutableState.value.equipment
        .takeIf { it.sampleRateHz == rate }
        ?.basebandFilterHz
        ?: filterFor(rate)

    private fun latestLocation(nowMonotonicNs: Long): CaptureLocation? {
        val permitted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!permitted) return null
        val manager = appContext.getSystemService(LocationManager::class.java)
        val location = runCatching {
            manager.getProviders(true).mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }.maxByOrNull { it.elapsedRealtimeNanos }
        }.getOrNull() ?: return null
        return CaptureLocation(
            location.latitude,
            location.longitude,
            location.accuracy.takeIf { location.hasAccuracy() },
            ((nowMonotonicNs - location.elapsedRealtimeNanos).coerceAtLeast(0L) / 1_000_000L),
        )
    }

    private fun expireOldExports() {
        val cutoff = System.currentTimeMillis() - EXPORT_TTL_MS
        File(appContext.cacheDir, "exports").listFiles()?.filter { it.lastModified() < cutoff }?.forEach(File::delete)
    }

    private fun filterFor(rate: Int) = when (rate) { 2_000_000 -> 1_750_000; 4_000_000 -> 3_500_000; else -> 7_000_000 }
    private companion object { const val EXPORT_TTL_MS = 24L * 60 * 60 * 1_000 }
}

private data class CaptureLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val ageMs: Long,
)

@Composable
fun FocusedCaptureScreen(
    controller: FocusedCaptureController,
    initialFrequencyHz: Long,
    onBack: () -> Unit,
    onShare: (File) -> Unit,
) {
    val state by controller.state.collectAsState()
    var frequency by remember(initialFrequencyHz) { mutableStateOf(initialFrequencyHz.toString()) }
    var rate by remember { mutableStateOf(2_000_000) }
    var durationMs by remember { mutableStateOf(2_000L) }
    var note by remember { mutableStateOf("") }
    var includeIq by remember { mutableStateOf(true) }
    var includeRoutes by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf(CoordinateMode.OMITTED) }
    var includeNotes by remember { mutableStateOf(false) }
    var includeIdentifiers by remember { mutableStateOf(false) }
    DisposableEffect(controller) { onDispose { controller.close() } }
    LaunchedEffect(state.settingsReady, state.equipment.sampleRateHz) {
        if (state.settingsReady) rate = state.equipment.sampleRateHz
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Focused receive and IQ capture", style = MaterialTheme.typography.titleLarge)
            Text("RX only • RF amplifier off • antenna-port power off")
            Text(
                "Capture profile: ${state.equipment.versionId}; LNA ${state.equipment.lnaGainDb} dB; " +
                    "VGA ${state.equipment.vgaGainDb} dB; filter ${state.equipment.basebandFilterHz} Hz",
            )
            state.equipment.safetyDifference?.let { Text("Profile difference: $it", color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(frequency, { frequency = it.filter(Char::isDigit) }, label = { Text("Center frequency (Hz)") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2_000_000, 4_000_000, 8_000_000).forEach { value ->
                    OutlinedButton(
                        enabled = !state.equipment.sampleRateLocked,
                        onClick = { rate = value },
                    ) { Text("${value / 1_000_000} MS/s${if (rate == value) " ✓" else ""}") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(250L, 1_000L, 2_000L, 5_000L, 30_000L).forEach { value ->
                    OutlinedButton(onClick = { durationMs = value }) { Text(if (value < 1_000) "0.25 s" else "${value / 1_000} s") }
                }
            }
            val expected = runCatching { CapturePreflight.expectedBytes(durationMs, rate) }.getOrDefault(0)
            Text("Preflight: ${expected / 1_000_000.0} MB; measured USB ${state.measuredSamplesPerSecond / 1_000_000.0} MS/s; storage reserve 256 MiB")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = state.settingsReady, onClick = { controller.startPreview(frequency.toLongOrNull() ?: 0, rate) }) { Text("Start focused preview") }
                OutlinedButton(onClick = controller::stop) { Text("Stop / idle") }
            }
            Text("${state.phase}${state.relativePowerDbfs?.let { " • %.1f dBFS relative".format(it) } ?: ""}")
            Text("Narrow waterfall (bounded 8 Hz; brighter characters are stronger)")
            Text(state.waterfallRows.joinToString("\n").ifBlank { "Waiting for RX samples…" })
            OutlinedTextField(
                note,
                { note = it.take(4_096) },
                label = { Text("Capture note (${note.length}/4096)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = state.settingsReady && state.measuredSamplesPerSecond >= rate * 95L / 100L,
                onClick = { controller.capture(frequency.toLongOrNull() ?: 0, rate, durationMs, note) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Capture ${durationMs / 1_000.0} s manually") }
            if (state.expectedBytes > 0) Text("Written ${state.progressBytes} / ${state.expectedBytes} bytes")
            state.problem?.let { Text("Capture problem: $it", color = MaterialTheme.colorScheme.error) }
            state.result?.let { result ->
                Text("Complete: ${result.byteCount} bytes • ${result.sha256.take(16)}… • sidecar + PGM preview")
                Text("Review export contents before Android sharing")
                Toggle("Include IQ, sidecar, and preview", includeIq) { includeIq = it }
                Toggle("Include route", includeRoutes) { includeRoutes = it }
                Toggle("Include notes", includeNotes) { includeNotes = it }
                Toggle("Include device identifier suffix", includeIdentifiers) { includeIdentifiers = it }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CoordinateMode.entries.forEach { mode -> OutlinedButton(onClick = { coordinates = mode }, modifier = Modifier.fillMaxWidth()) {
                        Text("${mode.name.lowercase()} coordinates${if (coordinates == mode) " ✓" else ""}")
                    } }
                }
                Text("Share summary: IQ=$includeIq, route=$includeRoutes, coordinates=${coordinates.name.lowercase()}, notes=$includeNotes, identifiers=$includeIdentifiers")
                Button(onClick = {
                    onShare(controller.export(ExportPolicy(includeIq, includeRoutes, coordinates, includeNotes, includeIdentifiers)))
                }, modifier = Modifier.fillMaxWidth()) { Text("Create reviewed bundle and open share sheet") }
            }
            OutlinedButton(onClick = { controller.stop(); onBack() }) { Text("Back (return radio to idle)") }
        }
    }
}

@Composable private fun Toggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    ReviewedExportOption(label, checked, onChecked)
}
