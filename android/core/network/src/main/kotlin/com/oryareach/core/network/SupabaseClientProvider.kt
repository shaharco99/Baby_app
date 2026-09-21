package com.oryareach.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Builds the Supabase client from build configuration.
 *
 * The publishable key is an identifier, not a secret: it grants nothing on its own, because
 * every table is protected by RLS, `anon` holds no table privileges, and no function in the
 * public schema is executable by `anon`. Authorization comes from the signed-in user's JWT.
 */
object SupabaseClientProvider {

    fun isConfigured(): Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    fun create(
        sessions: SessionManager,
        url: String = BuildConfig.SUPABASE_URL,
        anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    ): SupabaseClient {
        require(url.isNotBlank() && anonKey.isNotBlank()) {
            "Supabase is not configured. Set supabaseUrl and supabaseAnonKey in " +
                "android/local.properties or pass them as Gradle properties."
        }

        return createSupabaseClient(supabaseUrl = url, supabaseKey = anonKey) {
            install(Auth) {
                // Keystore-sealed rather than the SDK default, which writes the refresh token
                // to plain SharedPreferences.
                sessionManager = sessions
            }
            install(Postgrest)
            // Only `notify-workspace`, which wakes the partner's device after a push.
            install(Functions)
            install(Storage)
        }
    }
}
