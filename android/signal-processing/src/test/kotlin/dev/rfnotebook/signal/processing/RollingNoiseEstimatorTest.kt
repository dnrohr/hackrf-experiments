package dev.rfnotebook.signal.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingNoiseEstimatorTest {
    @Test fun `persistent occupied bin does not pull baseline up`() {
        val estimator = RollingNoiseEstimator(windowSize = 8, startupNoiseDbfs = -90f)
        repeat(20) { estimator.observe("equipment:v1", 915_000_000, -55f) }

        val estimate = estimator.estimate("equipment:v1", 915_000_000)

        assertTrue(estimate.powerDbfs < -80f)
        assertEquals(20, estimate.ageFrames)
    }

    @Test fun `equipment profiles have isolated baselines`() {
        val estimator = RollingNoiseEstimator(windowSize = 8, startupNoiseDbfs = -90f)
        repeat(8) { estimator.observe("equipment:v1", 915_000_000, -89f) }
        repeat(8) { estimator.observe("equipment:v2", 915_000_000, -85f) }

        assertTrue(estimator.estimate("equipment:v1", 915_000_000).powerDbfs <
            estimator.estimate("equipment:v2", 915_000_000).powerDbfs)
    }

    @Test fun `span reference bootstraps a higher startup floor without hiding a narrow carrier`() {
        val estimator = RollingNoiseEstimator(windowSize = 8, startupNoiseDbfs = -90f)

        val noise = estimator.observe("equipment:v1", 914_900_000, -75f, spanReferenceFloorDbfs = -75f)
        val carrier = estimator.observe("equipment:v1", 915_000_000, -45f, spanReferenceFloorDbfs = -75f)

        assertTrue(noise.powerDbfs >= -75f)
        assertTrue(carrier.powerDbfs <= -75f)
    }

    @Test fun `sustained coherent floor rise adapts after bounded evidence`() {
        val estimator = RollingNoiseEstimator(windowSize = 8, startupNoiseDbfs = -90f)
        repeat(8) { estimator.observe("equipment:v1", 915_000_000, -90f, -90f) }
        repeat(3) { estimator.observe("equipment:v1", 915_000_000, -75f, -75f) }

        assertTrue(estimator.estimate("equipment:v1", 915_000_000).powerDbfs >= -75f)
    }
}
