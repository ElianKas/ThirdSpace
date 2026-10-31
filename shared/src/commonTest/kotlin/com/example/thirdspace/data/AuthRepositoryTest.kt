package com.example.thirdspace.data

import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the OFFLINE-provable logic of [AuthRepository] and
 * [AuthResult] — the parts that do NOT require a live backend or network:
 *
 * - the not-configured path (unconfigured provider -> success=false,
 *   "not_configured", no crash),
 * - the sign-up metadata mapping (name/city -> the `data` JsonObject the DB
 *   `handle_new_user()` trigger reads),
 * - the exception/code -> flat [AuthResult] mapping,
 * - the [SessionStatus] -> flat [AuthResult] mapping.
 *
 * Real sign-up / sign-in / session-restore against Supabase are BLOCKED until
 * the user's keys + migrations are applied and are intentionally NOT exercised
 * here (no network is required or used).
 *
 * Reuses the kotlin.test + kotlinx-coroutines-test frameworks already on the
 * classpath (no new dependency). Runs on Android host + iOS simulator targets.
 */
class AuthRepositoryTest {

    // The SupabaseClientProvider is a shared object; make sure every test
    // starts from (and leaves) the unconfigured state so there is no bleed.
    @AfterTest
    fun resetProvider() {
        SupabaseClientProvider.configure("", "")
    }

    // --- Not-configured path (no client, no network, no crash) ---

    @Test
    fun unconfiguredSignInReturnsNotConfigured() = runBlocking {
        SupabaseClientProvider.configure("", "") // unconfigured
        val result = AuthRepository.signIn("a@b.c", "pw")
        assertFalse(result.success)
        assertFalse(result.isAuthenticated)
        assertEquals("not_configured", result.errorCode)
        assertEquals("", result.email)
    }

    @Test
    fun unconfiguredSignUpReturnsNotConfigured() = runBlocking {
        SupabaseClientProvider.configure("", "")
        val result = AuthRepository.signUp("a@b.c", "pw", "Ada", "Berlin")
        assertFalse(result.success)
        assertEquals("not_configured", result.errorCode)
    }

    @Test
    fun unconfiguredSignOutReturnsNotConfigured() = runBlocking {
        SupabaseClientProvider.configure("", "")
        val result = AuthRepository.signOut()
        assertFalse(result.success)
        assertEquals("not_configured", result.errorCode)
    }

    @Test
    fun unconfiguredRestoreReturnsNotConfigured() = runBlocking {
        SupabaseClientProvider.configure("", "")
        val result = AuthRepository.restoreSession()
        assertFalse(result.success)
        assertEquals("not_configured", result.errorCode)
    }

    @Test
    fun unconfiguredStateReadsAreSafe() {
        SupabaseClientProvider.configure("", "")
        assertEquals("", AuthRepository.currentUserEmail())
        assertFalse(AuthRepository.isAuthenticated())
    }

    // --- Sign-up metadata mapping (name/city -> raw_user_meta_data) ---

    @Test
    fun buildMetadataIncludesNameAndCity() {
        val data = AuthRepository.buildMetadata("Ada Lovelace", "Berlin")
        assertEquals(JsonPrimitive("Ada Lovelace"), data["name"])
        assertEquals(JsonPrimitive("Berlin"), data["city"])
    }

    @Test
    fun buildMetadataOmitsBlankFields() {
        val data = AuthRepository.buildMetadata("", "   ")
        assertTrue(data.isEmpty())
    }

    @Test
    fun buildMetadataKeepsOnlyProvidedField() {
        val data = AuthRepository.buildMetadata("Ada", "")
        assertEquals(JsonPrimitive("Ada"), data["name"])
        assertFalse(data.containsKey("city"))
    }

    // --- Exception -> flat AuthResult code mapping ---

    @Test
    fun codeFromMessageMapsInvalidCredentials() {
        assertEquals(
            "invalid_credentials",
            AuthRepository.codeFromMessage("{\"error_code\":\"invalid_credentials\"}"),
        )
    }

    @Test
    fun codeFromMessageMapsEmailExists() {
        assertEquals(
            "email_exists",
            AuthRepository.codeFromMessage("user_already_exists"),
        )
    }

    @Test
    fun codeFromMessageMapsWeakPassword() {
        assertEquals(
            "weak_password",
            AuthRepository.codeFromMessage("weak_password: too short"),
        )
    }

    @Test
    fun codeFromMessageFallsBackToGeneric() {
        assertEquals("generic", AuthRepository.codeFromMessage("totally unknown"))
        assertEquals("generic", AuthRepository.codeFromMessage(null))
    }

    @Test
    fun mapThrowableGenericForPlainException() {
        val result = AuthRepository.mapThrowable(IllegalStateException("boom"))
        assertFalse(result.success)
        assertEquals("generic", result.errorCode)
        assertEquals("boom", result.errorMessage)
    }

    // --- SessionStatus -> flat AuthResult mapping ---

    @Test
    fun statusToResultNotAuthenticatedWhenSignedOut() {
        val result = AuthRepository.statusToResult(SessionStatus.NotAuthenticated(false))
        assertTrue(result.success)
        assertFalse(result.isAuthenticated)
        assertEquals("", result.email)
    }

    @Test
    fun statusToResultNotAuthenticatedWhenInitializing() {
        val result = AuthRepository.statusToResult(SessionStatus.Initializing)
        assertTrue(result.success)
        assertFalse(result.isAuthenticated)
    }

    // --- AuthResult factory invariants (all flat, no nullables) ---

    @Test
    fun authResultFactoriesAreFlatAndConsistent() {
        val ok = AuthResult.authenticated("a@b.c")
        assertTrue(ok.success && ok.isAuthenticated)
        assertEquals("a@b.c", ok.email)
        assertEquals("", ok.errorCode)

        val none = AuthResult.successNoSession()
        assertTrue(none.success)
        assertFalse(none.isAuthenticated)
        assertEquals("", none.email)

        val fail = AuthResult.failure("network", "offline")
        assertFalse(fail.success)
        assertFalse(fail.isAuthenticated)
        assertEquals("network", fail.errorCode)
        assertEquals("offline", fail.errorMessage)
    }
}
