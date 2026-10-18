package com.example.floodwatch.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Formats Supabase `timestamptz` values without treating microseconds as milliseconds. */
internal object ReportTimestampFormatter {
    fun format(
        value: String,
        locale: Locale = Locale.getDefault(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val instant = parseInstant(value) ?: return value
        return DateTimeFormatter.ofPattern("MMM dd, yyyy h:mm a", locale)
            .withZone(zoneId)
            .format(instant)
    }

    fun parseInstant(value: String): Instant? {
        if (value.isBlank()) return null
        // PostgREST may serialize the date/time separator as either `T` or a space.
        val normalized = value.trim().replaceFirst(' ', 'T')
        return try {
            OffsetDateTime.parse(normalized).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(normalized)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
