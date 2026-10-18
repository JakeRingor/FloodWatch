package com.example.floodwatch.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

object ReportRepository {
    private const val REPORT_BUCKET = "flood-reports"

    suspend fun submit(
        userId: String,
        originalJpegBytes: ByteArray,
        annotatedJpegBytes: ByteArray,
        report: FloodReport
    ) {
        val timestamp = System.currentTimeMillis()
        val originalPath = "$userId/report_${timestamp}_original.jpg"
        val annotatedPath = "$userId/report_${timestamp}_annotated.jpg"
        val bucket = SupabaseClient.client.storage.from(REPORT_BUCKET)
        var databaseOwnsFiles = false
        try {
            bucket.upload(path = originalPath, data = originalJpegBytes)
            bucket.upload(path = annotatedPath, data = annotatedJpegBytes)
            SupabaseClient.client.from("flood_reports").insert(
                report.copy(
                    imageUrl = bucket.publicUrl(annotatedPath),
                    originalImageUrl = bucket.publicUrl(originalPath)
                )
            )
            databaseOwnsFiles = true
            FloodRepository.invalidate()
        } catch (error: CancellationException) {
            if (!databaseOwnsFiles) deleteOrphans(originalPath, annotatedPath)
            throw error
        } catch (error: Exception) {
            if (!databaseOwnsFiles) deleteOrphans(originalPath, annotatedPath)
            throw error
        }
    }

    private suspend fun deleteOrphans(vararg paths: String) = withContext(NonCancellable) {
        val bucket = SupabaseClient.client.storage.from(REPORT_BUCKET)
        paths.forEach { path -> runCatching { bucket.delete(path) } }
    }
}
