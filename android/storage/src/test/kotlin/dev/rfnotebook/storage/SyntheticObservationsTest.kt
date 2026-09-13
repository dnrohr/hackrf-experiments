package dev.rfnotebook.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class SyntheticObservationsTest {
    @Test fun `synthetic observations round trip through persisted representation`() {
        val decoded = SyntheticObservations.decode(SyntheticObservations.encode(SyntheticObservations.route))
        assertEquals(SyntheticObservations.route, decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed persisted observation is rejected`() {
        SyntheticObservations.decode("40.0,-74.0,6")
    }
}
