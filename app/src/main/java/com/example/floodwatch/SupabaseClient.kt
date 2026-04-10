package com.example.floodwatch

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = "https://jhjkfkgixkqbofehwwtn.supabase.co",
        supabaseKey = "sb_publishable_J2mD38t9sXunmzXOj9Wpug_l0B2ypVn"
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
}
