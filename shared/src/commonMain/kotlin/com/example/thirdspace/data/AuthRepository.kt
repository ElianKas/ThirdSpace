package com.example.thirdspace.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.auth.exception.AuthRestException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Flat, ObjC/Swift-friendly outcome of an auth operation.
 *
 * BOUNDARY RULE (see architecture steering): this is intentionally made of ONLY
 * flat, non-nullable primitives (String / Boolean) so it can cross the
 * `FeedBridge` flat boundary to Swift without exposing any supabase-kt, Ktor,
 * coroutine, or nullable type. There are no nullables here: [email] and
 * [errorMessage] use the empty string to mean "none".
 *
 * - [success]         — the operation completed without error.
 * - [isAuthenticated] — whether there is a live session afterwards.
 * - [email]           — the current user's email, or "" when not authenticated.
 * - [errorCode]       — a STABLE machine code Swift can localize:
 *                       "invalid_credentials" / "email_exists" /
 *                       "weak_password" / "not_configured" / "network" /
 *                       "generic", or "" on success.
 * - [errorMessage]    — a human-readable fallback message (never translated;
 *                       Swift localizes via [errorCode], falling back to this).
 */
data class AuthResult(
    val success: Boolean,
    val isAuthenticated: Boolean,
    val email: String,
    val errorCode: String,
    val errorMessage: String,
) {
    companion object {
        /** A successful result carrying the authenticated [email]. */
        fun authenticated(email: String): AuthResult =
            AuthResult(
                success = true,
                isAuthenticated = true,
                email = email,
                errorCode = "",
                errorMessage = "",
            )

        /** A successful result with no live session (e.g. sign-out, or sign-up
         *  that requires email confirmation before a session exists). */
        fun successNoSession(): AuthResult =
            AuthResult(
                success = true,
                isAuthenticated = false,
                email = "",
                errorCode = "",
                errorMessage = "",
            )

        /** A failed result with a stable [code] and a fallback [message]. */
        fun failure(code: String, message: String): AuthResult =
            AuthResult(
                success = false,
                isAuthenticated = false,
                email = "",
                errorCode = code,
                errorMessage = message,
            )
    }
}

/**
 * Shared email/password auth repository over the supabase-kt GoTrue (`Auth`)
 * plugin.
 *
 * Phase C scope: real email/password sign-up, sign-in, sign-out, session
 * restore, and a session-status observation seam. All supabase-kt / coroutine /
 * Flow types stay INTERNAL to the shared module — only the flat [AuthResult]
 * (String/Boolean) is produced for the bridge. The suspend functions here are
 * `internal` and are launched on a coroutine scope by the iOS `FeedBridge`
 * (which delivers the flat result to Swift via a callback); Swift never sees a
 * suspend/Flow type.
 *
 * NOT-CONFIGURED SAFETY: every operation first checks
 * [SupabaseClientProvider.isConfigured]. When the app ships without real keys
 * (the default), the client is `null`; operations do NOT touch supabase-kt and
 * instead return [AuthResult.failure] with code `"not_configured"`, so nothing
 * crashes. Real end-to-end auth requires the user's Supabase keys + applied
 * migrations.
 *
 * supabase-kt 3.8.0 GoTrue API used (confirmed against the resolved klib):
 * - sign up:  `auth.signUpWith(Email) { email = ..; password = ..; data = .. }`
 *             (the `data` JsonObject becomes `raw_user_meta_data`, which the DB
 *             `handle_new_user()` trigger reads as `->>'name'` / `->>'city'`).
 * - sign in:  `auth.signInWith(Email) { email = ..; password = .. }`
 * - sign out: `auth.signOut()`
 * - session:  `auth.sessionStatus: StateFlow<SessionStatus>`, with
 *             `SessionStatus.Authenticated(session, source)` carrying
 *             `session.user?.email`; `auth.awaitInitialization()` to wait for
 *             the persisted-session load (autoLoadFromStorage is on by default).
 * - errors:   `AuthRestException.errorCode` (an `AuthErrorCode`, e.g.
 *             `invalid_credentials` / `email_exists` / `weak_password`).
 */
object AuthRepository {

    /** The GoTrue plugin, or `null` when the client is not configured. */
    private val auth get() = SupabaseClientProvider.client?.auth

    /**
     * Current authenticated email from the live session, or "" when there is no
     * session (or the client is unconfigured). Flat, non-null.
     */
    fun currentUserEmail(): String =
        auth?.currentSessionOrNull()?.user?.email.orEmpty()

    /** Whether there is a live session right now. Flat Bool. */
    fun isAuthenticated(): Boolean =
        auth?.currentSessionOrNull() != null

    /**
     * The current user's id (the auth uid), or "" when there is no session /
     * the client is unconfigured. Used as `owner_id` when persisting a spot so
     * the remote RLS `insert own` policy (`owner_id = auth.uid()`) is satisfied.
     * Flat, non-null.
     */
    fun currentUserId(): String =
        auth?.currentSessionOrNull()?.user?.id.orEmpty()

    /**
     * Observe auth-state changes as a stream of flat [AuthResult] snapshots
     * (success=true; [AuthResult.isAuthenticated] + [AuthResult.email] reflect
     * the current status). Maps the internal GoTrue `sessionStatus` Flow so the
     * `SessionStatus` type never leaves the shared module.
     *
     * When unconfigured, emits a single not-authenticated snapshot (no crash,
     * no supabase-kt access).
     */
    internal fun authStateFlow(): Flow<AuthResult> {
        val a = auth ?: return flowOf(AuthResult.successNoSession())
        return a.sessionStatus.map { status -> statusToResult(status) }
    }

    /**
     * Wait for the initial session load from storage (autoLoadFromStorage is on
     * by default), then return the resulting flat snapshot. No-op (returns a
     * not-authenticated snapshot) when unconfigured.
     */
    internal suspend fun restoreSession(): AuthResult {
        val a = auth ?: return AuthResult.failure("not_configured", "Backend not configured")
        return try {
            a.awaitInitialization()
            if (a.currentSessionOrNull() != null) {
                AuthResult.authenticated(a.currentSessionOrNull()?.user?.email.orEmpty())
            } else {
                AuthResult.successNoSession()
            }
        } catch (t: Throwable) {
            mapThrowable(t)
        }
    }

    /**
     * Create a new account with [email] / [password], passing [name] and [city]
     * into the user metadata (`raw_user_meta_data`) so the DB
     * `handle_new_user()` trigger can populate the profile row from
     * `->>'name'` and `->>'city'`.
     *
     * Returns authenticated when GoTrue returns a live session immediately;
     * returns a session-less success when the project requires email
     * confirmation first.
     */
    internal suspend fun signUp(
        email: String,
        password: String,
        name: String,
        city: String,
    ): AuthResult {
        val a = auth ?: return AuthResult.failure("not_configured", "Backend not configured")
        return try {
            a.signUpWith(Email) {
                this.email = email
                this.password = password
                this.data = buildMetadata(name, city)
            }
            // signUpWith returns null when a session is created immediately
            // (email confirmation disabled); check the live session.
            if (a.currentSessionOrNull() != null) {
                AuthResult.authenticated(a.currentSessionOrNull()?.user?.email.orEmpty())
            } else {
                AuthResult.successNoSession()
            }
        } catch (t: Throwable) {
            mapThrowable(t)
        }
    }

    /** Sign in an existing account with [email] / [password]. */
    internal suspend fun signIn(email: String, password: String): AuthResult {
        val a = auth ?: return AuthResult.failure("not_configured", "Backend not configured")
        return try {
            a.signInWith(Email) {
                this.email = email
                this.password = password
            }
            if (a.currentSessionOrNull() != null) {
                AuthResult.authenticated(a.currentSessionOrNull()?.user?.email.orEmpty())
            } else {
                AuthResult.successNoSession()
            }
        } catch (t: Throwable) {
            mapThrowable(t)
        }
    }

    /** Sign out the current session (clears the persisted session). */
    internal suspend fun signOut(): AuthResult {
        val a = auth ?: return AuthResult.failure("not_configured", "Backend not configured")
        return try {
            a.signOut()
            AuthResult.successNoSession()
        } catch (t: Throwable) {
            mapThrowable(t)
        }
    }

    /**
     * Build the sign-up user metadata JSON from [name] / [city]. Exposed as
     * `internal` so a commonTest can assert the mapping without a live backend.
     * Blank values are omitted so the trigger only sees provided fields.
     */
    internal fun buildMetadata(name: String, city: String): JsonObject {
        val entries = buildMap {
            if (name.isNotBlank()) put("name", JsonPrimitive(name))
            if (city.isNotBlank()) put("city", JsonPrimitive(city))
        }
        return JsonObject(entries)
    }

    /**
     * Map an internal GoTrue [SessionStatus] to a flat [AuthResult] snapshot.
     * Exposed as `internal` so a commonTest can assert the mapping.
     */
    internal fun statusToResult(status: SessionStatus): AuthResult = when (status) {
        is SessionStatus.Authenticated ->
            AuthResult.authenticated(status.session.user?.email.orEmpty())
        else ->
            AuthResult.successNoSession()
    }

    /**
     * Map a thrown supabase-kt / Ktor exception to a flat [AuthResult] failure
     * with a STABLE [AuthResult.errorCode] Swift can localize. Exposed as
     * `internal` so a commonTest can assert the mapping without a network.
     */
    internal fun mapThrowable(t: Throwable): AuthResult = when (t) {
        // A network/transport failure (host unreachable, offline, etc.).
        is HttpRequestException ->
            AuthResult.failure("network", t.message ?: "Network error")
        // A GoTrue auth error: resolve a stable code from the message body,
        // which carries the GoTrue `error_code` (e.g. "invalid_credentials").
        is AuthRestException ->
            AuthResult.failure(codeFromMessage(t.message), t.message ?: "Auth error")
        // Any other REST error from supabase-kt.
        is RestException ->
            AuthResult.failure("generic", t.message ?: "Request error")
        else ->
            AuthResult.failure("generic", t.message ?: "Unexpected error")
    }

    /**
     * Resolve a stable error code from a GoTrue exception [message]. The
     * message body carries the GoTrue `error_code`; we match known codes as
     * substrings so Swift gets a stable key regardless of the exact
     * supabase-kt [AuthRestException] property surface. Exposed `internal` for
     * a commonTest. Returns "generic" when nothing matches.
     */
    internal fun codeFromMessage(message: String?): String {
        val m = message.orEmpty().lowercase()
        return when {
            m.contains("invalid_credentials") || m.contains("invalid login") -> "invalid_credentials"
            m.contains("email_exists") || m.contains("user_already_exists") ||
                m.contains("already registered") -> "email_exists"
            m.contains("weak_password") -> "weak_password"
            m.contains("email_not_confirmed") -> "email_not_confirmed"
            else -> "generic"
        }
    }
}
