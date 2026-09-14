package dev.rfnotebook.acquisition

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel

enum class PipelineStage { NATIVE, PROCESSING, PERSISTENCE }

class BoundedStage<T>(
    val stage: PipelineStage,
    val capacity: Int,
) {
    private val channel = Channel<T>(capacity)
    private val depthCounter = AtomicInteger()
    private val highWaterCounter = AtomicInteger()
    private val droppedCounter = AtomicLong()

    init { require(capacity > 0) }

    val depth: Int get() = depthCounter.get()
    val highWaterMark: Int get() = highWaterCounter.get()
    val droppedCount: Long get() = droppedCounter.get()

    fun offer(value: T): Boolean {
        val proposedDepth = depthCounter.incrementAndGet()
        val accepted = channel.trySend(value).isSuccess
        if (!accepted) {
            depthCounter.decrementAndGet()
            droppedCounter.incrementAndGet()
            return false
        }
        highWaterCounter.accumulateAndGet(proposedDepth, ::maxOf)
        return true
    }

    suspend fun receive(): T {
        val value = channel.receive()
        depthCounter.decrementAndGet()
        return value
    }

    fun discardPending(): Int {
        var discarded = 0
        while (channel.tryReceive().isSuccess) {
            depthCounter.decrementAndGet()
            droppedCounter.incrementAndGet()
            discarded++
        }
        return discarded
    }

    fun close() = channel.close()
}

enum class HealthCounter {
    MALFORMED_FRAME,
    NATIVE_OVERRUN,
    STALE_FIX,
    SERVICE_GAP,
}

class AcquisitionHealthCounters {
    private val malformedFrames = AtomicLong()
    private val nativeOverruns = AtomicLong()
    private val staleFixes = AtomicLong()
    private val serviceGaps = AtomicLong()

    fun increment(counter: HealthCounter, amount: Long = 1) {
        require(amount >= 0)
        when (counter) {
            HealthCounter.MALFORMED_FRAME -> malformedFrames.addAndGet(amount)
            HealthCounter.NATIVE_OVERRUN -> nativeOverruns.addAndGet(amount)
            HealthCounter.STALE_FIX -> staleFixes.addAndGet(amount)
            HealthCounter.SERVICE_GAP -> serviceGaps.addAndGet(amount)
        }
    }

    fun snapshot(native: BoundedStage<*>, processing: BoundedStage<*>, persistence: BoundedStage<*>) =
        AcquisitionHealth(
            nativeQueueDepth = native.depth,
            processingQueueDepth = processing.depth,
            persistenceQueueDepth = persistence.depth,
            droppedNativeUnits = native.droppedCount,
            droppedProcessingUnits = processing.droppedCount,
            droppedPersistenceUnits = persistence.droppedCount,
            malformedFrameCount = malformedFrames.get(),
            overrunCount = nativeOverruns.get(),
            staleFixCount = staleFixes.get(),
            serviceGapCount = serviceGaps.get(),
        )
}

data class AcquisitionHealth(
    val nativeQueueDepth: Int,
    val processingQueueDepth: Int,
    val persistenceQueueDepth: Int,
    val droppedNativeUnits: Long,
    val droppedProcessingUnits: Long,
    val droppedPersistenceUnits: Long,
    val malformedFrameCount: Long,
    val overrunCount: Long,
    val staleFixCount: Long,
    val serviceGapCount: Long,
) {
    val hasDataLoss: Boolean
        get() = droppedNativeUnits + droppedProcessingUnits + droppedPersistenceUnits +
            malformedFrameCount + overrunCount + serviceGapCount > 0
}

object HealthWarningPolicy {
    fun warning(
        health: AcquisitionHealth,
        availableStorageBytes: Long,
        batteryPercent: Int?,
        thermalStatus: Int,
        moderateThermalStatus: Int,
    ): String? = buildList {
        if (health.hasDataLoss) add("Acquisition loss recorded")
        if (availableStorageBytes < StorageGuard.DEFAULT_RESERVE_BYTES) add("Storage reserve is low")
        if (batteryPercent != null && batteryPercent <= 15) add("Battery is low; orderly Stop is available")
        if (thermalStatus >= moderateThermalStatus) add("Thermal pressure detected; measurement settings remain fixed")
    }.joinToString("; ").ifBlank { null }
}

data class StorageAssessment(
    val canStart: Boolean,
    val availableBytes: Long,
    val estimatedSurveyBytes: Long,
    val reserveBytes: Long,
    val explanation: String,
)

object StorageGuard {
    const val DEFAULT_RESERVE_BYTES = 256L * 1024L * 1024L

    fun assess(
        availableBytes: Long,
        estimatedSurveyBytes: Long,
        reserveBytes: Long = DEFAULT_RESERVE_BYTES,
    ): StorageAssessment {
        require(availableBytes >= 0 && estimatedSurveyBytes >= 0 && reserveBytes >= 0)
        val required = estimatedSurveyBytes + reserveBytes
        val canStart = required >= estimatedSurveyBytes && availableBytes >= required
        return StorageAssessment(
            canStart,
            availableBytes,
            estimatedSurveyBytes,
            reserveBytes,
            if (canStart) "Storage check passed" else "Need $required bytes including reserve; $availableBytes bytes available",
        )
    }
}
