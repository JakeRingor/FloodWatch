package com.example.floodwatch

import com.example.floodwatch.util.HomeZoneUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeZoneUtilsTest {
    @Test
    fun `same coordinate is inside the home zone`() {
        assertTrue(HomeZoneUtils.isWithinRadius(14.5374, 121.1099, 14.5374, 121.1099, 250))
    }

    @Test
    fun `distant coordinate is outside a two kilometer zone`() {
        assertFalse(HomeZoneUtils.isWithinRadius(14.5374, 121.1099, 14.6000, 121.1099, 2_000))
    }

    @Test
    fun `notification radius is constrained to safe limits`() {
        assertEquals(250, HomeZoneUtils.normalizeRadius(1))
        assertEquals(10_000, HomeZoneUtils.normalizeRadius(50_000))
    }
}
