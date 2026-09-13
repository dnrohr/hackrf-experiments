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
import android.hardware.usb.UsbManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import dev.rfnotebook.radio.api.RxConfig
import dev.rfnotebook.radio.api.SampleSink
import dev.rfnotebook.radio.api.SweepConfig
import dev.rfnotebook.radio.api.SweepSink
import dev.rfnotebook.radio.hackrf.AndroidHackrfRadio
import dev.rfnotebook.radio.hackrf.NativeRadioSession
import dev.rfnotebook.signal.processing.HackrfSweepProcessor
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

data class AcquisitionSpikeState(
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

object AcquisitionSpikeStatus {
    private val mutable = MutableStateFlow(AcquisitionSpikeState())
    val state = mutable.asStateFlow()
    internal fun update(value: AcquisitionSpikeState) { mutable.value = value }
}

class AcquisitionSpikeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acquisitionJob: Job? = null
    @Volatile private var latestLocation: Location? = null
    @Volatile private var stopReason = "Stopped by user"
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
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "M0 acquisition", NotificationManager.IMPORTANCE_LOW),
        )
        ContextCompat.registerReceiver(
            this,
            detachReceiver,
            IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        startForeground(NOTIFICATION_ID, notification("Preparing receive-only test"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopReason = "Stopped by user"
            acquisitionJob?.cancel()
            stopSelf()
            return START_NOT_STICKY
        }
        if (acquisitionJob?.isActive != true) {
            val rate = intent?.getIntExtra(EXTRA_SAMPLE_RATE_HZ, DEFAULT_SAMPLE_RATE_HZ) ?: DEFAULT_SAMPLE_RATE_HZ
            val sweep = intent?.getBooleanExtra(EXTRA_SWEEP, false) ?: false
            acquisitionJob = scope.launch { runReceiveProbe(rate, sweep) }
        }
        return START_NOT_STICKY
    }

    private suspend fun runReceiveProbe(rate: Int, sweep: Boolean) {
        var session: NativeRadioSession? = null
        val sweepFrames = AtomicLong()
        val malformedSweepBlocks = AtomicLong()
        try {
            AcquisitionSpikeStatus.update(AcquisitionSpikeState("opening", rate))
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
                AcquisitionSpikeStatus.update(
                    AcquisitionSpikeState(
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
            AcquisitionSpikeStatus.update(AcquisitionSpikeState(detail = stopReason))
        } catch (error: Throwable) {
            AcquisitionSpikeStatus.update(AcquisitionSpikeState("error", rate, detail = error.message ?: error.javaClass.simpleName))
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
        require(fine || coarse) { "Location permission is required for the combined USB/location probe" }
        val provider = if (fine) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        locationManager.requestLocationUpdates(provider, STATS_INTERVAL_MS, 0f, locationListener, Looper.getMainLooper())
    }

    private fun notification(text: String): Notification {
        val stopIntent = Intent(this, AcquisitionSpikeService::class.java).setAction(ACTION_STOP)
        val stop = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_rx)
            .setContentTitle("RF Field Notebook — RX only")
            .setContentText(text)
            .setOngoing(true)
            .addAction(0, "Stop", stop)
            .build()
    }

    override fun onDestroy() {
        acquisitionJob?.cancel()
        scope.cancel()
        runCatching { unregisterReceiver(detachReceiver) }
        if (AcquisitionSpikeStatus.state.value.phase != "error") {
            AcquisitionSpikeStatus.update(AcquisitionSpikeState(detail = stopReason))
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "dev.rfnotebook.action.STOP_SPIKE"
        const val EXTRA_SAMPLE_RATE_HZ = "sample-rate-hz"
        const val EXTRA_SWEEP = "sweep"
        private const val CHANNEL = "m0-acquisition"
        private const val NOTIFICATION_ID = 100
        private const val DEFAULT_SAMPLE_RATE_HZ = 2_000_000
        private const val TEST_CENTER_FREQUENCY_HZ = 100_000_000L
        private const val SWEEP_START_FREQUENCY_HZ = 88_000_000L
        private const val SWEEP_END_FREQUENCY_HZ = 108_000_000L
        private const val SWEEP_BIN_WIDTH_HZ = 100_000
        private const val STATS_INTERVAL_MS = 500L
    }
}
