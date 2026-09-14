package dev.rfnotebook.app

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import dev.rfnotebook.acquisition.AcquisitionSpikeService
import dev.rfnotebook.acquisition.AcquisitionSpikeStatus
import dev.rfnotebook.maps.MapPrototypeView
import dev.rfnotebook.maps.OfflineRegionSpike
import dev.rfnotebook.storage.SyntheticObservations

class MainActivity : ComponentActivity() {
    private var usbPermissionState by mutableStateOf("unknown")
    private var usbTopologyRevision by mutableIntStateOf(0)
    private var selectedSampleRateHz = 2_000_000
    private var selectedSweep = false
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, AcquisitionSpikeService::class.java)
                    .putExtra(AcquisitionSpikeService.EXTRA_SAMPLE_RATE_HZ, selectedSampleRateHz)
                    .putExtra(AcquisitionSpikeService.EXTRA_SWEEP, selectedSweep),
            )
        }
    }
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_USB_PERMISSION) {
                usbPermissionState = if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) "granted" else "denied"
            }
        }
    }
    private val usbTopologyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED ||
                intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED
            ) {
                usbPermissionState = "unknown"
                usbTopologyRevision++
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            usbPermissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val usbTopologyFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(this, usbTopologyReceiver, usbTopologyFilter, ContextCompat.RECEIVER_EXPORTED)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { SpikeScreen() } } }
    }

    override fun onDestroy() {
        unregisterReceiver(usbPermissionReceiver)
        unregisterReceiver(usbTopologyReceiver)
        super.onDestroy()
    }

    @Composable private fun SpikeScreen() {
        val usb = getSystemService(UsbManager::class.java)
        val hackrf = remember(usbTopologyRevision) {
            usb.deviceList.values.firstOrNull { it.vendorId == 0x1d50 && it.productId == 0x6089 }
        }
        usbPermissionState = when {
            hackrf == null -> "HackRF not attached"
            usb.hasPermission(hackrf) -> "granted"
            usbPermissionState == "unknown" -> "required"
            else -> usbPermissionState
        }
        val serialSuffix = if (hackrf != null && usb.hasPermission(hackrf)) runCatching { hackrf.serialNumber?.takeLast(6) }.getOrNull() else null
        var offlineState by remember { mutableStateOf("Not requested") }
        var selectedRate by remember { mutableStateOf(2_000_000) }
        val persistedObservations = remember { SyntheticObservations.loadPersisted(this@MainActivity) }
        val acquisition by AcquisitionSpikeStatus.state.collectAsState()
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("RF Field Notebook — RX only", style = MaterialTheme.typography.headlineSmall)
            Text("USB permission: $usbPermissionState")
            Text("Device: ${hackrf?.productName ?: "—"}; serial suffix: ${serialSuffix ?: "—"}")
            Text("Stream: ${acquisition.phase} • ${"%.2f".format(acquisition.bytesPerSecond / 1_000_000.0)} MB/s • bytes: ${acquisition.bytes}")
            Text("Elapsed: ${acquisition.elapsedMillis / 1_000}s • expected RX bytes: ${acquisition.expectedRxBytes} • delta: ${acquisition.bytes - acquisition.expectedRxBytes}")
            Text("Dropped JVM buffers: ${acquisition.droppedBuffers} • native callback errors: ${acquisition.callbackErrors}")
            Text("Sweep power frames: ${acquisition.sweepFrames} • malformed device blocks: ${acquisition.malformedSweepBlocks}")
            Text("Native identity: ${acquisition.device}${if (acquisition.detail.isNotEmpty()) "; ${acquisition.detail}" else ""}")
            Text("Location: accuracy ${acquisition.locationAccuracyMeters?.let { "%.1f m".format(it) } ?: "—"}; age ${acquisition.locationAgeMillis?.let { "$it ms" } ?: "—"}")
            Column {
                listOf(2_000_000, 4_000_000, 8_000_000).forEach { rate ->
                    Button(onClick = { selectedRate = rate }) {
                        Text(if (selectedRate == rate) "${rate / 1_000_000} MS/s ✓" else "${rate / 1_000_000} MS/s")
                    }
                }
            }
            Column {
                if (hackrf != null && !usb.hasPermission(hackrf)) {
                    Button(onClick = {
                        val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
                        val pending = PendingIntent.getBroadcast(this@MainActivity, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                        usb.requestPermission(hackrf, pending)
                    }) { Text("Request USB permission") }
                }
                Button(onClick = {
                    selectedSampleRateHz = selectedRate
                    selectedSweep = false
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
                }) { Text("Start visible RX test") }
                Button(onClick = {
                    selectedSampleRateHz = selectedRate
                    selectedSweep = true
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
                }) { Text("Start sweep test") }
                Button(onClick = { OfflineRegionSpike.downloadOrReopen(this@MainActivity) { offlineState = it } }) { Text("Download/reopen offline region") }
            }
            Text("Offline region: $offlineState; map: observed relative strength; accuracy 6–22 m")
            AndroidView(
                factory = { MapPrototypeView(it, persistedObservations) },
                modifier = Modifier.fillMaxWidth().height(360.dp),
            )
        }
    }

    companion object { private const val ACTION_USB_PERMISSION = "dev.rfnotebook.action.USB_PERMISSION" }
}
