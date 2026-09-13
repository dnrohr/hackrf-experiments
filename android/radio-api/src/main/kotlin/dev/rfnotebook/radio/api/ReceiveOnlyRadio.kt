package dev.rfnotebook.radio.api

data class RadioDescriptor(val serialSuffix: String, val product: String)

data class RadioDeviceInfo(
    val boardName: String,
    val firmwareVersion: String,
    val apiVersion: String,
    val hardwareRevision: String,
    val serialSuffix: String,
)

data class RxConfig(val centerFrequencyHz: Long, val sampleRateHz: Int)

data class SweepConfig(
    val startFrequencyHz: Long,
    val endFrequencyHz: Long,
    val binWidthHz: Int,
    val sampleRateHz: Int,
)

fun interface SampleSink { fun onSamples(samples: ByteArray, monotonicNanos: Long) }
fun interface SweepSink { fun onFrame(frame: ByteArray, monotonicNanos: Long) }

interface ReceiveOnlyRadio {
    suspend fun enumerate(): List<RadioDescriptor>
    suspend fun open(serialSuffix: String): RadioSession
}

interface RadioSession : AutoCloseable {
    suspend fun deviceInfo(): RadioDeviceInfo
    suspend fun startSweep(config: SweepConfig, sink: SweepSink)
    suspend fun startRx(config: RxConfig, sink: SampleSink)
    suspend fun stop()
    override fun close()
}

object RadioLimits {
    const val MIN_FREQUENCY_HZ = 1_000_000L
    const val MAX_FREQUENCY_HZ = 6_000_000_000L
    val supportedSampleRatesHz = setOf(2_000_000, 4_000_000, 8_000_000)

    fun requireValid(config: RxConfig) {
        require(config.centerFrequencyHz in MIN_FREQUENCY_HZ..MAX_FREQUENCY_HZ)
        require(config.sampleRateHz in supportedSampleRatesHz)
    }

    fun requireValid(config: SweepConfig) {
        require(config.startFrequencyHz in MIN_FREQUENCY_HZ until config.endFrequencyHz)
        require(config.endFrequencyHz <= MAX_FREQUENCY_HZ)
        require(config.binWidthHz > 0)
        require(config.sampleRateHz in supportedSampleRatesHz)
    }
}
