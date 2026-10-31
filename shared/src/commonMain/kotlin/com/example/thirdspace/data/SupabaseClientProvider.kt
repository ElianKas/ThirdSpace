package com.example.thirdspace.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Single shared entry point that holds the (optional) Supabase client.
 *
 * PHASE A — INERT BY DESIGN. This provider is wired in but does NOT connect to
 * any backend yet:
 * - It does NOT build a client at construction. The app ships with no keys, so
 *   the client stays `null` and [isConfigured] stays `false` until
 *   [configure] is called with real values.
 * - Nothing here performs a network call. Auth, spot persistence, and actual
 *   requests are Phases C/D; this phase only proves the dependency + seam
 *   compile on both platforms.
 *
 * BOUNDARY RULE (see architecture steering): the [SupabaseClient] and all
 * supabase-kt / Ktor / coroutine types stay INTERNAL to the shared module. They
 * must never cross the flat `FeedBridge` boundary to Swift — only flat
 * String/Int/Bool/Double do. That is why [client] is `internal`.
 */
object SupabaseClientProvider {

    /**
     * The configured client, or `null` until [configure] succeeds. Kept
     * `internal` so supabase-kt types never leak across the Swift boundary.
     */
    internal var client: SupabaseClient? = null
        private set

    /** Whether a usable client has been built (i.e. real keys were supplied). */
    val isConfigured: Boolean
        get() = client != null

    /**
     * Build and store the Supabase client from a project [url] and [anonKey].
     *
     * Treats blank or obvious placeholder values as "not configured": in that
     * case the client is left `null` and this is a no-op, so an app launched
     * WITHOUT real keys (the default Phase A state) never builds a client and
     * never crashes. Call this once at startup; calling it again with real
     * values reconfigures.
     *
     * Only the Auth (GoTrue) and Postgrest modules are installed — no Realtime,
     * per the Phase A scope. No session is created and no request is made here;
     * this only constructs the client object.
     */
    fun configure(url: String, anonKey: String) {
        if (!looksConfigured(url, anonKey)) {
            client = null
            return
        }
        client = createSupabaseClient(supabaseUrl = url, supabaseKey = anonKey) {
            install(Auth)
            install(Postgrest)
        }
    }

    /**
     * Guard against building a client from empty or template placeholder values
     * (the committed `Supabase.xcconfig` ships with `PLACEHOLDER` seeds so the
     * iOS build resolves before a developer supplies real keys).
     */
    private fun looksConfigured(url: String, anonKey: String): Boolean {
        if (url.isBlank() || anonKey.isBlank()) return false
        if (anonKey == "PLACEHOLDER") return false
        if (url.contains("PLACEHOLDER")) return false
        return true
    }
}
