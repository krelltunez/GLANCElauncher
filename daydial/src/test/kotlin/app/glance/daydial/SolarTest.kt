package app.glance.daydial

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolarTest {
    private val denver = ZoneId.of("America/Denver")

    @Test
    fun `the sun is high at a June noon, low at a December noon, and down at midnight`() {
        // Denver, solar noon near 13:00 MDT in June. Peak elevation there is 90 - 39.74 + 23.44 = 73.7.
        val june = Solar.elevation(LocalDate.of(2026, 6, 21), 39.74, -104.99, 13 * 60.0, denver)
        assertTrue(june in 70.0..74.0, "june noon $june")
        val december = Solar.elevation(LocalDate.of(2026, 12, 21), 39.74, -104.99, 12 * 60.0, denver)
        assertTrue(december in 24.0..30.0, "december noon $december")
        assertTrue(Solar.elevation(LocalDate.of(2026, 6, 21), 39.74, -104.99, 0.0, denver) < 0)
    }

    @Test
    fun `the peak is capped overhead inside the tropics`() {
        assertEquals(73.7, Solar.peakElevation(39.74), 0.01)
        assertEquals(90.0, Solar.peakElevation(10.0))
        assertEquals(Solar.peakElevation(39.74), Solar.peakElevation(-39.74))
    }
}
