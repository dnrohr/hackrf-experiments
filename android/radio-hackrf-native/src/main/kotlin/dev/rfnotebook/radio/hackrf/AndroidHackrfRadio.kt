package dev.rfnotebook.radio.hackrf

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import dev.rfnotebook.radio.api.RadioDescriptor
import dev.rfnotebook.radio.api.RadioDeviceInfo
import dev.rfnotebook.radio.api.RadioLimits
import dev.rfnotebook.radio.api.RadioSession
import dev.rfnotebook.radio.api.ReceiveOnlyRadio
import dev.rfnotebook.radio.api.RxConfig
import dev.rfnotebook.radio.api.SampleSink
import dev.rfnotebook.radio.api.SweepConfig
import dev.rfnotebook.radio.api.SweepSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class NativeReceiveStats(
    val byteCount: Long,
    val callbackErrorCount: Long,
    val droppedBufferCount: Long,
    val streaming: Boolean,
)

class AndroidHackrfRadio(context: Context) : ReceiveOnlyRadio {
    private val usbManager = context.applicationContext.getSystemService(UsbManager::class.java)

    init { HackrfRuntime.ensureInitialized() }

    override suspend fun enumerate(): List<RadioDescriptor> = supportedDevices().map { device ->
        RadioDescriptor(serialSuffix(device), device.productName ?: "HackRF")
    }

    override suspend fun open(serialSuffix: String): RadioSession {
        val matches = supportedDevices().filter { usbManager.hasPermission(it) && serialSuffix(it).endsWith(serialSuffix) }
        require(matches.size == 1) { "Expected exactly one permitted HackRF matching the serial suffix" }
        val connection = requireNotNull(usbManager.openDevice(matches.single())) { "Android could not open the permitted HackRF" }
        val handle = NativeHackrf.nativeOpen(connection.fileDescriptor)
        if (handle == 0L) { connection.close(); error("libhackrf could not open the Android USB descriptor") }
        return NativeRadioSession(handle, connection, serialSuffix(matches.single()))
    }

    private fun supportedDevices(): List<UsbDevice> = usbManager.deviceList.values.filter {
        it.vendorId == HACKRF_VENDOR_ID && it.productId in HACKRF_PRODUCT_IDS
    }

    private fun serialSuffix(device: UsbDevice): String =
        if (usbManager.hasPermission(device)) runCatching { device.serialNumber?.takeLast(8).orEmpty() }.getOrDefault("") else ""

    companion object {
        private const val HACKRF_VENDOR_ID = 0x1d50
        private val HACKRF_PRODUCT_IDS = setOf(0x6089, 0x604b, 0xcc15)
    }
}

class NativeRadioSession internal constructor(
    private var handle: Long,
    private val connection: UsbDeviceConnection,
    private val serialSuffix: String,
) : RadioSession {
    private val deliveryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var deliveryJob: Job? = null

    override suspend fun deviceInfo(): RadioDeviceInfo {
        val parts = requireNotNull(NativeHackrf.nativeDeviceInfo(requireOpen())).split('\n')
        check(parts.size == 4) { "Malformed native device information" }
        return RadioDeviceInfo(parts[0], parts[1], parts[2], parts[3], serialSuffix)
    }

    override suspend fun startRx(config: RxConfig, sink: SampleSink) {
        RadioLimits.requireValid(config)
        stopDelivery()
        checkNative(NativeHackrf.nativeStartRx(requireOpen(), config.centerFrequencyHz, config.sampleRateHz), "start RX")
        deliveryJob = deliveryScope.launch { deliverBuffers { buffer, timestamp -> sink.onSamples(buffer, timestamp) } }
    }

    override suspend fun startSweep(config: SweepConfig, sink: SweepSink) {
        RadioLimits.requireValid(config)
        stopDelivery()
        checkNative(NativeHackrf.nativeStartSweep(requireOpen(), config.startFrequencyHz, config.endFrequencyHz, config.binWidthHz, config.sampleRateHz), "start sweep")
        deliveryJob = deliveryScope.launch { deliverBuffers { buffer, timestamp -> sink.onFrame(buffer, timestamp) } }
    }

    fun stats(): NativeReceiveStats {
        val values = NativeHackrf.nativeStats(requireOpen())
        check(values.size == 4) { "Malformed native statistics" }
        return NativeReceiveStats(values[0], values[1], values[2], values[3] != 0L)
    }

    override suspend fun stop() {
        stopDelivery()
        checkNative(NativeHackrf.nativeStop(requireOpen()), "stop receive")
    }

    override fun close() {
        runBlocking { stopDelivery() }
        deliveryScope.cancel()
        val current = synchronized(this) { handle.also { handle = 0 } }
        if (current != 0L) NativeHackrf.nativeClose(current)
        connection.close()
    }

    private suspend fun stopDelivery() {
        deliveryJob?.cancelAndJoin()
        deliveryJob = null
    }

    private suspend fun deliverBuffers(consumer: (ByteArray, Long) -> Unit) {
        while (currentCoroutineContext().isActive) {
            val buffer = NativeHackrf.nativePollBuffer(requireOpen())
            if (buffer == null) delay(POLL_INTERVAL_MS) else consumer(buffer, android.os.SystemClock.elapsedRealtimeNanos())
        }
    }

    private fun requireOpen(): Long = handle.also { check(it != 0L) { "Radio session is closed" } }

    private companion object { const val POLL_INTERVAL_MS = 2L }
}

private fun checkNative(result: Int, action: String) { check(result == 0) { "Failed to $action (libhackrf error $result)" } }

private object HackrfRuntime {
    private val initialized = lazy { checkNative(NativeHackrf.nativeInitialize(), "initialize libhackrf") }
    fun ensureInitialized() { initialized.value }
}
