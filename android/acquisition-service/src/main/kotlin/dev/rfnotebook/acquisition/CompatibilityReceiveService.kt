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
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.rfnotebook.radio.api.RxConfig
import dev.rfnotebook.radio.api.SampleSink
import dev.rfnotebook.radio.api.SweepConfig
import dev.rfnotebook.radio.api.SweepSink
import dev.rfnotebook.radio.hackrf.AndroidHackrfRadio
import dev.rfnotebook.radio.hackrf.NativeRadioSession
import dev.rfnotebook.signal.processing.HackrfSweepProcessor
import dev.rfnotebook.signal.processing.SweepFrameParser
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.round
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

data class CompatibilityReceiveState(
    val phase: String = "idle",
    val sampleRateHz: Int = 0,
    val bytes: Long = 0,
    val bytesPerSecond: Long = 0,
    val elapsedMillis: Long = 0,
    val expectedRxBytes: Long = 0,
    val callbackErrors: Long = 0,
    val droppedBuffers: Long = 0,
    val device: String = "—",
    val locationAccuracyMeters: Float? = null,
    val locationAgeMillis: Long? = null,
    val sweepFrames: Long = 0,
    val malformedSweepBlocks: Long = 0,
    val detail: String = "",
)

object CompatibilityReceiveStatus {
    private val mutable = MutableStateFlow(CompatibilityReceiveState())
    val state = mutable.asStateFlow()
    internal fun update(value: CompatibilityReceiveState) { mutable.value = value }
}

internal fun hasAnyLocationPermission(coarseGranted: Boolean, fineGranted: Boolean): Boolean =
    coarseGranted || fineGranted

class CompatibilityReceiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acquisitionJob: Job? = null
    @Volatile private var latestLocation: Location? = null
    @Volatile private var stopReason = "Stopped by user"
    private var foregroundReady = false
    private var detachReceiverRegistered = false
    private val locationManager by lazy { getSystemService(LocationManager::class.java) }
    private val locationListener = LocationListener { latestLocation = it }
    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                stopReason = "USB detached; native session closed"
                acquisitionJob?.cancel()
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopReason = "Stopped by user"
            acquisitionJob?.cancel()
            stopSelf()
            return START_NOT_STICKY
        }
        ensureBootstrapForeground()
        if (!hasLocationPermission()) {
            CompatibilityReceiveStatus.update(CompatibilityReceiveState("error", detail = LOCATION_PERMISSION_REQUIRED))
            stopSelf(startId)
            return START_NOT_STICKY
        }
        promoteAcquisitionForeground()
        if (acquisitionJob?.isActive != true) {
            val rate = intent?.getIntExtra(EXTRA_SAMPLE_RATE_HZ, DEFAULT_SAMPLE_RATE_HZ) ?: DEFAULT_SAMPLE_RATE_HZ
            val sweep = intent?.getBooleanExtra(EXTRA_SWEEP, false) ?: false
            acquisitionJob = scope.launch { runReceiveProbe(rate, sweep) }
        }
        return START_NOT_STICKY
    }

    private fun ensureBootstrapForeground() {
        if (foregroundReady) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Compatibility receive test", NotificationManager.IMPORTANCE_LOW),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification("Checking acquisition permissions"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification("Checking acquisition permissions"))
        }
        foregroundReady = true
    }

    private fun promoteAcquisitionForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification("Preparing receive-only test"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        if (detachReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            detachReceiver,
            IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        detachReceiverRegistered = true
    }

    private suspend fun runReceiveProbe(rate: Int, sweep: Boolean) {
        var session: NativeRadioSession? = null
        val sweepFrames = AtomicLong()
        val malformedSweepBlocks = AtomicLong()
        var sweepFixtureCaptured = false
        try {
            CompatibilityReceiveStatus.update(CompatibilityReceiveState("opening", rate))
            val radio = AndroidHackrfRadio(this)
            val devices = radio.enumerate()
            require(devices.size == 1) { "Attach and permit exactly one supported HackRF" }
            session = radio.open(devices.single().serialSuffix) as NativeRadioSession
            val info = session.deviceInfo()
            val deviceLabel = "${info.boardName} ${info.hardwareRevision}; FW ${info.firmwareVersion}; API ${info.apiVersion}; …${info.serialSuffix}"
            startLocationUpdates()
            if (sweep) {
                session.startSweep(
                    SweepConfig(SWEEP_START_FREQUENCY_HZ, SWEEP_END_FREQUENCY_HZ, SWEEP_BIN_WIDTH_HZ, rate),
                    SweepSink { raw, _ ->
                        val processed = HackrfSweepProcessor.process(raw, rate, SWEEP_BIN_WIDTH_HZ)
                        sweepFrames.addAndGet(processed.frames.size.toLong())
                        malformedSweepBlocks.addAndGet(processed.malformedBlocks.toLong())
                        if (!sweepFixtureCaptured) {
                            processed.frames.firstOrNull()?.let { frame ->
                                val sanitized = frame.copy(
                                    bins = frame.bins.map { it.copy(powerDbfs = round(it.powerDbfs)) },
                                )
                                getExternalFilesDir(null)?.let { directory ->
                                    File(directory, SWEEP_FIXTURE_FILE).writeBytes(SweepFrameParser.encode(sanitized))
                                    sweepFixtureCaptured = true
                                }
                            }
                        }
                    },
                )
            } else {
                session.startRx(RxConfig(TEST_CENTER_FREQUENCY_HZ, rate), SampleSink { _, _ -> })
            }
            var priorBytes = 0L
            val startedAt = SystemClock.elapsedRealtime()
            var priorTime = startedAt
            while (scope.isActive) {
                delay(STATS_INTERVAL_MS)
                val now = SystemClock.elapsedRealtime()
                val stats = session.stats()
                check(stats.streaming) { "Native receive stopped unexpectedly" }
                val elapsedMs = (now - priorTime).coerceAtLeast(1L)
                val throughput = (stats.byteCount - priorBytes) * 1_000L / elapsedMs
                val totalElapsedMs = now - startedAt
                priorBytes = stats.byteCount
                priorTime = now
                val location = latestLocation
                CompatibilityReceiveStatus.update(
                    CompatibilityReceiveState(
                        if (sweep) "sweeping" else "receiving",
                        rate,
                        stats.byteCount,
                        throughput,
                        totalElapsedMs,
                        if (sweep) 0 else rate.toLong() * 2L * totalElapsedMs / 1_000L,
                        stats.callbackErrorCount,
                        stats.droppedBufferCount,
                        deviceLabel,
                        location?.accuracy,
                        location?.let { SystemClock.elapsedRealtime() - it.elapsedRealtimeNanos / 1_000_000L },
                        sweepFrames.get(),
                        malformedSweepBlocks.get(),
                    ),
                )
                getSystemService(NotificationManager::class.java).notify(
                    NOTIFICATION_ID,
                    notification("RX ${rate / 1_000_000} MS/s • ${stats.byteCount} bytes • ${stats.droppedBufferCount} dropped"),
                )
            }
        } catch (_: CancellationException) {
            CompatibilityReceiveStatus.update(CompatibilityReceiveState(detail = stopReason))
        } catch (error: Throwable) {
            CompatibilityReceiveStatus.update(CompatibilityReceiveState("error", rate, detail = error.message ?: error.javaClass.simpleName))
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                notification("Receive probe failed: ${error.message ?: error.javaClass.simpleName}"),
            )
        } finally {
            runCatching { locationManager.removeUpdates(locationListener) }
            session?.close()
        }
    }

    private fun startLocationUpdates() {
        val fine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        require(hasAnyLocationPermission(coarse, fine)) { LOCATION_PERMISSION_REQUIRED }
        val provider = if (fine) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        locationManager.requestLocationUpdates(provider, STATS_INTERVAL_MS, 0f, locationListener, Looper.getMainLooper())
    }

    private fun hasLocationPermission(): Boolean = hasAnyLocationPermission(
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
    )

    private fun notification(text: String): Notification {
        val stopIntent = Intent(this, CompatibilityReceiveService::class.java).setAction(ACTION_STOP)
        val stop = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_rx)
            .setContentTitle("RF Field Notebook — receive only")
            .setContentText(text)
            .setOngoing(true)
            .addAction(0, "Stop", stop)
            .build()
    }

    override fun onDestroy() {
        acquisitionJob?.cancel()
        scope.cancel()
        if (detachReceiverRegistered) runCatching { unregisterReceiver(detachReceiver) }
        if (CompatibilityReceiveStatus.state.value.phase != "error") {
            CompatibilityReceiveStatus.update(CompatibilityReceiveState(detail = stopReason))
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "dev.rfnotebook.action.STOP_COMPATIBILITY_RECEIVE"
        const val EXTRA_SAMPLE_RATE_HZ = "sample-rate-hz"
        const val EXTRA_SWEEP = "sweep"
        private const val CHANNEL = "m0-acquisition"
        private const val NOTIFICATION_ID = 100
        private const val DEFAULT_SAMPLE_RATE_HZ = 2_000_000
        private const val TEST_CENTER_FREQUENCY_HZ = 100_000_000L
        private const val SWEEP_START_FREQUENCY_HZ = 88_000_000L
        private const val SWEEP_END_FREQUENCY_HZ = 108_000_000L
        private const val SWEEP_BIN_WIDTH_HZ = 100_000
        private const val SWEEP_FIXTURE_FILE = "m0-sweep-frame.bin"
        private const val STATS_INTERVAL_MS = 500L
        private const val LOCATION_PERMISSION_REQUIRED = "Location permission is required for the combined USB/location probe"
    }
}
