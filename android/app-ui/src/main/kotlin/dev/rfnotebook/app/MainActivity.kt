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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import dev.rfnotebook.acquisition.CompatibilityReceiveService
import dev.rfnotebook.acquisition.CompatibilityReceiveState
import dev.rfnotebook.acquisition.CompatibilityReceiveStatus
import dev.rfnotebook.acquisition.StorageGuard
import dev.rfnotebook.acquisition.SurveyAcquisitionService
import dev.rfnotebook.acquisition.SurveyAcquisitionState
import dev.rfnotebook.acquisition.SurveyAcquisitionStatus
import dev.rfnotebook.domain.SurveyStatus
import dev.rfnotebook.domain.FrequencyText
import dev.rfnotebook.domain.FrequencyRange
import dev.rfnotebook.domain.EquipmentProfile
import dev.rfnotebook.storage.NotebookDatabase
import dev.rfnotebook.storage.NotebookSetupRepository
import dev.rfnotebook.storage.DiscoveryRepository
import dev.rfnotebook.storage.DiscoveryDetail
import dev.rfnotebook.storage.SignalFingerprintEntity
import dev.rfnotebook.storage.SurveyLaunch
import dev.rfnotebook.storage.SurveySummary
import dev.rfnotebook.storage.SurveyBundleImporter
import dev.rfnotebook.storage.SurveyBundleContentFactory
import dev.rfnotebook.storage.SurveyBundleExporter
import dev.rfnotebook.storage.ExportPolicy
import dev.rfnotebook.storage.CoordinateMode
import dev.rfnotebook.storage.InterruptedArtifactRecovery
import java.io.File
import java.util.UUID
import dev.rfnotebook.domain.FingerprintState
import dev.rfnotebook.maps.MapDataRepository
import dev.rfnotebook.maps.MapDataset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppPage { SETUP, PREFLIGHT, ACTIVE, SUMMARY, DISCOVERIES, DISCOVERY_DETAIL, MAP, CAPTURE }

class MainActivity : ComponentActivity() {
    private var usbPermissionState by mutableStateOf("unknown")
    private var usbTopologyRevision by mutableIntStateOf(0)
    private var pendingLaunch: SurveyLaunch? = null
    private var pendingCompatibilityTest = false
    private var page by mutableStateOf(AppPage.SETUP)
    private var launchProblem by mutableStateOf<String?>(null)
    private var pendingExportFile: File? = null

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
            if (pendingCompatibilityTest) {
                pendingCompatibilityTest = false
                ContextCompat.startForegroundService(this, Intent(this, CompatibilityReceiveService::class.java))
            } else pendingLaunch?.let {
                startSurveyService(it)
                page = AppPage.ACTIVE
            }
        } else {
            pendingCompatibilityTest = false
            launchProblem = "Location permission was denied; the survey was not started."
            page = AppPage.PREFLIGHT
        }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val stagingRoot = File(cacheDir, "imports").apply { mkdirs() }
                val incoming = File(stagingRoot, "incoming-${UUID.randomUUID()}.zip")
                try {
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Could not open the selected bundle" }
                        SurveyBundleImporter.stageIncoming(input, incoming, availableBytes = {
                            StatFs(cacheDir.absolutePath).availableBytes
                        })
                    }
                    val destination = File(filesDir, "imports/${UUID.randomUUID()}")
                    SurveyBundleImporter.import(incoming, destination, availableBytes = {
                        StatFs(filesDir.absolutePath).availableBytes
                    })
                    destination
                } finally {
                    incoming.delete()
                }
            }.onSuccess { imported -> withContext(Dispatchers.Main) {
                launchProblem = "Imported validated bundle ${imported.name}; unsupported or unsafe content is never partially committed."
            } }.onFailure { failure -> withContext(Dispatchers.Main) {
                launchProblem = "Import rejected: ${failure.message}"
            } }
        }
    }
    private val saveExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val source = pendingExportFile
        pendingExportFile = null
        if (uri == null || source == null) return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                contentResolver.openOutputStream(uri, "w").use { output ->
                    requireNotNull(output) { "Could not open the selected export destination" }
                    source.inputStream().buffered().use { it.copyTo(output) }
                }
            }.onSuccess { withContext(Dispatchers.Main) {
                launchProblem = "Saved reviewed bundle ${source.name} to the selected Android document destination."
            } }.onFailure { failure -> withContext(Dispatchers.Main) {
                launchProblem = "Saving export failed safely: ${failure.message}"
            } }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getString(STATE_PAGE)?.let { saved ->
            AppPage.entries.firstOrNull { it.name == saved }
        } ?: AppPage.SETUP
        InterruptedArtifactRecovery.clean(
            File(filesDir, "captures"),
            File(cacheDir, "exports"),
            File(cacheDir, "imports"),
            File(filesDir, "imports"),
        )
        ContextCompat.registerReceiver(this, usbPermissionReceiver, IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED)
        val usbTopologyFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(this, usbTopologyReceiver, usbTopologyFilter, ContextCompat.RECEIVER_EXPORTED)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { NotebookScreen() } } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PAGE, page.name)
        super.onSaveInstanceState(outState)
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
        val serialSuffix = if (permitted) runCatching { hackrf.serialNumber?.takeLast(8) }.getOrNull() else null
        var band by rememberSaveable { mutableStateOf("902–928 MHz") }
        var rate by rememberSaveable { mutableIntStateOf(4_000_000) }
        var lna by rememberSaveable { mutableIntStateOf(16) }
        var vga by rememberSaveable { mutableIntStateOf(16) }
        var antennaName by rememberSaveable { mutableStateOf("Uncalibrated antenna") }
        var adapterNotes by rememberSaveable { mutableStateOf("") }
        var equipmentNotes by rememberSaveable { mutableStateOf("Relative observations only; this profile is not calibrated.") }
        var photoReference by rememberSaveable { mutableStateOf("") }
        var rangeStart by rememberSaveable { mutableStateOf("902 MHz") }
        var rangeEnd by rememberSaveable { mutableStateOf("928 MHz") }
        var additionalRanges by rememberSaveable { mutableStateOf("") }
        var excludedRanges by rememberSaveable { mutableStateOf("") }
        var binWidth by rememberSaveable { mutableIntStateOf(100_000) }
        var targetRevisit by rememberSaveable { mutableStateOf("1000") }
        var thresholdSnr by rememberSaveable { mutableStateOf("8") }
        var minimumBandwidth by rememberSaveable { mutableStateOf("100 kHz") }
        var launch by remember { mutableStateOf<SurveyLaunch?>(null) }
        var summarySurveyId by rememberSaveable { mutableStateOf<String?>(null) }
        var recoverable by remember { mutableStateOf<SurveyLaunch?>(null) }
        var summary by remember { mutableStateOf<SurveySummary?>(null) }
        var problem by remember { mutableStateOf<String?>(null) }
        var exportMessage by remember { mutableStateOf<String?>(null) }
        var discoveryState by remember { mutableStateOf(DiscoveryUiState(DiscoveryPhase.EMPTY)) }
        var discoveryDetail by remember { mutableStateOf<DiscoveryDetail?>(null) }
        var selectedFingerprintId by rememberSaveable { mutableStateOf<String?>(null) }
        var mapDataset by remember { mutableStateOf<MapDataset?>(null) }
        var mappedFingerprintId by rememberSaveable { mutableStateOf<String?>(null) }
        var captureFingerprintId by rememberSaveable { mutableStateOf<String?>(null) }
        var captureSurveyId by rememberSaveable { mutableStateOf<String?>(null) }
        var captureEquipmentProfileVersionId by rememberSaveable { mutableStateOf<String?>(null) }
        var captureFrequencyHz by rememberSaveable { mutableStateOf(433_920_000L) }
        val acquisition by SurveyAcquisitionStatus.state.collectAsState()
        val compatibility by CompatibilityReceiveStatus.state.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        val storage = remember(rangeStart, rangeEnd, additionalRanges, excludedRanges, binWidth) {
            val estimate = runCatching { estimateSurveyBytes(rangeStart, rangeEnd, additionalRanges, excludedRanges, binWidth) }
                .getOrDefault(Long.MAX_VALUE - StorageGuard.DEFAULT_RESERVE_BYTES)
            StorageGuard.assess(StatFs(filesDir.absolutePath).availableBytes, estimate)
        }
        LaunchedEffect(Unit) {
            runCatching {
                withContext(Dispatchers.IO) {
                    val repository = NotebookSetupRepository(NotebookDatabase.open(this@MainActivity).notebookDao())
                    val captureRecovery = InterruptedArtifactRecovery.recoverCaptures(
                        NotebookDatabase.open(this@MainActivity).notebookDao(),
                        File(filesDir, "captures"),
                    )
                    val finalized = repository.recoverInterruptedFinalizations(
                        System.currentTimeMillis(),
                        SystemClock.elapsedRealtimeNanos(),
                    )
                    Triple(captureRecovery, finalized, repository.recoverableLaunch())
                }
            }.onSuccess { (captureRecovery, finalized, resumable) ->
                recoverable = resumable
                if (captureRecovery.completedCaptureIds.isNotEmpty() || captureRecovery.failedCaptureIds.isNotEmpty()) {
                    launchProblem = "Recovered ${captureRecovery.completedCaptureIds.size} finalized capture(s); " +
                        "${captureRecovery.failedCaptureIds.size} interrupted capture(s) were marked failed without partial files."
                }
                finalized.firstOrNull()?.let { surveyId ->
                    summarySurveyId = surveyId
                    launchProblem = "Recovered an interrupted survey finalization; persisted results are complete."
                    page = AppPage.SUMMARY
                }
            }.onFailure { failure ->
                launchProblem = "Interrupted-survey recovery failed safely; stored data was left unchanged: ${failure.message}"
            }
        }
        LaunchedEffect(page, selectedFingerprintId, mappedFingerprintId) {
            when (page) {
                AppPage.DISCOVERIES -> if (discoveryState.phase == DiscoveryPhase.EMPTY) {
                    loadDiscoveries { discoveryState = it }
                }
                AppPage.DISCOVERY_DETAIL -> selectedFingerprintId?.let { id ->
                    if (discoveryDetail?.fingerprint?.id != id) {
                        runCatching { withContext(Dispatchers.IO) {
                            DiscoveryRepository(NotebookDatabase.open(this@MainActivity).notebookDao()).detail(id)
                        } }.onSuccess { discoveryDetail = it }.onFailure {
                            problem = "Could not restore discovery detail: ${it.message}"
                            page = AppPage.DISCOVERIES
                        }
                    }
                }
                AppPage.MAP -> mappedFingerprintId?.let { id ->
                    if (mapDataset == null) {
                        runCatching { withContext(Dispatchers.IO) {
                            val database = NotebookDatabase.open(this@MainActivity)
                            val comparisonIds = DiscoveryRepository(database.notebookDao()).discoveries()
                                .map { it.id }
                            MapDataRepository(database.notebookDao()).load(
                                (listOf(id) + comparisonIds).distinct().take(8).toSet(),
                            )
                        } }.onSuccess { mapDataset = it }.onFailure {
                            problem = "Could not restore map: ${it.message}"
                            page = AppPage.DISCOVERIES
                        }
                    }
                }
                else -> Unit
            }
        }
        LaunchedEffect(acquisition.status) {
            if (page == AppPage.ACTIVE && acquisition.status == SurveyStatus.COMPLETE) page = AppPage.SUMMARY
        }

        Column(
            Modifier.statusBarsPadding().navigationBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("RF Field Notebook", style = MaterialTheme.typography.headlineSmall)
            Text("Receive only • observed power is relative, not calibrated")
            if (page !in setOf(AppPage.DISCOVERIES, AppPage.DISCOVERY_DETAIL, AppPage.MAP, AppPage.CAPTURE)) {
                OutlinedButton(onClick = {
                    discoveryState = DiscoveryUiState(DiscoveryPhase.PROCESSING)
                    page = AppPage.DISCOVERIES
                    coroutineScope.launch { loadDiscoveries { discoveryState = it } }
                }) { Text("Discoveries") }
            }
            when (page) {
                AppPage.SETUP -> SetupPage(
                    hackrf, usb, permissionLabel, serialSuffix, band, { selected ->
                        band = selected
                        val defaults = defaultRangeText(selected)
                        rangeStart = defaults.first
                        rangeEnd = defaults.second
                        additionalRanges = ""
                        excludedRanges = ""
                    }, rate, { rate = it }, lna, { lna = it }, vga, { vga = it },
                    antennaName, { antennaName = it }, adapterNotes, { adapterNotes = it },
                    equipmentNotes, { equipmentNotes = it }, photoReference, { photoReference = it },
                    rangeStart, { rangeStart = it }, rangeEnd, { rangeEnd = it },
                    additionalRanges, { additionalRanges = it }, excludedRanges, { excludedRanges = it },
                    binWidth, { binWidth = it }, targetRevisit, { targetRevisit = it },
                    thresholdSnr, { thresholdSnr = it }, minimumBandwidth, { minimumBandwidth = it }, recoverable,
                    onRecover = { value ->
                        launch = value
                        summarySurveyId = value.surveyId
                        pendingLaunch = value
                        launchProblem = null
                        locationLauncher.launch(SURVEY_PERMISSIONS)
                    },
                    onPreflight = { page = AppPage.PREFLIGHT },
                    onFocusedCapture = {
                        captureFingerprintId = null
                        captureSurveyId = null
                        captureEquipmentProfileVersionId = null
                        captureFrequencyHz = runCatching { FrequencyText.parseHz(rangeStart) }.getOrDefault(433_920_000L)
                        page = AppPage.CAPTURE
                    },
                    onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    compatibility = compatibility,
                    onStopCompatibility = {
                        this@MainActivity.startService(
                            Intent(this@MainActivity, CompatibilityReceiveService::class.java)
                                .setAction(CompatibilityReceiveService.ACTION_STOP),
                        )
                    },
                )
                AppPage.PREFLIGHT -> PreflightPage(serialSuffix, band, rangeStart, rangeEnd, additionalRanges, excludedRanges, binWidth, targetRevisit, thresholdSnr, minimumBandwidth, rate, lna, vga, storage.canStart, storage.explanation,
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
                                        antennaName = antennaName,
                                        adapterNotes = adapterNotes,
                                        equipmentNotes = equipmentNotes,
                                        photoReference = photoReference,
                                        binWidthHz = binWidth.toLong(),
                                        targetRevisitMs = targetRevisit.toLong(),
                                        thresholdSnrDb = thresholdSnr.toFloat(),
                                        minimumBandwidthHz = FrequencyText.parseHz(minimumBandwidth),
                                        includedRanges = configuredRanges(rangeStart, rangeEnd, additionalRanges),
                                        excludedRanges = FrequencyText.parseRanges(excludedRanges),
                                        appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown",
                                    )
                                }
                            }.onSuccess {
                                launch = it
                                summarySurveyId = it.surveyId
                                pendingLaunch = it
                                launchProblem = null
                                locationLauncher.launch(SURVEY_PERMISSIONS)
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
                    LaunchedEffect(summarySurveyId, acquisition.status) {
                        summarySurveyId?.let { id -> summary = withContext(Dispatchers.IO) {
                            NotebookSetupRepository(NotebookDatabase.open(this@MainActivity).notebookDao()).summary(id)
                        } }
                    }
                    val createBundle: suspend (ExportPolicy) -> File = { policy ->
                        val surveyId = requireNotNull(summarySurveyId) { "No completed survey is selected" }
                        withContext(Dispatchers.IO) {
                            val database = NotebookDatabase.open(this@MainActivity)
                            val appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
                            val content = SurveyBundleContentFactory(database.notebookDao()).create(surveyId, appVersion)
                            val directory = File(cacheDir, "exports").apply { mkdirs() }
                            SurveyBundleExporter.create(File(directory, "rf-field-notebook-survey-$surveyId.zip"), content, policy)
                        }
                    }
                    SummaryPage(summary, exportMessage, onNew = { page = AppPage.SETUP }, onShareExport = { policy ->
                        coroutineScope.launch {
                            runCatching { createBundle(policy) }.onSuccess { file ->
                                exportMessage = "Validated bundle ready: ${file.name}. Review the Android destination before sharing."
                                shareBundle(file)
                            }.onFailure { exportMessage = "Export failed safely: ${it.message}" }
                        }
                    }, onSaveExport = { policy ->
                        coroutineScope.launch {
                            runCatching { createBundle(policy) }.onSuccess { file ->
                                exportMessage = "Validated bundle ready: ${file.name}. Choose a local document destination."
                                pendingExportFile = file
                                saveExportLauncher.launch(file.name)
                            }.onFailure { exportMessage = "Export failed safely: ${it.message}" }
                        }
                    }, onProcess = {
                        val surveyId = summarySurveyId ?: return@SummaryPage
                        discoveryState = DiscoveryUiState(DiscoveryPhase.PROCESSING)
                        page = AppPage.DISCOVERIES
                        coroutineScope.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                DiscoveryRepository(NotebookDatabase.open(this@MainActivity).notebookDao()).reprocessSurvey(surveyId)
                            } }.onFailure { discoveryState = DiscoveryUiState(DiscoveryPhase.FAILED, problem = it.message) }
                            if (discoveryState.phase != DiscoveryPhase.FAILED) loadDiscoveries { discoveryState = it }
                        }
                    })
                }
                AppPage.DISCOVERIES -> DiscoveriesPage(
                    discoveryState,
                    onRefresh = { coroutineScope.launch { loadDiscoveries { discoveryState = it } } },
                    onSelect = { fingerprint: SignalFingerprintEntity ->
                        selectedFingerprintId = fingerprint.id
                        discoveryDetail = null
                        page = AppPage.DISCOVERY_DETAIL
                        coroutineScope.launch { discoveryDetail = withContext(Dispatchers.IO) {
                            DiscoveryRepository(NotebookDatabase.open(this@MainActivity).notebookDao()).detail(fingerprint.id)
                        } }
                    },
                    onBack = { page = AppPage.SETUP },
                    onProcessSurvey = { surveyId ->
                        discoveryState = discoveryState.copy(phase = DiscoveryPhase.PROCESSING, problem = null)
                        coroutineScope.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                DiscoveryRepository(NotebookDatabase.open(this@MainActivity).notebookDao()).reprocessSurvey(surveyId)
                            } }.onFailure { discoveryState = discoveryState.copy(phase = DiscoveryPhase.FAILED, problem = it.message) }
                            if (discoveryState.phase != DiscoveryPhase.FAILED) loadDiscoveries { discoveryState = it }
                        }
                    },
                    onOpenSurvey = { surveyId ->
                        summarySurveyId = surveyId
                        summary = null
                        exportMessage = null
                        page = AppPage.SUMMARY
                    },
                    onMerge = { fingerprintIds ->
                        discoveryState = discoveryState.copy(phase = DiscoveryPhase.PROCESSING, problem = null)
                        coroutineScope.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                DiscoveryRepository(NotebookDatabase.open(this@MainActivity).notebookDao())
                                    .mergeFingerprints(fingerprintIds, System.currentTimeMillis())
                            } }.onFailure { discoveryState = discoveryState.copy(phase = DiscoveryPhase.FAILED, problem = it.message) }
                            if (discoveryState.phase != DiscoveryPhase.FAILED) loadDiscoveries { discoveryState = it }
                        }
                    },
                )
                AppPage.DISCOVERY_DETAIL -> DiscoveryDetailPage(discoveryDetail, onState = { state ->
                    val current = discoveryDetail ?: return@DiscoveryDetailPage
                    coroutineScope.launch {
                        discoveryDetail = withContext(Dispatchers.IO) {
                            DiscoveryRepository(NotebookDatabase.open(this@MainActivity).notebookDao()).updateUserFields(
                                current.fingerprint.id, state, current.fingerprint.userLabel,
                                current.fingerprint.tags.split('|').filter { it.isNotBlank() }.toSet(), current.fingerprint.notes,
                            )
                            DiscoveryRepository(NotebookDatabase.open(this@MainActivity).notebookDao()).detail(current.fingerprint.id)
                        }
                    }
                }, onSave = { label, tags, notes ->
                    val current = discoveryDetail ?: return@DiscoveryDetailPage
                    coroutineScope.launch {
                        discoveryDetail = withContext(Dispatchers.IO) {
                            val repository = DiscoveryRepository(NotebookDatabase.open(this@MainActivity).notebookDao())
                            repository.updateUserFields(
                                current.fingerprint.id, FingerprintState.valueOf(current.fingerprint.state), label, tags, notes,
                            )
                            repository.detail(current.fingerprint.id)
                        }
                    }
                }, onBack = { page = AppPage.DISCOVERIES }, onMap = {
                    val current = discoveryDetail ?: return@DiscoveryDetailPage
                    mappedFingerprintId = current.fingerprint.id
                    mapDataset = null
                    page = AppPage.MAP
                    coroutineScope.launch {
                        mapDataset = withContext(Dispatchers.IO) {
                            val ids = (listOf(current.fingerprint.id) + discoveryState.fingerprints.map { it.id })
                                .distinct().take(8).toSet()
                            MapDataRepository(NotebookDatabase.open(this@MainActivity).notebookDao()).load(ids)
                        }
                    }
                }, onCapture = {
                    val current = discoveryDetail ?: return@DiscoveryDetailPage
                    captureFingerprintId = current.fingerprint.id
                    captureEquipmentProfileVersionId = current.fingerprint.equipmentProfileVersionId
                    captureSurveyId = summarySurveyId?.takeIf { preferred ->
                        current.detections.any { it.surveyId == preferred }
                    } ?: current.detections.maxByOrNull { it.endedAtEpochMs }?.surveyId
                    captureFrequencyHz = current.fingerprint.nominalFrequencyHz
                    page = AppPage.CAPTURE
                }, onSplitLast = {
                    val current = discoveryDetail ?: return@DiscoveryDetailPage
                    val moved = current.detections.lastOrNull()?.id ?: return@DiscoveryDetailPage
                    coroutineScope.launch {
                        runCatching { withContext(Dispatchers.IO) {
                            DiscoveryRepository(NotebookDatabase.open(this@MainActivity).notebookDao()).splitFingerprint(
                                current.fingerprint.id, setOf(moved), System.currentTimeMillis(),
                            )
                        } }.onSuccess {
                            page = AppPage.DISCOVERIES
                            loadDiscoveries { discoveryState = it }
                        }.onFailure { problem = it.message }
                    }
                })
                AppPage.MAP -> MapExplorerPage(
                    dataset = mapDataset,
                    initialFingerprintId = mappedFingerprintId,
                    onBack = { page = AppPage.DISCOVERY_DETAIL },
                )
                AppPage.CAPTURE -> if (serialSuffix == null) {
                    Section("Focused capture") {
                        Text("Attach and grant USB permission to a HackRF before focused receive.")
                        OutlinedButton(onClick = { page = AppPage.SETUP }) { Text("Back") }
                    }
                } else {
                    val controller = remember(serialSuffix, captureFingerprintId, captureSurveyId, captureEquipmentProfileVersionId) {
                        FocusedCaptureController(
                            this@MainActivity, serialSuffix, captureFingerprintId, captureSurveyId,
                            captureEquipmentProfileVersionId,
                        )
                    }
                    FocusedCaptureScreen(
                        controller = controller,
                        initialFrequencyHz = captureFrequencyHz,
                        onBack = { page = if (captureFingerprintId == null) AppPage.SETUP else AppPage.DISCOVERY_DETAIL },
                        onShare = ::shareBundle,
                    )
                }
            }
            problem?.let { Text("Problem: $it", color = MaterialTheme.colorScheme.error) }
            launchProblem?.let { Text("Problem: $it", color = MaterialTheme.colorScheme.error) }
        }
    }

    @Composable private fun SetupPage(
        hackrf: UsbDevice?, usb: UsbManager, permissionLabel: String, serialSuffix: String?,
        band: String, onBand: (String) -> Unit, rate: Int, onRate: (Int) -> Unit,
        lna: Int, onLna: (Int) -> Unit, vga: Int, onVga: (Int) -> Unit,
        antennaName: String, onAntennaName: (String) -> Unit, adapterNotes: String, onAdapterNotes: (String) -> Unit,
        equipmentNotes: String, onEquipmentNotes: (String) -> Unit,
        photoReference: String, onPhotoReference: (String) -> Unit,
        rangeStart: String, onRangeStart: (String) -> Unit, rangeEnd: String, onRangeEnd: (String) -> Unit,
        additionalRanges: String, onAdditionalRanges: (String) -> Unit,
        excludedRanges: String, onExcludedRanges: (String) -> Unit,
        binWidth: Int, onBinWidth: (Int) -> Unit,
        targetRevisit: String, onTargetRevisit: (String) -> Unit,
        thresholdSnr: String, onThresholdSnr: (String) -> Unit,
        minimumBandwidth: String, onMinimumBandwidth: (String) -> Unit,
        recoverable: SurveyLaunch?, onRecover: (SurveyLaunch) -> Unit, onPreflight: () -> Unit,
        onFocusedCapture: () -> Unit,
        onImport: () -> Unit,
        compatibility: CompatibilityReceiveState,
        onStopCompatibility: () -> Unit,
    ) {
        Section("Before the first survey") {
            Text("Receive only: this app has no transmit control. RF amplifier and antenna-port power start Off.")
            Text("Choose the antenna and fixed gain deliberately. Results are relative dBFS observations, not calibrated field strength or a transmitter location.")
            Text("Precise locations, device identity, notes, frequencies, and IQ stay in app-private storage until you review and explicitly share an export.")
            Text("Use a powered OTG hub when needed, protect the HackRF input from strong signals, and follow local interception and disclosure law.")
        }
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
                pendingCompatibilityTest = true
                locationLauncher.launch(SURVEY_PERMISSIONS)
            }) { Text("Run compatibility receive test") }
            if (compatibility.phase != "idle" || compatibility.detail.isNotBlank()) {
                Text("Receive test: ${compatibility.phase}")
                if (compatibility.device != "—") Text("Hardware: ${compatibility.device}")
                if (compatibility.sampleRateHz > 0) {
                    Text("Measured: ${"%.2f".format(compatibility.bytesPerSecond / 2_000_000.0)} MS/s; ${formatBytes(compatibility.bytes)} delivered")
                    Text("Dropped buffers: ${compatibility.droppedBuffers}; callback errors: ${compatibility.callbackErrors}")
                }
                if (compatibility.phase in setOf("opening", "receiving", "sweeping")) {
                    OutlinedButton(onClick = onStopCompatibility) { Text("Stop receive test") }
                }
                if (compatibility.detail.isNotBlank()) {
                    Text(
                        compatibility.detail,
                        color = if (compatibility.phase == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Section("Equipment profile v1") {
            OutlinedTextField(antennaName, onAntennaName, label = { Text("Antenna name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(adapterNotes, onAdapterNotes, label = { Text("Connector / adapter notes") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(equipmentNotes, onEquipmentNotes, label = { Text("Equipment notes") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(photoReference, onPhotoReference, label = { Text("Optional local photo reference") }, modifier = Modifier.fillMaxWidth())
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
            Text("Baseband filter: ${EquipmentProfile.recommendedBasebandFilterHz(rate) / 1_000} kHz")
            Text("After a survey references v1, an edit creates v2.")
        }
        Section("Band profile") {
            STARTER_BANDS.forEach { name -> OutlinedButton(onClick = { onBand(name) }, Modifier.fillMaxWidth()) {
                Text("$name${if (name == band) " ✓" else ""}")
            } }
            OutlinedTextField(rangeStart, onRangeStart, label = { Text("Range start (Hz/kHz/MHz/GHz)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(rangeEnd, onRangeEnd, label = { Text("Range end (Hz/kHz/MHz/GHz)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(additionalRanges, onAdditionalRanges, label = { Text("Additional ranges (start-end; …)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(excludedRanges, onExcludedRanges, label = { Text("Excluded ranges (start-end; …)") }, modifier = Modifier.fillMaxWidth())
            Text("Resolution: ${binWidth / 1_000} kHz")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(50_000, 100_000, 200_000).forEach { width -> OutlinedButton(onClick = { onBinWidth(width) }) {
                    Text("${width / 1_000}k${if (width == binWidth) " ✓" else ""}")
                } }
            }
            OutlinedTextField(targetRevisit, onTargetRevisit, label = { Text("Target revisit (ms)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(thresholdSnr, onThresholdSnr, label = { Text("Detector threshold (dB)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(minimumBandwidth, onMinimumBandwidth, label = { Text("Minimum bandwidth (Hz/kHz/MHz)") }, modifier = Modifier.fillMaxWidth())
            Text("Exploration aids only; these profiles do not authorize transmission.")
        }
        Button(enabled = serialSuffix != null, onClick = onPreflight, modifier = Modifier.fillMaxWidth()) { Text("Survey preflight") }
        OutlinedButton(enabled = serialSuffix != null, onClick = onFocusedCapture, modifier = Modifier.fillMaxWidth()) {
            Text("Manual focused RX / IQ capture")
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Import validated survey bundle") }
    }

    @Composable private fun PreflightPage(
        serialSuffix: String?, band: String, rangeStart: String, rangeEnd: String,
        additionalRanges: String, excludedRanges: String, binWidth: Int,
        targetRevisit: String, thresholdSnr: String, minimumBandwidth: String,
        rate: Int, lna: Int, vga: Int,
        storageOkay: Boolean, storageExplanation: String, gpsStatus: String,
        onBack: () -> Unit, onStart: () -> Unit,
    ) {
        Section("Preflight") {
            Text("HackRF: ${serialSuffix?.let { "…$it ready" } ?: "not ready"}")
            Text("$band ($rangeStart–$rangeEnd) • ${binWidth / 1_000} kHz bins")
            if (additionalRanges.isNotBlank()) Text("Additional ranges: $additionalRanges")
            if (excludedRanges.isNotBlank()) Text("Excluded: $excludedRanges")
            Text("${rate / 1_000_000} MS/s • ${EquipmentProfile.recommendedBasebandFilterHz(rate) / 1_000} kHz filter • LNA $lna dB • VGA $vga dB")
            Text("Revisit ${targetRevisit} ms • threshold ${thresholdSnr} dB • minimum bandwidth $minimumBandwidth")
            Text("Estimated cycle: ${runCatching { estimatedCycleMs(rangeStart, rangeEnd, additionalRanges, excludedRanges, binWidth) }.getOrNull()?.let { "$it ms" } ?: "invalid range"} from measured M0 capacity")
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
            Text("Ranges: ${state.configuredRanges}")
            Text("Current subrange: ${state.currentRange}")
            val elapsed = state.startedAtEpochMs?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) }
            Text("Collection time: ${formatDuration(state.activeDurationMs)}; elapsed since start: ${elapsed?.let(::formatDuration) ?: "—"} (includes pauses/gaps); distance: ${"%.2f".format(state.distanceMeters / 1_000.0)} km")
            Text("USB: ${"%.2f".format(state.usbBytesPerSecond / 1_000_000.0)} MB/s")
            Text("Aggregates persisted: ${state.aggregatesPersisted}")
            Text("GPS accuracy: ${state.locationAccuracyM?.let { "%.1f m".format(it) } ?: "missing"}; fix age: ${state.locationFixAgeMs?.let { "${it / 1_000}s" } ?: "—"}")
            state.health?.let { health ->
                Text("Queues native/processing/disk: ${health.nativeQueueDepth}/${health.processingQueueDepth}/${health.persistenceQueueDepth}")
                Text("Stage drops native/processing/disk: ${health.droppedNativeUnits}/${health.droppedProcessingUnits}/${health.droppedPersistenceUnits}")
                Text("Drops: ${health.droppedNativeUnits + health.droppedProcessingUnits + health.droppedPersistenceUnits}; overruns: ${health.overrunCount}; malformed: ${health.malformedFrameCount}")
            }
            Text("Storage free / estimated remaining: ${state.availableStorageBytes?.let(::formatBytes) ?: "—"} / ${state.estimatedRemainingBytes?.let(::formatBytes) ?: "—"}")
            Text("Battery: ${state.batteryPercent?.let { "$it%" } ?: "—"}; thermal status: ${state.thermalStatus ?: "—"}")
            state.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPause) { Text("Pause") }
            Button(onClick = onResume) { Text("Resume") }
            Button(onClick = onStop) { Text("Stop") }
        }
    }

    @Composable private fun SummaryPage(
        summary: SurveySummary?,
        exportMessage: String?,
        onNew: () -> Unit,
        onShareExport: (ExportPolicy) -> Unit,
        onSaveExport: (ExportPolicy) -> Unit,
        onProcess: () -> Unit,
    ) {
        var includeIq by remember { mutableStateOf(false) }
        var includeRoutes by remember { mutableStateOf(true) }
        var coordinateMode by remember { mutableStateOf(CoordinateMode.ROUNDED) }
        var includeNotes by remember { mutableStateOf(false) }
        var includeIdentifiers by remember { mutableStateOf(false) }
        Section("Survey summary") {
            if (summary == null) Text("Finalizing persisted results…") else {
                Text("Status: ${summary.survey.status}")
                Text("Spectrum aggregates: ${summary.aggregateCount}")
                Text("Location fixes: ${summary.locationFixCount}")
                val duration = (summary.survey.endedAtEpochMs ?: System.currentTimeMillis()) - (summary.survey.startedAtEpochMs ?: summary.survey.lastWallTimeEpochMs)
                Text("Collection time: ${formatDuration(summary.activeDurationMs)}; elapsed span: ${formatDuration(duration)} (includes pauses/gaps); route distance: ${"%.2f".format(summary.survey.distanceMeters / 1_000.0)} km")
                Text("Located observation batches: ${"%.1f".format(summary.survey.locationCoverageRatio * 100)}%")
                Text("Explicit acquisition gaps: ${summary.gapCount}")
                summary.gaps.forEach { gap ->
                    val durationMs = gap.endedMonotonicNs?.let { (it - gap.startedMonotonicNs).coerceAtLeast(0) / 1_000_000 }
                    Text("${gap.reason}: ${durationMs?.let { "$it ms" } ?: "open"}; ${gap.droppedUnitCount} dropped — ${gap.explanation}")
                }
                Text("Unlocated observations remain stored as MISSING or STALE.")
                Text("Drops: ${summary.survey.droppedFrameCount}; overruns: ${summary.survey.overrunCount}")
                summary.latestHealth?.let { health ->
                    Text("Final USB rate: ${"%.2f".format(health.usbBytesPerSecond / 1_000_000.0)} MB/s")
                    Text("Final queues native/processing/disk: ${health.nativeQueueDepth}/${health.processingQueueDepth}/${health.persistenceQueueDepth}")
                    Text("Battery: ${health.batteryPercent?.let { "$it%" } ?: "—"}; thermal status: ${health.thermalStatus ?: "—"}; free storage: ${formatBytes(health.availableStorageBytes)}")
                    health.warning?.let { Text("Health: $it", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        Section("Reviewed survey export") {
            Text("Nothing leaves app-private storage until you create this bundle and choose an Android share destination.")
            ReviewedExportOption("Include linked IQ", includeIq) { includeIq = it }
            ReviewedExportOption("Include route", includeRoutes) { includeRoutes = it }
            ReviewedExportOption("Include notes and user-entered labels", includeNotes) { includeNotes = it }
            ReviewedExportOption("Include device identifier suffix", includeIdentifiers) { includeIdentifiers = it }
            CoordinateMode.entries.forEach { mode ->
                OutlinedButton(onClick = { coordinateMode = mode }, modifier = Modifier.fillMaxWidth()) {
                    Text("${mode.name.lowercase()} coordinates${if (coordinateMode == mode) " ✓" else ""}")
                }
            }
            Text("Manifest review: IQ=$includeIq, route=$includeRoutes, coordinates=${coordinateMode.name.lowercase()}, notes=$includeNotes, identifiers=$includeIdentifiers")
            Button(
                enabled = summary?.survey?.status == SurveyStatus.COMPLETE.name,
                onClick = { onShareExport(ExportPolicy(includeIq, includeRoutes, coordinateMode, includeNotes, includeIdentifiers)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create reviewed survey bundle and open share sheet") }
            OutlinedButton(
                enabled = summary?.survey?.status == SurveyStatus.COMPLETE.name,
                onClick = { onSaveExport(ExportPolicy(includeIq, includeRoutes, coordinateMode, includeNotes, includeIdentifiers)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save reviewed survey bundle") }
            exportMessage?.let { Text(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onProcess) { Text("Process discoveries") }
            OutlinedButton(onClick = onNew) { Text("New survey") }
        }
    }

    private suspend fun loadDiscoveries(update: (DiscoveryUiState) -> Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                val repository = DiscoveryRepository(NotebookDatabase.open(this@MainActivity).notebookDao())
                val fingerprints = repository.discoveries()
                val details = fingerprints.map { repository.detail(it.id) }
                val partial = repository.partialDataExplanation(details)
                val surveys = repository.reprocessableSurveys()
                DiscoveryUiState(
                    when {
                        fingerprints.isEmpty() -> DiscoveryPhase.EMPTY
                        partial != null -> DiscoveryPhase.PARTIAL
                        else -> DiscoveryPhase.CONTENT
                    },
                    fingerprints,
                    details,
                    partial,
                    surveys,
                )
            }
        }.onSuccess(update).onFailure { update(DiscoveryUiState(DiscoveryPhase.FAILED, problem = it.message)) }
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
            OutlinedButton(
                enabled = value > range.first,
                onClick = { onValue((value - range.step).coerceAtLeast(range.first)) },
                modifier = Modifier.semantics { contentDescription = "Decrease $label" },
            ) { Text("−") }
            OutlinedButton(
                enabled = value < range.last,
                onClick = { onValue((value + range.step).coerceAtMost(range.last)) },
                modifier = Modifier.semantics { contentDescription = "Increase $label" },
            ) { Text("+") }
        }
    }

    private fun requestUsbPermission(usb: UsbManager, device: UsbDevice) {
        val pending = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        usb.requestPermission(device, pending)
    }

    private fun shareBundle(file: java.io.File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share reviewed RF Field Notebook bundle"))
    }

    private fun startSurveyService(value: SurveyLaunch) {
        ContextCompat.startForegroundService(this, Intent(this, SurveyAcquisitionService::class.java)
            .setAction(SurveyAcquisitionService.ACTION_START)
            .putExtra(SurveyAcquisitionService.EXTRA_SURVEY_ID, value.surveyId)
            .putExtra(SurveyAcquisitionService.EXTRA_SERIAL_SUFFIX, value.serialSuffix)
            .putExtra(SurveyAcquisitionService.EXTRA_START_FREQUENCY_HZ, value.startFrequencyHz)
            .putExtra(SurveyAcquisitionService.EXTRA_END_FREQUENCY_HZ, value.endFrequencyHz)
            .putExtra(SurveyAcquisitionService.EXTRA_BIN_WIDTH_HZ, value.binWidthHz)
            .putExtra(SurveyAcquisitionService.EXTRA_SAMPLE_RATE_HZ, value.sampleRateHz)
            .putExtra(SurveyAcquisitionService.EXTRA_BASEBAND_FILTER_HZ, value.basebandFilterHz)
            .putExtra(SurveyAcquisitionService.EXTRA_LNA_GAIN_DB, value.lnaGainDb)
            .putExtra(SurveyAcquisitionService.EXTRA_VGA_GAIN_DB, value.vgaGainDb)
            .putExtra(SurveyAcquisitionService.EXTRA_RF_AMP_ENABLED, value.rfAmpEnabled)
            .putExtra(SurveyAcquisitionService.EXTRA_ANTENNA_POWER_ENABLED, value.antennaPowerEnabled)
            .putExtra(
                SurveyAcquisitionService.EXTRA_SCAN_RANGES_HZ,
                value.scanRanges.flatMap { listOf(it.startHz, it.endHz) }.toLongArray(),
            ))
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

    private fun estimateSurveyBytes(start: String, end: String, additional: String, excluded: String, binWidth: Int): Long =
        ((includedWidth(start, end, additional, excluded) + binWidth - 1) / binWidth).also { require(it > 0) } * 1_800L * 128L

    private fun estimatedCycleMs(start: String, end: String, additional: String, excluded: String, binWidth: Int): Long =
        (includedWidth(start, end, additional, excluded) / binWidth / 2L).also { require(it > 0) }

    private fun includedWidth(start: String, end: String, additional: String, excluded: String): Long {
        val ranges = configuredRanges(start, end, additional)
        val exclusions = FrequencyText.parseRanges(excluded)
        require(exclusions.all { exclusion -> ranges.any { it.contains(exclusion) } })
        return ranges.sumOf { it.widthHz } - exclusions.sumOf { it.widthHz }
    }

    private fun configuredRanges(start: String, end: String, additional: String): List<FrequencyRange> =
        listOf(FrequencyRange(FrequencyText.parseHz(start), FrequencyText.parseHz(end))) + FrequencyText.parseRanges(additional)

    private fun defaultRangeText(band: String) = when (band) {
        "315 MHz" -> "314 MHz" to "316 MHz"
        "433 MHz" -> "433 MHz" to "435 MHz"
        "150–174 MHz" -> "150 MHz" to "174 MHz"
        "450–470 MHz" -> "450 MHz" to "470 MHz"
        else -> "902 MHz" to "928 MHz"
    }

    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs.coerceAtLeast(0) / 1_000
        return "%02d:%02d:%02d".format(seconds / 3_600, seconds / 60 % 60, seconds % 60)
    }

    private fun formatBytes(bytes: Long): String = "%.1f MiB".format(bytes / 1_048_576.0)

    companion object {
        private const val ACTION_USB_PERMISSION = "dev.rfnotebook.action.USB_PERMISSION"
        private const val STATE_PAGE = "rf-field-notebook.page"
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

@Composable
internal fun ReviewedExportOption(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label)
    }
}
