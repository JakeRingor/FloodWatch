package com.example.floodwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportStatusFilterTest {
    @Test
    fun allFilterHasNoServerStatusPattern() {
        assertTrue(ReportStatusFilter.ALL.patterns.isEmpty())
    }

    @Test
    fun needsAttentionIncludesBothInvalidStatuses() {
        assertEquals(
            listOf("invalid_image", "invalid_information"),
            ReportStatusFilter.NEEDS_ATTENTION.patterns
        )
    }

    @Test
    fun verifiedFilterHasUsefulEmptyMessage() {
        assertEquals(
            "You do not have any verified reports yet.",
            ReportStatusFilter.VERIFIED.emptyMessage()
        )
    }
}
