package dev.rfnotebook.acquisition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcquisitionPermissionGateTest {
    @Test fun `foreground service requires at least one location grant`() {
        assertFalse(hasAnyLocationPermission(coarseGranted = false, fineGranted = false))
        assertTrue(hasAnyLocationPermission(coarseGranted = true, fineGranted = false))
        assertTrue(hasAnyLocationPermission(coarseGranted = false, fineGranted = true))
    }
}
