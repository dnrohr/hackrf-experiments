package dev.rfnotebook.acquisition

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbDevice
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.BatteryManager
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dev.rfnotebook.domain.GapReason
import dev.rfnotebook.domain.LocationAssociationKind
import dev.rfnotebook.domain.LocationAssociationPolicy
import dev.rfnotebook.domain.LocationAssociator
import dev.rfnotebook.domain.LocationFix
import dev.rfnotebook.domain.SurveyCommand
import dev.rfnotebook.domain.SurveyStatus
import dev.rfnotebook.domain.RadioConnectionCommand
import dev.rfnotebook.domain.RadioConnectionStatus
import dev.rfnotebook.radio.api.HackrfCompatibilityPolicy as RadioCompatibilityPolicy
import dev.rfnotebook.radio.api.RadioException
import dev.rfnotebook.radio.api.RadioErrorCode
import dev.rfnotebook.radio.api.SweepConfig
import dev.rfnotebook.radio.api.SweepSink
import dev.rfnotebook.radio.api.SweepRange
import dev.rfnotebook.radio.hackrf.AndroidHackrfRadio
import dev.rfnotebook.radio.hackrf.NativeRadioSession
import dev.rfnotebook.signal.processing.HackrfSweepProcessor
import dev.rfnotebook.signal.processing.ParsedSweepFrame
import dev.rfnotebook.signal.processing.SpectrumBucketAccumulator
import dev.rfnotebook.signal.processing.SpectrumSummary
import dev.rfnotebook.storage.HealthSnapshotEntity
import dev.rfnotebook.storage.LocationFixEntity
import dev.rfnotebook.storage.NotebookDatabase
import dev.rfnotebook.storage.SpectrumAggregateEntity
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class SurveyAcquisitionState(
    val surveyId: String? = null,
    val status: SurveyStatus? = null,
    val device: String = "—",
    val usbBytesPerSecond: Long = 0,
    val aggregatesPersisted: Long = 0,
    val locationAccuracyM: Float? = null,
    val health: AcquisitionHealth? = null,
    val warning: String? = null,
    val startedAtEpochMs: Long? = null,
    val distanceMeters: Double = 0.0,
    val configuredRanges: String = "—",
    val availableStorageBytes: Long? = null,
    val estimatedRemainingBytes: Long? = null,
    val batteryPercent: Int? = null,
    val thermalStatus: Int? = null,
    val currentRange: String = "—",
    val locationFixAgeMs: Long? = null,
)

object SurveyAcquisitionStatus {
    private val mutable = MutableStateFlow(SurveyAcquisitionState())
    val state = mutable.asStateFlow()
    internal fun update(state: SurveyAcquisitionState) { mutable.value = state }
}

private data class RawTransfer(val bytes: ByteArray, val wallTimeEpochMs: Long, val monotonicNs: Long)
private data class TimedFrame(val frame: ParsedSweepFrame, val wallTimeEpochMs: Long, val monotonicNs: Long)
private sealed interface PersistItem {
    data class Fix(val value: LocationFixEntity) : PersistItem
    data class Aggregates(val fix: LocationFixEntity?, val values: List<SpectrumAggregateEntity>) : PersistItem
}

class SurveyAcquisitionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val latestFixes = AtomicReference<List<LocationFix>>(emptyList())
    private val counters = AcquisitionHealthCounters()
    private val nativeQueue = BoundedStage<RawTransfer>(PipelineStage.NATIVE, NATIVE_QUEUE_CAPACITY)
    private val processingQueue = BoundedStage<TimedFrame>(PipelineStage.PROCESSING, PROCESSING_QUEUE_CAPACITY)
    private val persistenceQueue = BoundedStage<PersistItem>(PipelineStage.PERSISTENCE, PERSISTENCE_QUEUE_CAPACITY)
    private val accumulator = SpectrumBucketAccumulator(TIME_BUCKET_MS)
    private val workers = mutableListOf<Job>()
    private lateinit var database: NotebookDatabase
    private lateinit var radioController: ServiceRadioController
    private lateinit var coordinator: SurveyCoordinator
    private lateinit var surveyId: String
    private var aggregatesPersisted = 0L
    private val locatedBatches = AtomicLong()
    private val totalBatches = AtomicLong()
    private val unlocatedObservations = AtomicLong()
    private val routeDistanceMillimeters = AtomicLong()
    private val stopping = AtomicBoolean()
    private val currentRange = AtomicReference<Pair<Long, Long>?>(null)
    private val operationalWarning = AtomicReference<String?>(null)
    private var lastRouteLocation: Location? = null
    private var estimatedSurveyBytes = 0L
    private var foregroundReady = false
    private var receiverRegistered = false
    private var sampleRateHz = 0
    private var binWidthHz = 0
    private var scanRanges: List<SweepRange> = emptyList()
    private var lastNativeDrops = 0L
    private var lastCallbackErrors = 0L
    private var lastBytes = 0L
    private var lastStatsNs = 0L

    private val locationManager by lazy { getSystemService(LocationManager::class.java) }
    private val locationListener = LocationListener(::onLocation)
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val device = intent?.let { IntentCompat.getParcelableExtra(it, UsbManager.EXTRA_DEVICE, UsbDevice::class.java) }
            if (device == null || device.vendorId != HACKRF_VENDOR_ID || device.productId !in HACKRF_PRODUCT_IDS) return
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> scope.launch { handleDetach() }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> updateNotification("HackRF attached; tap Resume when ready")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        database = NotebookDatabase.open(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureBootstrapForeground()
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> scope.launch { startSurvey(intent ?: Intent()) }
            ACTION_PAUSE -> scope.launch { pauseSurvey() }
            ACTION_RESUME -> scope.launch { resumeSurvey() }
            ACTION_STOP -> scope.launch { stopSurvey() }
        }
        return START_NOT_STICKY
    }

    private suspend fun startSurvey(intent: Intent) {
        try {
            requireLocationPermission()
            surveyId = requireNotNull(intent.getStringExtra(EXTRA_SURVEY_ID)) { "Missing survey ID" }
            sampleRateHz = intent.getIntExtra(EXTRA_SAMPLE_RATE_HZ, 0)
            binWidthHz = intent.getIntExtra(EXTRA_BIN_WIDTH_HZ, 0)
            val rangeEdges = intent.getLongArrayExtra(EXTRA_SCAN_RANGES_HZ)
                ?: longArrayOf(
                    intent.getLongExtra(EXTRA_START_FREQUENCY_HZ, 0),
                    intent.getLongExtra(EXTRA_END_FREQUENCY_HZ, 0),
                )
            require(rangeEdges.isNotEmpty() && rangeEdges.size % 2 == 0) { "Malformed sweep range list" }
            scanRanges = rangeEdges.toList().chunked(2).map { (start, end) -> SweepRange(start, end) }
            val config = SweepConfig(
                scanRanges.minOf { it.startFrequencyHz },
                scanRanges.maxOf { it.endFrequencyHz },
                binWidthHz,
                sampleRateHz,
                intent.getIntExtra(EXTRA_BASEBAND_FILTER_HZ, 0),
                intent.getIntExtra(EXTRA_LNA_GAIN_DB, 0),
                intent.getIntExtra(EXTRA_VGA_GAIN_DB, 0),
                intent.getBooleanExtra(EXTRA_RF_AMP_ENABLED, false),
                intent.getBooleanExtra(EXTRA_ANTENNA_POWER_ENABLED, false),
                scanRanges,
            )
            val suffix = requireNotNull(intent.getStringExtra(EXTRA_SERIAL_SUFFIX)) { "Missing selected serial suffix" }
            check(config.binWidthHz > 0) { "Sweep bin width must be positive" }
            val bins = config.ranges.sumOf {
                (it.endFrequencyHz - it.startFrequencyHz + config.binWidthHz - 1) / config.binWidthHz
            }.coerceAtLeast(1)
            estimatedSurveyBytes = bins * DEFAULT_SURVEY_SECONDS * ESTIMATED_AGGREGATE_BYTES
            val initialStorage = StorageGuard.assess(StatFs(filesDir.absolutePath).availableBytes, estimatedSurveyBytes)
            check(initialStorage.canStart) { initialStorage.explanation }
            radioController = ServiceRadioController(suffix, config)
            coordinator = SurveyCoordinator(
                RoomSurveyStateStore(database.notebookDao()),
                radioController,
            ) { SurveyTime(System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos()) }
            registerUsbReceiver()
            startLocationUpdates()
            startWorkers()
            val persisted = database.notebookDao().survey(surveyId) ?: error("Survey $surveyId does not exist")
            val state = when (SurveyStatus.valueOf(persisted.status)) {
                SurveyStatus.DRAFT -> {
                    coordinator.command(surveyId, SurveyCommand.Validate)
                    coordinator.command(surveyId, SurveyCommand.Start)
                }
                SurveyStatus.VALIDATING -> coordinator.command(surveyId, SurveyCommand.Start)
                SurveyStatus.ACTIVE -> {
                    coordinator.recover(surveyId)
                    coordinator.command(surveyId, SurveyCommand.Resume)
                }
                SurveyStatus.PAUSED -> coordinator.command(surveyId, SurveyCommand.Resume)
                else -> error("Survey ${persisted.status.lowercase()} cannot be started")
            }
            val activeSurvey = requireNotNull(database.notebookDao().survey(surveyId))
            routeDistanceMillimeters.set((activeSurvey.distanceMeters * 1_000.0).toLong())
            SurveyAcquisitionStatus.update(
                SurveyAcquisitionStatus.state.value.copy(
                    surveyId = surveyId,
                    status = state.status,
                    startedAtEpochMs = activeSurvey.startedAtEpochMs,
                    distanceMeters = activeSurvey.distanceMeters,
                    configuredRanges = scanRanges.joinToString { "${it.startFrequencyHz / 1_000_000.0}–${it.endFrequencyHz / 1_000_000.0} MHz" },
                ),
            )
            updateNotification("Survey active • RX only")
        } catch (failure: Throwable) {
            failVisible(failure)
        }
    }

    private suspend fun pauseSurvey() {
        if (!::coordinator.isInitialized) return
        val state = coordinator.command(surveyId, SurveyCommand.Pause)
        SurveyAcquisitionStatus.update(SurveyAcquisitionStatus.state.value.copy(status = state.status))
        updateNotification("Survey paused")
    }

    private suspend fun resumeSurvey() {
        if (!::coordinator.isInitialized) return
        try {
            val state = coordinator.command(surveyId, SurveyCommand.Resume)
            RoomSurveyStateStore(database.notebookDao()).closeOpenGaps(
                surveyId, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos(),
            )
            operationalWarning.set(null)
            SurveyAcquisitionStatus.update(SurveyAcquisitionStatus.state.value.copy(status = state.status, warning = null))
            updateNotification("Survey active • RX only")
        } catch (failure: Throwable) {
            val explanation = if (failure is RadioException) "${failure.code}: ${failure.message}" else failure.message ?: failure.javaClass.simpleName
            operationalWarning.set("Resume failed: $explanation")
            SurveyAcquisitionStatus.update(
                SurveyAcquisitionStatus.state.value.copy(status = SurveyStatus.PAUSED, warning = operationalWarning.get()),
            )
            updateNotification("Resume failed • survey remains paused")
        }
    }

    private suspend fun stopSurvey() {
        if (!stopping.compareAndSet(false, true)) return
        if (::coordinator.isInitialized) {
            val current = database.notebookDao().survey(surveyId)
            if (current?.status == SurveyStatus.ACTIVE.name || current?.status == SurveyStatus.PAUSED.name) {
                coordinator.command(surveyId, SurveyCommand.Stop)
            }
            stopWorkers()
            persistFinalHealth()
            RoomSurveyStateStore(database.notebookDao()).closeOpenGaps(
                surveyId, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos(),
            )
            coordinator.command(surveyId, SurveyCommand.Finalize)
            radioController.close()
            SurveyAcquisitionStatus.update(SurveyAcquisitionStatus.state.value.copy(status = SurveyStatus.COMPLETE))
        }
        stopSelf()
    }

    private suspend fun handleDetach() {
        if (!::coordinator.isInitialized) return
        radioController.closeNative()
        val current = database.notebookDao().survey(surveyId)
        if (current?.status == SurveyStatus.ACTIVE.name) coordinator.command(surveyId, SurveyCommand.Pause)
        radioController.markRecoverable("USB detached")
        RoomSurveyStateStore(database.notebookDao()).recordOpenGap(
            surveyId, GapReason.USB_DETACH, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos(),
        )
        counters.increment(HealthCounter.SERVICE_GAP)
        operationalWarning.set("USB detached; gap recorded. Reattach and Resume.")
        SurveyAcquisitionStatus.update(
            SurveyAcquisitionStatus.state.value.copy(status = SurveyStatus.PAUSED, warning = operationalWarning.get()),
        )
        updateNotification("USB detached • survey paused")
    }

    private fun startWorkers() {
        if (workers.isNotEmpty()) return
        workers += scope.launch {
            while (isActive) {
                val raw = nativeQueue.receiveOrNull() ?: break
                try {
                    val result = HackrfSweepProcessor.process(raw.bytes, sampleRateHz, binWidthHz)
                    counters.increment(HealthCounter.MALFORMED_FRAME, result.malformedBlocks.toLong())
                    result.frames.asSequence()
                        .map { frame -> frame.copy(bins = frame.bins.filter { bin ->
                            scanRanges.any { range -> bin.frequencyHz in range.startFrequencyHz until range.endFrequencyHz }
                        }) }
                        .filter { it.bins.isNotEmpty() }
                        .forEach {
                            currentRange.set(it.lowFrequencyHz to it.highFrequencyHz)
                            processingQueue.offer(TimedFrame(it, raw.wallTimeEpochMs, raw.monotonicNs))
                        }
                } catch (_: IllegalArgumentException) {
                    counters.increment(HealthCounter.MALFORMED_FRAME)
                }
            }
        }
        workers += scope.launch {
            while (isActive) {
                val timed = processingQueue.receiveOrNull() ?: break
                enqueueSummaries(accumulator.add(timed.wallTimeEpochMs, timed.monotonicNs, timed.frame))
            }
        }
        workers += scope.launch {
            while (isActive) {
                delay(TIME_BUCKET_MS)
                enqueueSummaries(accumulator.flushBefore(System.currentTimeMillis()))
            }
        }
        workers += scope.launch {
            while (isActive) {
                val items = buildList {
                    add(persistenceQueue.receiveOrNull() ?: return@launch)
                    while (size < PERSISTENCE_BATCH_MAX_ITEMS) {
                        val next = persistenceQueue.poll() ?: break
                        add(next)
                    }
                }
                val fixes = items.asSequence()
                    .mapNotNull { item -> when (item) {
                        is PersistItem.Fix -> item.value
                        is PersistItem.Aggregates -> item.fix
                    } }
                    .distinctBy { it.id }
                    .toList()
                val aggregates = items.flatMap { item ->
                    when (item) {
                        is PersistItem.Fix -> emptyList()
                        is PersistItem.Aggregates -> item.values
                    }
                }
                database.notebookDao().insertSurveyBatch(fixes, aggregates)
                aggregatesPersisted += aggregates.size
            }
        }
        workers += scope.launch { publishHealthLoop() }
    }

    private suspend fun publishHealthLoop() {
        while (scope.isActive) {
            delay(HEALTH_INTERVAL_MS)
            if (!::surveyId.isInitialized) continue
            val stats = radioController.statsOrNull()
            val nowNs = SystemClock.elapsedRealtimeNanos()
            val elapsedNs = (nowNs - lastStatsNs).coerceAtLeast(1)
            val bytesPerSecond = stats?.let { (it.byteCount - lastBytes).coerceAtLeast(0) * 1_000_000_000L / elapsedNs } ?: 0
            stats?.let {
                val newDrops = (it.droppedBufferCount - lastNativeDrops).coerceAtLeast(0)
                val newErrors = (it.callbackErrorCount - lastCallbackErrors).coerceAtLeast(0)
                counters.increment(HealthCounter.NATIVE_OVERRUN, newDrops + newErrors)
                lastNativeDrops = it.droppedBufferCount
                lastCallbackErrors = it.callbackErrorCount
                lastBytes = it.byteCount
            }
            if (stats != null && !stats.streaming && database.notebookDao().survey(surveyId)?.status == SurveyStatus.ACTIVE.name) {
                handleRadioStall()
            }
            lastStatsNs = nowNs
            val health = counters.snapshot(nativeQueue, processingQueue, persistenceQueue)
            val storage = StatFs(filesDir.absolutePath).availableBytes
            val battery = getSystemService(BatteryManager::class.java)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
            val thermal = getSystemService(PowerManager::class.java).currentThermalStatus
            val warning = HealthWarningPolicy.warning(
                health, storage, battery, thermal, PowerManager.THERMAL_STATUS_MODERATE,
                operationalWarning.get(),
            )
            val newestFix = latestFixes.get().lastOrNull()
            val fixAgeMs = newestFix?.let { (nowNs - it.monotonicNs).coerceAtLeast(0) / 1_000_000 }
            database.notebookDao().insertHealthSnapshot(
                HealthSnapshotEntity(
                    surveyId = surveyId,
                    wallTimeEpochMs = System.currentTimeMillis(),
                    monotonicNs = nowNs,
                    usbBytesPerSecond = bytesPerSecond,
                    nativeQueueDepth = health.nativeQueueDepth,
                    processingQueueDepth = health.processingQueueDepth,
                    persistenceQueueDepth = health.persistenceQueueDepth,
                    droppedNativeUnits = health.droppedNativeUnits,
                    droppedProcessingUnits = health.droppedProcessingUnits,
                    droppedPersistenceUnits = health.droppedPersistenceUnits,
                    malformedFrameCount = health.malformedFrameCount,
                    overrunCount = health.overrunCount,
                    staleFixCount = health.staleFixCount,
                    serviceGapCount = health.serviceGapCount,
                    availableStorageBytes = storage,
                    estimatedRemainingBytes = (estimatedSurveyBytes - aggregatesPersisted * ESTIMATED_AGGREGATE_BYTES).coerceAtLeast(0),
                    batteryPercent = battery,
                    thermalStatus = thermal,
                    warning = warning,
                ),
            )
            database.notebookDao().updateSurveyHealth(
                surveyId = surveyId,
                distanceMeters = routeDistanceMillimeters.get() / 1_000.0,
                locationCoverageRatio = locatedBatches.get().toDouble() / totalBatches.get().coerceAtLeast(1),
                droppedFrameCount = health.droppedNativeUnits + health.droppedProcessingUnits + health.droppedPersistenceUnits,
                overrunCount = health.overrunCount,
                malformedFrameCount = health.malformedFrameCount,
                staleFixCount = health.staleFixCount,
                unlocatedObservationCount = unlocatedObservations.get(),
            )
            SurveyAcquisitionStatus.update(
                SurveyAcquisitionStatus.state.value.copy(
                    usbBytesPerSecond = bytesPerSecond,
                    aggregatesPersisted = aggregatesPersisted,
                    health = health,
                    warning = warning,
                    distanceMeters = routeDistanceMillimeters.get() / 1_000.0,
                    availableStorageBytes = storage,
                    estimatedRemainingBytes = (estimatedSurveyBytes - aggregatesPersisted * ESTIMATED_AGGREGATE_BYTES).coerceAtLeast(0),
                    batteryPercent = battery,
                    thermalStatus = thermal,
                    currentRange = currentRange.get()?.let { (low, high) ->
                        "${low / 1_000_000.0}–${high / 1_000_000.0} MHz"
                    } ?: "—",
                    locationFixAgeMs = fixAgeMs,
                ),
            )
            updateNotification("RX ${bytesPerSecond / 1_000_000} MB/s • ${health.droppedNativeUnits + health.droppedProcessingUnits + health.droppedPersistenceUnits} dropped")
            if (storage < StorageGuard.DEFAULT_RESERVE_BYTES) {
                RoomSurveyStateStore(database.notebookDao()).recordCountedGap(
                    surveyId,
                    GapReason.LOW_STORAGE,
                    System.currentTimeMillis(),
                    SystemClock.elapsedRealtimeNanos(),
                    0,
                    "Available storage fell below the fixed ${StorageGuard.DEFAULT_RESERVE_BYTES}-byte reserve; survey stopped orderly",
                )
                scope.launch { stopSurvey() }
                return
            }
        }
    }

    private fun onLocation(location: Location) {
        lastRouteLocation?.let { previous ->
            routeDistanceMillimeters.addAndGet((previous.distanceTo(location) * 1_000.0).toLong().coerceAtLeast(0))
        }
        lastRouteLocation = Location(location)
        val fix = LocationFix(
            id = UUID.randomUUID().toString(),
            surveyId = surveyId,
            wallTimeEpochMs = location.time,
            monotonicNs = location.elapsedRealtimeNanos,
            latitude = location.latitude,
            longitude = location.longitude,
            horizontalAccuracyM = location.accuracy,
            altitudeM = location.altitude.takeIf { location.hasAltitude() },
            speedMps = location.speed.takeIf { location.hasSpeed() },
            bearingDegrees = location.bearing.takeIf { location.hasBearing() },
            provider = location.provider ?: "unknown",
            isInterpolated = false,
        )
        latestFixes.updateAndGet { (it + fix).takeLast(MAX_RECENT_FIXES) }
        persistenceQueue.offer(PersistItem.Fix(fix.toEntity()))
        SurveyAcquisitionStatus.update(SurveyAcquisitionStatus.state.value.copy(locationAccuracyM = fix.horizontalAccuracyM))
    }

    private suspend fun handleRadioStall() {
        radioController.closeNative()
        coordinator.command(surveyId, SurveyCommand.Pause)
        radioController.markRecoverable("Radio transfer stalled")
        RoomSurveyStateStore(database.notebookDao()).recordOpenGap(
            surveyId, GapReason.RADIO_STALL, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos(),
        )
        counters.increment(HealthCounter.SERVICE_GAP)
        operationalWarning.set("Radio stalled; gap recorded. Tap Resume to reopen.")
        SurveyAcquisitionStatus.update(
            SurveyAcquisitionStatus.state.value.copy(status = SurveyStatus.PAUSED, warning = operationalWarning.get()),
        )
        updateNotification("Radio stalled • survey paused")
    }

    private fun enqueueSummaries(summaries: List<SpectrumSummary>) {
        if (summaries.isEmpty()) return
        val byTime = summaries.groupBy { it.timeBucketStartEpochMs to it.representativeMonotonicNs }
        byTime.values.forEach { group ->
            val representative = group.first()
            val association = LocationAssociator.associate(
                representative.representativeMonotonicNs,
                latestFixes.get(),
                LocationAssociationPolicy(MAX_FIX_AGE_NS, MAX_INTERPOLATION_GAP_NS),
            )
            if (association.kind == LocationAssociationKind.STALE) counters.increment(HealthCounter.STALE_FIX)
            totalBatches.incrementAndGet()
            if (association.fix == null) unlocatedObservations.addAndGet(group.size.toLong()) else locatedBatches.incrementAndGet()
            val fixEntity = association.fix?.toEntity()
            persistenceQueue.offer(PersistItem.Aggregates(
                fixEntity,
                group.map { summary ->
                    SpectrumAggregateEntity(
                        surveyId = surveyId,
                        timeBucketStartEpochMs = summary.timeBucketStartEpochMs,
                        frequencyBinHz = summary.frequencyBinHz,
                        minimumPowerDbfs = summary.minimumPowerDbfs,
                        medianPowerDbfs = summary.medianPowerDbfs,
                        maximumPowerDbfs = summary.maximumPowerDbfs,
                        noiseEstimateDbfs = summary.noiseEstimateDbfs,
                        sampleCount = summary.sampleCount,
                        locationFixId = fixEntity?.id,
                        locationState = association.kind.name,
                    )
                },
            ))
        }
    }

    private fun LocationFix.toEntity() = LocationFixEntity(
        id, surveyId, wallTimeEpochMs, monotonicNs, latitude, longitude, horizontalAccuracyM,
        altitudeM, speedMps, bearingDegrees, provider, isInterpolated,
    )

    private fun startLocationUpdates() {
        val fine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val provider = if (fine) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        locationManager.requestLocationUpdates(provider, LOCATION_INTERVAL_MS, 0f, locationListener, Looper.getMainLooper())
    }

    private fun requireLocationPermission() {
        val coarse = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasAnyLocationPermission(coarse, fine)) error("Location permission is required to start a survey")
    }

    private fun registerUsbReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private fun ensureBootstrapForeground() {
        if (foregroundReady) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Active RF survey", NotificationManager.IMPORTANCE_LOW),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Preparing survey"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else startForeground(NOTIFICATION_ID, notification("Preparing survey"))
        foregroundReady = true
    }

    private fun promoteForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification("Survey active • RX only"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_rx)
        .setContentTitle("RF Field Notebook — receive only")
        .setContentText(text)
        .setOngoing(true)
        .addAction(0, "Pause", serviceAction(ACTION_PAUSE, 1))
        .addAction(0, "Resume", serviceAction(ACTION_RESUME, 2))
        .addAction(0, "Stop", serviceAction(ACTION_STOP, 3))
        .build()

    private fun serviceAction(action: String, requestCode: Int) = PendingIntent.getService(
        this, requestCode, Intent(this, SurveyAcquisitionService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private suspend fun failVisible(failure: Throwable) {
        val explanation = if (failure is RadioException) "${failure.code}: ${failure.message}" else failure.message ?: failure.javaClass.simpleName
        if (::coordinator.isInitialized && ::surveyId.isInitialized) {
            try {
                val current = database.notebookDao().survey(surveyId)
                if (current != null && current.status !in setOf(SurveyStatus.COMPLETE.name, SurveyStatus.FAILED.name)) {
                    coordinator.command(surveyId, SurveyCommand.Fail(explanation))
                }
            } catch (_: Throwable) { }
            try { stopWorkers() } catch (_: Throwable) { }
            try { radioController.close() } catch (_: Throwable) { }
        }
        SurveyAcquisitionStatus.update(SurveyAcquisitionStatus.state.value.copy(status = SurveyStatus.FAILED, warning = explanation))
        updateNotification("Survey failed • $explanation")
        stopSelf()
    }

    private suspend fun stopWorkers() {
        if (workers.isEmpty()) return
        val nativeWorker = workers[0]
        val processingWorker = workers[1]
        val flushWorker = workers[2]
        val persistenceWorker = workers[3]
        val healthWorker = workers[4]
        flushWorker.cancel()
        flushWorker.join()
        nativeQueue.close()
        var timedOut = withTimeoutOrNull(PIPELINE_DRAIN_TIMEOUT_MS) { nativeWorker.join(); true } != true
        processingQueue.close()
        timedOut = (withTimeoutOrNull(PIPELINE_DRAIN_TIMEOUT_MS) { processingWorker.join(); true } != true) || timedOut
        enqueueSummaries(accumulator.flushAll())
        persistenceQueue.close()
        timedOut = (withTimeoutOrNull(PIPELINE_DRAIN_TIMEOUT_MS) { persistenceWorker.join(); true } != true) || timedOut
        healthWorker.cancel()
        healthWorker.join()
        workers.forEach { if (it.isActive) it.cancel() }
        workers.forEach { runCatching { it.join() } }
        workers.clear()
        val discarded = nativeQueue.discardPending() + processingQueue.discardPending() + persistenceQueue.discardPending()
        if (timedOut) {
            counters.increment(HealthCounter.SERVICE_GAP)
            RoomSurveyStateStore(database.notebookDao()).recordCountedGap(
                surveyId, GapReason.QUEUE_PRESSURE, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos(),
                discarded.toLong(), "Pipeline did not drain within ${PIPELINE_DRAIN_TIMEOUT_MS} ms; queued units were counted as dropped",
            )
        }
    }

    private suspend fun persistFinalHealth() {
        val health = counters.snapshot(nativeQueue, processingQueue, persistenceQueue)
        database.notebookDao().updateSurveyHealth(
            surveyId = surveyId,
            distanceMeters = routeDistanceMillimeters.get() / 1_000.0,
            locationCoverageRatio = locatedBatches.get().toDouble() / totalBatches.get().coerceAtLeast(1),
            droppedFrameCount = health.droppedNativeUnits + health.droppedProcessingUnits + health.droppedPersistenceUnits,
            overrunCount = health.overrunCount,
            malformedFrameCount = health.malformedFrameCount,
            staleFixCount = health.staleFixCount,
            unlocatedObservationCount = unlocatedObservations.get(),
        )
    }

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(locationListener) }
        if (receiverRegistered) runCatching { unregisterReceiver(usbReceiver) }
        if (::radioController.isInitialized) radioController.closeNative()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inner class ServiceRadioController(
        private val serialSuffix: String,
        private val config: SweepConfig,
    ) : SurveyRadioController {
        private var session: NativeRadioSession? = null
        private val connectionStore = RoomRadioConnectionStore(
            database.notebookDao(),
            "radio:$serialSuffix",
        ) { SurveyTime(System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos()) }

        override suspend fun start() {
            if (session == null) {
                when (connectionStore.state().status) {
                    RadioConnectionStatus.DISCONNECTED -> connectionStore.transition(RadioConnectionCommand.Attach(true))
                    RadioConnectionStatus.ERROR_RECOVERABLE -> connectionStore.transition(RadioConnectionCommand.Retry)
                    RadioConnectionStatus.READY -> {
                        connectionStore.transition(RadioConnectionCommand.RecoverableError("Reopening process-local radio session"))
                        connectionStore.transition(RadioConnectionCommand.Retry)
                    }
                    RadioConnectionStatus.OPENING -> Unit
                    else -> connectionStore.transition(RadioConnectionCommand.Close).also {
                        connectionStore.transition(RadioConnectionCommand.Closed)
                        connectionStore.transition(RadioConnectionCommand.Attach(true))
                    }
                }
                val opened = try {
                    AndroidHackrfRadio(this@SurveyAcquisitionService).open(serialSuffix) as NativeRadioSession
                } catch (failure: Throwable) {
                    connectionStore.transition(RadioConnectionCommand.RecoverableError(failure.message ?: "Open failed"))
                    throw failure
                }
                val info = try {
                    opened.deviceInfo()
                } catch (failure: Throwable) {
                    opened.close()
                    connectionStore.transition(RadioConnectionCommand.RecoverableError(failure.message ?: "Identity read failed"))
                    throw failure
                }
                val compatibility = RadioCompatibilityPolicy.evaluate(info)
                if (!compatibility.compatible) {
                    opened.close()
                    connectionStore.transition(RadioConnectionCommand.TerminalError(compatibility.explanation))
                    throw RadioException(RadioErrorCode.INCOMPATIBLE_FIRMWARE, compatibility.explanation)
                }
                session = opened
                connectionStore.transition(RadioConnectionCommand.Opened)
                database.notebookDao().radioDevice("radio:$serialSuffix")?.let { stored ->
                    database.notebookDao().insertRadioDevice(
                        stored.copy(
                            model = info.boardName,
                            hardwareRevision = info.hardwareRevision,
                            firmwareVersion = info.firmwareVersion,
                            usbApiVersion = info.apiVersion,
                            lastSeenAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                }
                SurveyAcquisitionStatus.update(
                    SurveyAcquisitionStatus.state.value.copy(
                        device = "${info.boardName}; FW ${info.firmwareVersion}; API ${info.apiVersion}; …${info.serialSuffix}",
                    ),
                )
            }
            promoteForeground()
            try {
                session!!.startSweep(config, SweepSink { bytes, monotonicNs ->
                    nativeQueue.offer(RawTransfer(bytes, System.currentTimeMillis(), monotonicNs))
                })
                connectionStore.transition(RadioConnectionCommand.StartSurvey)
            } catch (failure: Throwable) {
                closeNative()
                connectionStore.transition(RadioConnectionCommand.RecoverableError(failure.message ?: "Sweep start failed"))
                throw failure
            }
        }

        override suspend fun stop() {
            session?.stop()
            if (connectionStore.state().status == RadioConnectionStatus.SURVEYING) {
                connectionStore.transition(RadioConnectionCommand.StopSurvey)
            }
        }

        fun statsOrNull() = runCatching { session?.stats() }.getOrNull()

        fun closeNative() {
            session?.close()
            session = null
        }

        suspend fun markRecoverable(explanation: String) {
            connectionStore.transition(RadioConnectionCommand.RecoverableError(explanation))
        }

        suspend fun close() {
            runCatching { connectionStore.transition(RadioConnectionCommand.Close) }
            closeNative()
            runCatching { connectionStore.transition(RadioConnectionCommand.Closed) }
        }
    }

    companion object {
        const val ACTION_START = "dev.rfnotebook.action.START_SURVEY"
        const val ACTION_PAUSE = "dev.rfnotebook.action.PAUSE_SURVEY"
        const val ACTION_RESUME = "dev.rfnotebook.action.RESUME_SURVEY"
        const val ACTION_STOP = "dev.rfnotebook.action.STOP_SURVEY"
        const val EXTRA_SURVEY_ID = "survey-id"
        const val EXTRA_SERIAL_SUFFIX = "serial-suffix"
        const val EXTRA_START_FREQUENCY_HZ = "start-frequency-hz"
        const val EXTRA_END_FREQUENCY_HZ = "end-frequency-hz"
        const val EXTRA_BIN_WIDTH_HZ = "bin-width-hz"
        const val EXTRA_SAMPLE_RATE_HZ = "sample-rate-hz"
        const val EXTRA_BASEBAND_FILTER_HZ = "baseband-filter-hz"
        const val EXTRA_LNA_GAIN_DB = "lna-gain-db"
        const val EXTRA_VGA_GAIN_DB = "vga-gain-db"
        const val EXTRA_RF_AMP_ENABLED = "rf-amp-enabled"
        const val EXTRA_ANTENNA_POWER_ENABLED = "antenna-power-enabled"
        const val EXTRA_SCAN_RANGES_HZ = "scan-ranges-hz"
        private const val CHANNEL = "survey-acquisition"
        private const val NOTIFICATION_ID = 101
        private const val NATIVE_QUEUE_CAPACITY = 4
        // A 262,144-byte native transfer contains up to sixteen 16,384-byte
        // sweep blocks, and each block yields two frames. Keep the queue
        // bounded while absorbing one complete transfer burst.
        private const val PROCESSING_QUEUE_CAPACITY = 128
        private const val PERSISTENCE_QUEUE_CAPACITY = 16
        private const val PERSISTENCE_BATCH_MAX_ITEMS = 64
        private const val MAX_RECENT_FIXES = 16
        private const val MAX_FIX_AGE_NS = 5_000_000_000L
        private const val MAX_INTERPOLATION_GAP_NS = 10_000_000_000L
        private const val TIME_BUCKET_MS = 1_000L
        private const val HEALTH_INTERVAL_MS = 1_000L
        private const val LOCATION_INTERVAL_MS = 1_000L
        private const val PIPELINE_DRAIN_TIMEOUT_MS = 5_000L
        private const val DEFAULT_SURVEY_SECONDS = 1_800L
        private const val ESTIMATED_AGGREGATE_BYTES = 128L
        private const val HACKRF_VENDOR_ID = 0x1d50
        private val HACKRF_PRODUCT_IDS = setOf(0x6089, 0x604b, 0xcc15)
    }
}
