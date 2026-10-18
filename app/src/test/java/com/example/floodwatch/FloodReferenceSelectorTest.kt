package com.example.floodwatch

import com.example.floodwatch.util.FloodReferenceCandidate
import com.example.floodwatch.util.FloodReferenceSelector
import org.junit.Assert.assertEquals
import org.junit.Test

class FloodReferenceSelectorTest {
    @Test
    fun deepestSeverityWins() {
        val selected = FloodReferenceSelector.select(
            listOf(
                FloodReferenceCandidate("moderate", 2, 30.0, 3.0),
                FloodReferenceCandidate("critical", 4, 60.0, 8.0)
            )
        )
        assertEquals("critical", selected)
    }

    @Test
    fun lowestElevationBreaksEqualDepthTie() {
        val selected = FloodReferenceSelector.select(
            listOf(
                FloodReferenceCandidate("higher", 3, 45.0, 12.0),
                FloodReferenceCandidate("lower", 3, 45.0, 4.0)
            )
        )
        assertEquals("lower", selected)
    }

    @Test
    fun depthRangeCreatesComparableDepth() {
        assertEquals(50.0, FloodReferenceSelector.depthForRange("31-50 cm", 3), 0.0)
        assertEquals(75.0, FloodReferenceSelector.depthForRange("50+ cm", 4), 0.0)
    }
}
