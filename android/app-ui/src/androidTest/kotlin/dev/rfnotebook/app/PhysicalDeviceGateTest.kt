package dev.rfnotebook.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.rfnotebook.acquisition.AcquisitionSpikeService
import dev.rfnotebook.acquisition.AcquisitionSpikeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhysicalDeviceGateTest {
    @Test fun targetProvidesUsbHostMode() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(
            "M0 connected validation requires a target with USB host mode",
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST),
        )
    }

    @Test fun deniedLocationStopsForegroundServiceWithoutCrashingApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val coarseDenied = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) != PackageManager.PERMISSION_GRANTED
        val fineDenied = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) != PackageManager.PERMISSION_GRANTED
        // This state-specific gate is also run directly after the harness revokes
        // both permissions. The aggregate connected task may start from the
        // granted state, in which case it must leave that state undisturbed.
        if (!(coarseDenied && fineDenied)) return

        ActivityScenario.launch(MainActivity::class.java).use {
            ContextCompat.startForegroundService(context, Intent(context, AcquisitionSpikeService::class.java))
            Thread.sleep(6_000)
            assertEquals("error", AcquisitionSpikeStatus.state.value.phase)
        }
    }

    @Test fun removingActivityTaskLeavesExplicitSurveyRunning() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val usb = context.getSystemService(UsbManager::class.java)
        val hackrf = usb.deviceList.values.firstOrNull {
            it.vendorId == HACKRF_VENDOR_ID && it.productId == HACKRF_PRODUCT_ID
        }
        if (hackrf == null || !usb.hasPermission(hackrf)) return
        val locationGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!locationGranted) return

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            ContextCompat.startForegroundService(
                context,
                Intent(context, AcquisitionSpikeService::class.java),
            )
            assertTrue("RX did not reach receiving state", waitUntil(10_000) {
                AcquisitionSpikeStatus.state.value.phase == "receiving"
            })
            val bytesBeforeRemoval = AcquisitionSpikeStatus.state.value.bytes

            scenario.onActivity { it.finishAndRemoveTask() }

            assertTrue("RX did not continue after activity task removal", waitUntil(5_000) {
                val state = AcquisitionSpikeStatus.state.value
                state.phase == "receiving" && state.bytes > bytesBeforeRemoval
            })
        }
        context.stopService(Intent(context, AcquisitionSpikeService::class.java))
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }

    companion object {
        private const val HACKRF_VENDOR_ID = 0x1d50
        private const val HACKRF_PRODUCT_ID = 0x6089
    }
}
