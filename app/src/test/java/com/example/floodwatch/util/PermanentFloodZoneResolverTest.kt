package com.example.floodwatch.util

import com.example.floodwatch.data.FloodReport
import com.example.floodwatch.data.PermanentFloodZone
import org.junit.Assert.assertEquals
import org.junit.Test

class PermanentFloodZoneResolverTest {
    private val zone = PermanentFloodZone(
        id = "zone-1",
        slug = "test",
        name = "Test zone",
        nearestStreet = "Test street",
        latitude = 14.56539,
        longitude = 121.12674,
        radiusMeters = 150,
        noahHazardLevel = "MEDIUM"
    )

    @Test
    fun `NOAH baseline is used without a deep verified report`() {
        assertEquals(
            PermanentZoneDisplayState.MEDIUM_HAZARD,
            PermanentFloodZoneResolver.displayState(zone, emptyList())
        )
    }

    @Test
    fun `nearby deep verified report overrides weather`() {
        val report = FloodReport(
            latitude = 14.56540,
            longitude = 121.12675,
            severity = 3,
            status = "VERIFIED"
        )
        assertEquals(
            PermanentZoneDisplayState.DEEP_VERIFIED,
            PermanentFloodZoneResolver.displayState(zone, listOf(report))
        )
    }

    @Test
    fun `far deep report does not override NOAH baseline`() {
        val report = FloodReport(
            latitude = 14.57540,
            longitude = 121.13675,
            severity = 4,
            status = "VERIFIED"
        )
        assertEquals(
            PermanentZoneDisplayState.MEDIUM_HAZARD,
            PermanentFloodZoneResolver.displayState(zone, listOf(report))
        )
    }

    @Test
    fun `lowest elevation zone is selected`() {
        val lowerZone = zone.copy(id = "lower", elevationMeters = 4.0)
        val higherZone = zone.copy(id = "higher", elevationMeters = 9.0)
        assertEquals(
            "lower",
            PermanentFloodZoneResolver.lowestElevationZoneId(listOf(higherZone, lowerZone))
        )
    }
}
