package dev.rfnotebook.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
}
