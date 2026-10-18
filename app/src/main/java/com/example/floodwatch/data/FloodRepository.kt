package com.example.floodwatch.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Central data source for flood alerts/reports. UI classes never build database queries. */
object FloodRepository {
    private const val CACHE_TTL_MS = 60_000L
    private const val DASHBOARD_LIMIT = 100L
    private val cacheMutex = Mutex()
    private var cachedAlerts: CacheEntry<FloodAlert>? = null
    private var cachedVerifiedReports: CacheEntry<FloodReport>? = null
    private var cachedPermanentZones: CacheEntry<PermanentFloodZone>? = null

    suspend fun activeAlerts(forceRefresh: Boolean = false): List<FloodAlert> = cacheMutex.withLock {
        cachedAlerts?.takeIf { !forceRefresh && it.isFresh() }?.items?.let { return@withLock it }
        val items = SupabaseClient.client.from("flood_alerts").select {
            filter { eq("is_active", true) }
            order("created_at", Order.DESCENDING)
            range(0, DASHBOARD_LIMIT - 1)
        }.decodeList<FloodAlert>()
        cachedAlerts = CacheEntry(items)
        items
    }

    suspend fun verifiedReports(forceRefresh: Boolean = false): List<FloodReport> =
        cacheMutex.withLock {
            cachedVerifiedReports?.takeIf { !forceRefresh && it.isFresh() }?.items?.let {
                return@withLock it
            }
            val items = SupabaseClient.client.from("flood_reports").select {
                filter {
                    eq("status", "VERIFIED")
                    FloodReport::deletedAt.isExact(null)
                }
                order("created_at", Order.DESCENDING)
                range(0, DASHBOARD_LIMIT - 1)
            }.decodeList<FloodReport>()
            cachedVerifiedReports = CacheEntry(items)
            items
        }

    suspend fun userReports(
        userId: String,
        statusPatterns: List<String>,
        offset: Long,
        pageSize: Long
    ): List<FloodReport> = SupabaseClient.client.from("flood_reports").select {
        filter {
            eq("user_id", userId)
            when (statusPatterns.size) {
                1 -> ilike("status", statusPatterns.first())
                in 2..Int.MAX_VALUE -> ilikeAny("status", statusPatterns)
            }
        }
        order("created_at", Order.DESCENDING)
        range(offset, offset + pageSize - 1)
    }.decodeList()

    suspend fun withdrawPendingReport(reportId: String): Boolean =
        SupabaseClient.client.postgrest.rpc(
            function = "withdraw_own_pending_report",
            parameters = buildJsonObject { put("p_report_id", reportId) }
        ).decodeAs()

    suspend fun permanentFloodZones(forceRefresh: Boolean = false): List<PermanentFloodZone> =
        cacheMutex.withLock {
            cachedPermanentZones?.takeIf { !forceRefresh && it.isFresh() }?.items?.let {
                return@withLock it
            }
            val items = SupabaseClient.client.from("permanent_flood_zones").select {
                filter { eq("is_active", true) }
                order("name", Order.ASCENDING)
            }.decodeList<PermanentFloodZone>()
            cachedPermanentZones = CacheEntry(items)
            items
        }

    fun invalidate() {
        cachedAlerts = null
        cachedVerifiedReports = null
        cachedPermanentZones = null
    }

    private data class CacheEntry<T>(
        val items: List<T>,
        val loadedAt: Long = System.currentTimeMillis()
    ) {
        fun isFresh(): Boolean = System.currentTimeMillis() - loadedAt < CACHE_TTL_MS
    }
}
