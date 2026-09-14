package dev.rfnotebook.app

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.StatFs
import android.os.SystemClock
import android.location.LocationManager
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.rfnotebook.acquisition.AcquisitionSpikeService
import dev.rfnotebook.acquisition.StorageGuard
import dev.rfnotebook.acquisition.SurveyAcquisitionService
import dev.rfnotebook.acquisition.SurveyAcquisitionState
import dev.rfnotebook.acquisition.SurveyAcquisitionStatus
import dev.rfnotebook.domain.SurveyStatus
import dev.rfnotebook.storage.NotebookDatabase
import dev.rfnotebook.storage.NotebookSetupRepository
import dev.rfnotebook.storage.SurveyLaunch
import dev.rfnotebook.storage.SurveySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppPage { SETUP, PREFLIGHT, ACTIVE, SUMMARY }

class MainActivity : ComponentActivity() {
    private var usbPermissionState by mutableStateOf("unknown")
    private var usbTopologyRevision by mutableIntStateOf(0)
    private var pendingLaunch: SurveyLaunch? = null
    private var pendingLegacyTest = false

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_USB_PERMISSION) {
                usbPermissionState = if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) "granted" else "denied"
            }
        }
    }
    private val usbTopologyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED || intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                usbPermissionState = "unknown"
                usbTopologyRevision++
            }
        }
    }
    private val locationLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            if (pendingLegacyTest) {
                pendingLegacyTest = false
                ContextCompat.startForegroundService(this, Intent(this, AcquisitionSpikeService::class.java))
            } else pendingLaunch?.let(::startSurveyService)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(this, usbPermissionReceiver, IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, usbTopologyReceiver, IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }, ContextCompat.RECEIVER_EXPORTED)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { NotebookScreen() } } }
    }

    override fun onDestroy() {
        unregisterReceiver(usbPermissionReceiver)
        unregisterReceiver(usbTopologyReceiver)
        super.onDestroy()
    }

    @Composable private fun NotebookScreen() {
        val usb = getSystemService(UsbManager::class.java)
        val hackrf = remember(usbTopologyRevision) { findHackrf(usb) }
        val permitted = hackrf != null && usb.hasPermission(hackrf)
        val permissionLabel = when {
            hackrf == null -> "not attached"
            permitted -> "granted"
            usbPermissionState == "unknown" -> "required"
            else -> usbPermissionState
        }
        val serialSuffix = if (permitted) runCatching { hackrf?.serialNumber?.takeLast(8) }.getOrNull() else null
        var page by remember { mutableStateOf(AppPage.SETUP) }
        var band by remember { mutableStateOf("902–928 MHz") }
        var rate by remember { mutableIntStateOf(4_000_000) }
        var lna by remember { mutableIntStateOf(16) }
        var vga by remember { mutableIntStateOf(16) }
        var launch by remember { mutableStateOf<SurveyLaunch?>(null) }
        var recoverable by remember { mutableStateOf<SurveyLaunch?>(null) }
        var summary by remember { mutableStateOf<SurveySummary?>(null) }
        var problem by remember { mutableStateOf<String?>(null) }
        val acquisition by SurveyAcquisitionStatus.state.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        val storage = remember(band) {
            StorageGuard.assess(StatFs(filesDir.absolutePath).availableBytes, estimateSurveyBytes(band))
        }
        LaunchedEffect(Unit) {
            recoverable = withContext(Dispatchers.IO) {
                NotebookSetupRepository(NotebookDatabase.open(this@MainActivity).notebookDao()).recoverableLaunch()
            }
        }
        LaunchedEffect(acquisition.status) {
            if (page == AppPage.ACTIVE && acquisition.status == SurveyStatus.COMPLETE) page = AppPage.SUMMARY
        }

        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("RF Field Notebook", style = MaterialTheme.typography.headlineSmall)
            Text("Receive only • observed power is relative, not calibrated")
            when (page) {
                AppPage.SETUP -> SetupPage(
                    hackrf, usb, permissionLabel, serialSuffix, band, { band = it }, rate, { rate = it },
                    lna, { lna = it }, vga, { vga = it }, recoverable,
                    onRecover = { value ->
                        launch = value
                        pendingLaunch = value
                        locationLauncher.launch(SURVEY_PERMISSIONS)
                        page = AppPage.ACTIVE
                    },
                    onPreflight = { page = AppPage.PREFLIGHT },
                )
                AppPage.PREFLIGHT -> PreflightPage(serialSuffix, band, rate, lna, vga, storage.canStart, storage.explanation,
                    gpsStatus(),
                    onBack = { page = AppPage.SETUP },
                    onStart = {
                        val selectedDevice = hackrf
                        val suffix = serialSuffix
                        if (suffix != null && selectedDevice != null) coroutineScope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    NotebookSetupRepository(NotebookDatabase.open(this@MainActivity).notebookDao()).createStarterSurvey(
                                        suffix, selectedDevice.productName ?: "HackRF", System.currentTimeMillis(),
                                        SystemClock.elapsedRealtimeNanos(), "$band field survey", band, rate, lna, vga,
                                    )
                                }
                            }.onSuccess {
                                launch = it
                                pendingLaunch = it
                                locationLauncher.launch(SURVEY_PERMISSIONS)
                                page = AppPage.ACTIVE
                            }.onFailure { problem = it.message }
                        }
                    },
                )
                AppPage.ACTIVE -> ActivePage(acquisition,
                    onPause = { serviceAction(SurveyAcquisitionService.ACTION_PAUSE) },
                    onResume = { serviceAction(SurveyAcquisitionService.ACTION_RESUME) },
                    onStop = { serviceAction(SurveyAcquisitionService.ACTION_STOP) },
                )
                AppPage.SUMMARY -> {
                    LaunchedEffect(launch?.surveyId, acquisition.status) {
                        launch?.surveyId?.let { id -> summary = withContext(Dispatchers.IO) {
                            NotebookSetupRepository(NotebookDatabase.open(this@MainActivity).notebookDao()).summary(id)
                        } }
                    }
                    SummaryPage(summary) { page = AppPage.SETUP }
                }
            }
            problem?.let { Text("Problem: $it", color = MaterialTheme.colorScheme.error) }
        }
    }

    @Composable private fun SetupPage(
        hackrf: UsbDevice?, usb: UsbManager, permissionLabel: String, serialSuffix: String?,
        band: String, onBand: (String) -> Unit, rate: Int, onRate: (Int) -> Unit,
        lna: Int, onLna: (Int) -> Unit, vga: Int, onVga: (Int) -> Unit,
        recoverable: SurveyLaunch?, onRecover: (SurveyLaunch) -> Unit, onPreflight: () -> Unit,
    ) {
        recoverable?.let { value ->
            Section("Interrupted survey") {
                Text("A prior survey can be recovered paused. Its interruption will be recorded as a gap.")
                Button(enabled = serialSuffix == value.serialSuffix, onClick = { onRecover(value) }) { Text("Recover survey") }
            }
        }
        Section("Connection") {
            Text("USB permission: $permissionLabel")
            Text("Device: ${hackrf?.productName ?: "—"}; serial suffix: ${serialSuffix?.let { "…$it" } ?: "—"}")
            if (hackrf != null && !usb.hasPermission(hackrf)) Button(onClick = { requestUsbPermission(usb, hackrf) }) {
                Text("Request USB permission")
            }
            OutlinedButton(enabled = serialSuffix != null, onClick = {
                pendingLegacyTest = true
                locationLauncher.launch(SURVEY_PERMISSIONS)
            }) { Text("Run compatibility receive test") }
        }
        Section("Equipment profile v1") {
            Text("Uncalibrated antenna • adapter notes remain local")
            Text("RF amplifier: Off • antenna-port power: Off")
            GainPicker("LNA gain", lna, 0..40 step 8, onLna)
            GainPicker("VGA gain", vga, 0..62 step 2, onVga)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2_000_000, 4_000_000, 8_000_000).forEach { sampleRate ->
                    OutlinedButton(onClick = { onRate(sampleRate) }) {
                        Text("${sampleRate / 1_000_000}M${if (sampleRate == rate) " ✓" else ""}")
                    }
                }
            }
            Text("After a survey references v1, an edit creates v2.")
        }
        Section("Band profile") {
            STARTER_BANDS.forEach { name -> OutlinedButton(onClick = { onBand(name) }, Modifier.fillMaxWidth()) {
                Text("$name${if (name == band) " ✓" else ""}")
            } }
            Text("Exploration aids only; these profiles do not authorize transmission.")
        }
        Button(enabled = serialSuffix != null, onClick = onPreflight, modifier = Modifier.fillMaxWidth()) { Text("Survey preflight") }
    }

    @Composable private fun PreflightPage(
        serialSuffix: String?, band: String, rate: Int, lna: Int, vga: Int,
        storageOkay: Boolean, storageExplanation: String, gpsStatus: String,
        onBack: () -> Unit, onStart: () -> Unit,
    ) {
        Section("Preflight") {
            Text("HackRF: ${serialSuffix?.let { "…$it ready" } ?: "not ready"}")
            Text("$band • ${rate / 1_000_000} MS/s • LNA $lna dB • VGA $vga dB")
            Text("Estimated cycle: ${estimatedCycleMs(band)} ms from measured M0 capacity")
            Text("GPS: $gpsStatus")
            Text("Stale and missing fixes remain visible and unlocated observations are preserved.")
            Text("Storage: $storageExplanation")
            Text("Survey mode stores aggregates—not continuous wideband IQ.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(enabled = serialSuffix != null && storageOkay, onClick = onStart) { Text("Start visible survey") }
        }
    }

    @Composable private fun ActivePage(state: SurveyAcquisitionState, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit) {
        Section("Active survey") {
            Text("State: ${state.status ?: "preparing"}")
            Text("Device: ${state.device}")
            Text("USB: ${"%.2f".format(state.usbBytesPerSecond / 1_000_000.0)} MB/s")
            Text("Aggregates persisted: ${state.aggregatesPersisted}")
            Text("GPS accuracy: ${state.locationAccuracyM?.let { "%.1f m".format(it) } ?: "missing"}")
            state.health?.let { health ->
                Text("Queues native/processing/disk: ${health.nativeQueueDepth}/${health.processingQueueDepth}/${health.persistenceQueueDepth}")
                Text("Drops: ${health.droppedNativeUnits + health.droppedProcessingUnits + health.droppedPersistenceUnits}; overruns: ${health.overrunCount}; malformed: ${health.malformedFrameCount}")
            }
            state.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPause) { Text("Pause") }
            Button(onClick = onResume) { Text("Resume") }
            Button(onClick = onStop) { Text("Stop") }
        }
    }

    @Composable private fun SummaryPage(summary: SurveySummary?, onNew: () -> Unit) {
        Section("Survey summary") {
            if (summary == null) Text("Finalizing persisted results…") else {
                Text("Status: ${summary.survey.status}")
                Text("Spectrum aggregates: ${summary.aggregateCount}")
                Text("Location fixes: ${summary.locationFixCount}")
                Text("Explicit acquisition gaps: ${summary.gapCount}")
                Text("Unlocated observations remain stored as MISSING or STALE.")
                Text("Drops: ${summary.survey.droppedFrameCount}; overruns: ${summary.survey.overrunCount}")
            }
        }
        Button(onClick = onNew) { Text("New survey") }
    }

    @Composable private fun Section(title: String, content: @Composable () -> Unit) {
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        } }
    }

    @Composable private fun GainPicker(label: String, value: Int, range: IntProgression, onValue: (Int) -> Unit) {
        Text("$label: $value dB")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = value > range.first, onClick = { onValue((value - range.step).coerceAtLeast(range.first)) }) { Text("−") }
            OutlinedButton(enabled = value < range.last, onClick = { onValue((value + range.step).coerceAtMost(range.last)) }) { Text("+") }
        }
    }

    private fun requestUsbPermission(usb: UsbManager, device: UsbDevice) {
        val pending = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        usb.requestPermission(device, pending)
    }

    private fun startSurveyService(value: SurveyLaunch) {
        ContextCompat.startForegroundService(this, Intent(this, SurveyAcquisitionService::class.java)
            .setAction(SurveyAcquisitionService.ACTION_START)
            .putExtra(SurveyAcquisitionService.EXTRA_SURVEY_ID, value.surveyId)
            .putExtra(SurveyAcquisitionService.EXTRA_SERIAL_SUFFIX, value.serialSuffix)
            .putExtra(SurveyAcquisitionService.EXTRA_START_FREQUENCY_HZ, value.startFrequencyHz)
            .putExtra(SurveyAcquisitionService.EXTRA_END_FREQUENCY_HZ, value.endFrequencyHz)
            .putExtra(SurveyAcquisitionService.EXTRA_BIN_WIDTH_HZ, value.binWidthHz)
            .putExtra(SurveyAcquisitionService.EXTRA_SAMPLE_RATE_HZ, value.sampleRateHz))
    }

    private fun serviceAction(action: String) {
        ContextCompat.startForegroundService(this, Intent(this, SurveyAcquisitionService::class.java).setAction(action))
    }

    private fun findHackrf(usb: UsbManager) = usb.deviceList.values.firstOrNull {
        it.vendorId == HACKRF_VENDOR_ID && it.productId in HACKRF_PRODUCT_IDS
    }

    private fun gpsStatus(): String {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return "permission will be requested at Start"
        val manager = getSystemService(LocationManager::class.java)
        val provider = if (fine) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        if (!manager.isProviderEnabled(provider)) return "$provider provider disabled"
        val last = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            ?: return "$provider enabled; waiting for a fix"
        val ageMs = (SystemClock.elapsedRealtimeNanos() - last.elapsedRealtimeNanos).coerceAtLeast(0) / 1_000_000
        return "$provider fix ±${"%.1f".format(last.accuracy)} m, age ${ageMs / 1_000}s"
    }

    private fun estimateSurveyBytes(band: String) = bandWidthHz(band) / 100_000L * 1_800L * 128L
    private fun estimatedCycleMs(band: String) = (bandWidthHz(band) / 100_000L / 2L).coerceAtLeast(1)
    private fun bandWidthHz(band: String) = when (band) {
        "315 MHz", "433 MHz" -> 2_000_000L
        "150–174 MHz" -> 24_000_000L
        "450–470 MHz" -> 20_000_000L
        else -> 26_000_000L
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "dev.rfnotebook.action.USB_PERMISSION"
        private const val HACKRF_VENDOR_ID = 0x1d50
        private val HACKRF_PRODUCT_IDS = setOf(0x6089, 0x604b, 0xcc15)
        private val STARTER_BANDS = listOf("315 MHz", "433 MHz", "150–174 MHz", "450–470 MHz", "902–928 MHz")
        private val SURVEY_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }
}
