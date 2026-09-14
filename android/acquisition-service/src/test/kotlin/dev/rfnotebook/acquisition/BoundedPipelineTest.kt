package dev.rfnotebook.acquisition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class BoundedPipelineTest {
    @Test fun `full queue rejects and counts every dropped unit`() {
        val queue = BoundedStage<Int>(PipelineStage.NATIVE, capacity = 2)

        assertTrue(queue.offer(1))
        assertTrue(queue.offer(2))
        assertFalse(queue.offer(3))
        assertFalse(queue.offer(4))

        assertEquals(2, queue.depth)
        assertEquals(2, queue.highWaterMark)
        assertEquals(2L, queue.droppedCount)
    }

    @Test fun `health reports losses from every bounded stage`() {
        val native = BoundedStage<Int>(PipelineStage.NATIVE, 1)
        val processing = BoundedStage<Int>(PipelineStage.PROCESSING, 1)
        val persistence = BoundedStage<Int>(PipelineStage.PERSISTENCE, 1)
        listOf(native, processing, persistence).forEach { queue -> queue.offer(1); queue.offer(2) }
        val counters = AcquisitionHealthCounters().apply {
            increment(HealthCounter.MALFORMED_FRAME, 2)
            increment(HealthCounter.NATIVE_OVERRUN, 3)
            increment(HealthCounter.STALE_FIX, 4)
            increment(HealthCounter.SERVICE_GAP, 5)
        }

        val health = counters.snapshot(native, processing, persistence)

        assertEquals(1L, health.droppedNativeUnits)
        assertEquals(1L, health.droppedProcessingUnits)
        assertEquals(1L, health.droppedPersistenceUnits)
        assertEquals(2L, health.malformedFrameCount)
        assertEquals(3L, health.overrunCount)
        assertEquals(4L, health.staleFixCount)
        assertEquals(5L, health.serviceGapCount)
        assertTrue(health.hasDataLoss)
    }

    @Test fun `storage guard preserves fixed reserve`() {
        val denied = StorageGuard.assess(300, estimatedSurveyBytes = 100, reserveBytes = 256)
        val allowed = StorageGuard.assess(400, estimatedSurveyBytes = 100, reserveBytes = 256)

        assertFalse(denied.canStart)
        assertTrue(allowed.canStart)
    }

    @Test fun `shutdown counts queued units that cannot be drained`() {
        val queue = BoundedStage<Int>(PipelineStage.PERSISTENCE, 3)
        queue.offer(1)
        queue.offer(2)

        assertEquals(2, queue.discardPending())
        assertEquals(0, queue.depth)
        assertEquals(2L, queue.droppedCount)
    }

    @Test fun `poll removes an item without counting it as dropped`() {
        val queue = BoundedStage<Int>(PipelineStage.PERSISTENCE, 2)
        queue.offer(7)

        assertEquals(7, queue.poll())
        assertEquals(0, queue.depth)
        assertEquals(0L, queue.droppedCount)
        assertEquals(null, queue.poll())
    }

    @Test fun `closing a queue wakes a receiver without throwing`() = runBlocking {
        val queue = BoundedStage<Int>(PipelineStage.NATIVE, 1)

        queue.close()
        queue.close()

        assertEquals(null, queue.receiveOrNull())
        assertEquals(0, queue.depth)
        assertEquals(0L, queue.droppedCount)
    }

    @Test fun `pressure injection creates a user-visible warning`() {
        val native = BoundedStage<Int>(PipelineStage.NATIVE, 1)
        val processing = BoundedStage<Int>(PipelineStage.PROCESSING, 1)
        val persistence = BoundedStage<Int>(PipelineStage.PERSISTENCE, 1)
        native.offer(1)
        native.offer(2)
        val health = AcquisitionHealthCounters().snapshot(native, processing, persistence)

        val warning = HealthWarningPolicy.warning(
            health,
            StorageGuard.DEFAULT_RESERVE_BYTES + 1,
            batteryPercent = 80,
            thermalStatus = 0,
            moderateThermalStatus = 2,
        )

        assertTrue(warning!!.contains("Acquisition loss recorded"))
    }
}
