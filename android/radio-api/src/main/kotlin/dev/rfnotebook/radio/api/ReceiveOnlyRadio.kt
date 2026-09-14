package dev.rfnotebook.radio.api

data class RadioDescriptor(val serialSuffix: String, val product: String)

data class RadioDeviceInfo(
    val boardName: String,
    val firmwareVersion: String,
    val apiVersion: String,
    val hardwareRevision: String,
    val serialSuffix: String,
)

enum class RadioErrorCode {
    DEVICE_NOT_FOUND,
    PERMISSION_REQUIRED,
    AMBIGUOUS_SERIAL_SUFFIX,
    SESSION_ALREADY_OPEN,
    OPEN_FAILED,
    INCOMPATIBLE_FIRMWARE,
    UNSUPPORTED_CONFIGURATION,
    DETACHED,
    STALLED,
    NATIVE_FAILURE,
    CLOSED,
}

class RadioException(
    val code: RadioErrorCode,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

data class RadioCompatibility(
    val compatible: Boolean,
    val explanation: String,
)

object HackrfCompatibilityPolicy {
    private const val SUPPORTED_USB_API_MAJOR = 1

    fun evaluate(info: RadioDeviceInfo): RadioCompatibility {
        val major = info.apiVersion.substringBefore('.').toIntOrNull()
        return when {
            info.firmwareVersion.isBlank() -> RadioCompatibility(false, "HackRF firmware version was not reported")
            major != SUPPORTED_USB_API_MAJOR -> RadioCompatibility(
                false,
                "HackRF USB API ${info.apiVersion} is unsupported; expected major version $SUPPORTED_USB_API_MAJOR",
            )
            else -> RadioCompatibility(true, "Firmware ${info.firmwareVersion}; USB API ${info.apiVersion}")
        }
    }
}

data class RxConfig(
    val centerFrequencyHz: Long,
    val sampleRateHz: Int,
    val basebandFilterHz: Int = 0,
    val lnaGainDb: Int = 0,
    val vgaGainDb: Int = 0,
    val rfAmpEnabled: Boolean = false,
    val antennaPowerEnabled: Boolean = false,
)

data class SweepConfig(
    val startFrequencyHz: Long,
    val endFrequencyHz: Long,
    val binWidthHz: Int,
    val sampleRateHz: Int,
    val basebandFilterHz: Int = 0,
    val lnaGainDb: Int = 0,
    val vgaGainDb: Int = 0,
    val rfAmpEnabled: Boolean = false,
    val antennaPowerEnabled: Boolean = false,
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
        requireSupported(config.centerFrequencyHz in MIN_FREQUENCY_HZ..MAX_FREQUENCY_HZ) {
            "Center frequency ${config.centerFrequencyHz} Hz is outside $MIN_FREQUENCY_HZ..$MAX_FREQUENCY_HZ Hz"
        }
        requireSupported(config.sampleRateHz in supportedSampleRatesHz) {
            "Sample rate ${config.sampleRateHz} Hz is unsupported; choose ${supportedSampleRatesHz.sorted().joinToString()} Hz"
        }
        requireSettings(config.sampleRateHz, config.basebandFilterHz, config.lnaGainDb, config.vgaGainDb)
    }

    fun requireValid(config: SweepConfig) {
        requireSupported(config.startFrequencyHz in MIN_FREQUENCY_HZ until config.endFrequencyHz) {
            "Sweep start must be within the device range and below its end"
        }
        requireSupported(config.endFrequencyHz <= MAX_FREQUENCY_HZ) { "Sweep end exceeds the device range" }
        requireSupported(config.binWidthHz > 0) { "Sweep bin width must be positive" }
        requireSupported(config.sampleRateHz in supportedSampleRatesHz) {
            "Sample rate ${config.sampleRateHz} Hz is unsupported; choose ${supportedSampleRatesHz.sorted().joinToString()} Hz"
        }
        requireSettings(config.sampleRateHz, config.basebandFilterHz, config.lnaGainDb, config.vgaGainDb)
    }

    private fun requireSettings(sampleRateHz: Int, basebandFilterHz: Int, lnaGainDb: Int, vgaGainDb: Int) {
        val validFilter = basebandFilterHz == 0 || when (sampleRateHz) {
            2_000_000 -> basebandFilterHz == 1_750_000
            4_000_000 -> basebandFilterHz == 3_500_000
            8_000_000 -> basebandFilterHz == 7_000_000
            else -> false
        }
        requireSupported(validFilter) { "Baseband filter $basebandFilterHz Hz is invalid for sample rate $sampleRateHz Hz" }
        requireSupported(lnaGainDb in 0..40 && lnaGainDb % 8 == 0) { "LNA gain must be 0–40 dB in 8 dB steps" }
        requireSupported(vgaGainDb in 0..62 && vgaGainDb % 2 == 0) { "VGA gain must be 0–62 dB in 2 dB steps" }
    }

    private inline fun requireSupported(value: Boolean, message: () -> String) {
        if (!value) throw RadioException(RadioErrorCode.UNSUPPORTED_CONFIGURATION, message())
    }
}
