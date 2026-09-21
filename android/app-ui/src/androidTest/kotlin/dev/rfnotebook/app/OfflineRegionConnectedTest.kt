package dev.rfnotebook.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.rfnotebook.maps.FieldOfflineRegionManager
import dev.rfnotebook.maps.OfflineBounds
import dev.rfnotebook.maps.OfflineRegionPhase
import dev.rfnotebook.maps.OfflineRegionProgress
import dev.rfnotebook.maps.OfflineRegionRequest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OfflineRegionConnectedTest {
    @Test fun a_downloadOrResumeBoundedRegionReportsCompleteResourcesAndBytes() {
        val manager = createManager()
        val existing = awaitList(manager).firstOrNull { it.request.name == REGION_NAME && it.phase == OfflineRegionPhase.COMPLETE }
        val result = existing ?: run {
            val request = OfflineRegionRequest(
                REGION_NAME,
                OfflineBounds(40.718, -73.998, 40.708, -74.012),
                12.0,
                14.0,
            )
            val terminal = AtomicReference<OfflineRegionProgress>()
            val latch = CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                manager.download(request) { progress ->
                    if (progress.phase == OfflineRegionPhase.COMPLETE || progress.phase == OfflineRegionPhase.FAILED) {
                        terminal.set(progress)
                        latch.countDown()
                    }
                }
            }
            assertTrue("Offline region did not finish within 3 minutes", latch.await(3, TimeUnit.MINUTES))
            requireNotNull(terminal.get())
        }
        assertTrue(result.explanation, result.phase == OfflineRegionPhase.COMPLETE)
        assertTrue("Offline region stored no resources", result.completedResources > 0)
        assertTrue("Offline region stored no bytes", result.completedBytes > 0)
        Log.i(
            "M3Offline",
            "phase=${result.phase} resources=${result.completedResources} " +
                "bytes=${result.completedBytes} zoom=12-14 bounded=true",
        )
    }

    @Test fun b_completedRegionRemainsListedWithoutStartingNetworkWork() {
        val complete = awaitList(
            createManager(),
        ).firstOrNull { it.request.name == REGION_NAME && it.phase == OfflineRegionPhase.COMPLETE }
        assertTrue("Expected the previously downloaded M3 offline region", complete != null)
        assertTrue("Expected cached offline bytes", complete!!.completedBytes > 0)
        Log.i(
            "M3Offline",
            "offline_list_phase=${complete.phase} resources=${complete.completedResources} " +
                "bytes=${complete.completedBytes}",
        )
    }

    private fun awaitList(manager: FieldOfflineRegionManager): List<OfflineRegionProgress> {
        val result = AtomicReference<List<OfflineRegionProgress>>(emptyList())
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            manager.list { result.set(it); latch.countDown() }
        }
        assertTrue("Offline region listing timed out", latch.await(30, TimeUnit.SECONDS))
        return result.get()
    }

    private fun createManager(): FieldOfflineRegionManager {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var manager: FieldOfflineRegionManager
        instrumentation.runOnMainSync {
            manager = FieldOfflineRegionManager(instrumentation.targetContext)
        }
        return manager
    }

    companion object { private const val REGION_NAME = "M3 connected evidence" }
}
