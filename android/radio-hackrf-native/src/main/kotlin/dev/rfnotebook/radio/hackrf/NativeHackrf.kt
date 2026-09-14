package dev.rfnotebook.radio.hackrf

internal object NativeHackrf {
    init { System.loadLibrary("rfnotebook_radio") }

    external fun nativeInitialize(): Int
    external fun nativeOpen(fileDescriptor: Int): Long
    external fun nativeDeviceInfo(handle: Long): String?
    external fun nativeStartRx(
        handle: Long, centerFrequencyHz: Long, sampleRateHz: Int, basebandFilterHz: Int,
        lnaGainDb: Int, vgaGainDb: Int, rfAmpEnabled: Boolean, antennaPowerEnabled: Boolean,
    ): Int
    external fun nativeStartSweep(
        handle: Long, rangeEdgesHz: LongArray, binWidthHz: Int, sampleRateHz: Int,
        basebandFilterHz: Int, lnaGainDb: Int, vgaGainDb: Int,
        rfAmpEnabled: Boolean, antennaPowerEnabled: Boolean,
    ): Int
    external fun nativeStats(handle: Long): LongArray
    external fun nativePollBuffer(handle: Long): ByteArray?
    external fun nativeStop(handle: Long): Int
    external fun nativeClose(handle: Long)
}
