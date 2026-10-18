package com.example.floodwatch

import com.example.floodwatch.util.ReportTimestampFormatter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class ReportTimestampFormatterTest {
    @Test
    fun `parses Supabase microseconds without shifting the time`() {
        assertEquals(
            Instant.parse("2026-05-24T14:49:46.353110Z"),
            ReportTimestampFormatter.parseInstant("2026-05-24 14:49:46.353110+00:00")
        )
    }

    @Test
    fun `formats timestamps with variable fractional precision`() {
        assertEquals(
            "May 24, 2026 2:49 PM",
            ReportTimestampFormatter.format(
                "2026-05-24T14:49:46.3Z",
                Locale.US,
                ZoneOffset.UTC
            )
        )
    }

    @Test
    fun `invalid timestamp remains displayable`() {
        assertNull(ReportTimestampFormatter.parseInstant("not-a-date"))
        assertEquals("not-a-date", ReportTimestampFormatter.format("not-a-date"))
    }
}
