package com.example.floodwatch.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.serialization.json.put
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class ReportStatistics(val submitted: Int, val verified: Int)

object ProfileRepository {
    suspend fun load(userId: String): UserProfile? = SupabaseClient.client
        .from("user_profiles")
        .select { filter { eq("id", userId) } }
        .decodeList<UserProfile>().firstOrNull()

    suspend fun reportStatistics(userId: String): ReportStatistics = coroutineScope {
        val allRequest = async {
            SupabaseClient.client.from("flood_reports").select {
                head = true
                count(Count.EXACT)
                filter { eq("user_id", userId) }
            }
        }
        val verifiedRequest = async {
            SupabaseClient.client.from("flood_reports").select {
                head = true
                count(Count.EXACT)
                filter {
                    eq("user_id", userId)
                    ilike("status", "verified")
                }
            }
        }
        val all = allRequest.await()
        val verified = verifiedRequest.await()
        ReportStatistics(
            submitted = all.countOrNull()?.toInt() ?: 0,
            verified = verified.countOrNull()?.toInt() ?: 0
        )
    }

    suspend fun updateBasic(userId: String, name: String, phone: String?, address: String?) {
        SupabaseClient.client.from("user_profiles").update({
            set("full_name", name)
            set("phone_number", phone)
            set("address", address)
        }) { filter { eq("id", userId) } }
        SupabaseClient.client.auth.updateUser {
            data { put("full_name", name) }
        }
    }

    suspend fun saveHomeZone(
        userId: String,
        latitude: Double,
        longitude: Double,
        label: String,
        radiusMeters: Int
    ) {
        SupabaseClient.client.from("user_profiles").update({
            set("home_zone_label", label)
            set("home_latitude", latitude)
            set("home_longitude", longitude)
            set("home_radius_meters", radiusMeters)
            set("home_zone_enabled", true)
        }) { filter { eq("id", userId) } }
    }

    suspend fun clearHomeZone(userId: String) {
        SupabaseClient.client.from("user_profiles").update({
            set("home_zone_label", null as String?)
            set("home_latitude", null as Double?)
            set("home_longitude", null as Double?)
            set("home_zone_enabled", false)
        }) { filter { eq("id", userId) } }
    }
}
