import SwiftUI
import Shared

/// App-wide authentication/session state for the native SwiftUI UI.
///
/// Thin `@Observable` wrapper around the shared `FeedBridge` auth seam, mirroring
/// the `Localization` pattern. It holds the flat session state (`isAuthenticated`
/// + `email`) and drives the logged-out auth gate in `ContentView`.
///
/// Views read it from the environment:
///
///     @Environment(Session.self) private var session
///     if session.isAuthenticated { ... }
///
/// BOUNDARY: only flat Bool/String cross the bridge. The bridge launches the
/// shared suspend auth ops on a `Dispatchers.Main` scope, so every completion /
/// auth-state callback below arrives ON THE MAIN THREAD — safe to mutate this
/// `@Observable` directly without an extra `DispatchQueue.main` hop.
///
/// NOT-CONFIGURED SAFETY: when the app ships without Supabase keys the shared
/// client is unconfigured; the bridge returns `success=false` with errorCode
/// `"not_configured"` and nothing crashes. Tapping log in / sign up then simply
/// surfaces the localized "backend not configured" error. Real end-to-end auth
/// is BLOCKED until the user's keys + migrations are applied.
///
/// `@Observable` + `@Environment(Type.self)` is the iOS 17+ Observation pattern
/// and is valid at the project's iOS 18.2 target.
@Observable
final class Session {
    /// Whether there is a live session. Drives the auth gate.
    private(set) var isAuthenticated: Bool
    /// Current user's email, or "" when logged out. Dynamic content (not i18n).
    private(set) var email: String
    /// Last auth error as a STABLE code ("invalid_credentials" / "email_exists"
    /// / "weak_password" / "not_configured" / "network" / "generic"), or "" when
    /// there is none. Localized for display via `errorMessage(_:)`.
    private(set) var lastErrorCode: String = ""
    /// True while a sign in / sign up / sign out call is in flight (drives the
    /// button spinners and disables inputs).
    private(set) var isWorking: Bool = false

    init() {
        // Seed the initial state synchronously from the bridge (safe when
        // unconfigured: returns "" / false).
        email = FeedBridge.shared.currentAuthEmail()
        isAuthenticated = FeedBridge.shared.isAuthenticated()

        // Observe future auth-state changes (sign-in/out, token refresh,
        // restore). Delivered on the main thread by the bridge scope.
        FeedBridge.shared.observeAuth { [weak self] (authed: KotlinBoolean, email: String) in
            guard let self else { return }
            self.isAuthenticated = authed.boolValue
            self.email = email
        }

        // Trigger initial persisted-session restore (no-op when unconfigured).
        FeedBridge.shared.restoreSession { [weak self] (success: KotlinBoolean, authed: KotlinBoolean, email: String, _: String) in
            guard let self else { return }
            if success.boolValue {
                self.isAuthenticated = authed.boolValue
                self.email = email
            }
        }
    }

    // MARK: Actions

    /// Sign in with email/password. Updates state on completion; on failure
    /// sets `lastErrorCode` for the view to localize inline.
    func signIn(email: String, password: String) {
        guard !isWorking else { return }
        isWorking = true
        lastErrorCode = ""
        FeedBridge.shared.signIn(email: email, password: password) { [weak self] (success: KotlinBoolean, authed: KotlinBoolean, email: String, code: String) in
            self?.handleResult(success: success.boolValue, authed: authed.boolValue, email: email, code: code)
        }
    }

    /// Create an account with name/email/password (+ city). `name`/`city` are
    /// passed shared-side into the sign-up metadata so the DB trigger populates
    /// the profile.
    func signUp(name: String, email: String, password: String, city: String) {
        guard !isWorking else { return }
        isWorking = true
        lastErrorCode = ""
        FeedBridge.shared.signUp(email: email, password: password, name: name, city: city) { [weak self] (success: KotlinBoolean, authed: KotlinBoolean, email: String, code: String) in
            self?.handleResult(success: success.boolValue, authed: authed.boolValue, email: email, code: code)
        }
    }

    /// Sign out the current session (wired to the Profile logout button).
    func signOut() {
        guard !isWorking else { return }
        isWorking = true
        lastErrorCode = ""
        FeedBridge.shared.signOut { [weak self] (success: KotlinBoolean, authed: KotlinBoolean, email: String, code: String) in
            self?.handleResult(success: success.boolValue, authed: authed.boolValue, email: email, code: code)
        }
    }

    /// Clear the current error (e.g. when switching between Login and SignUp).
    func clearError() {
        lastErrorCode = ""
    }

    // MARK: Error localization

    /// Map a stable error code to a localized message using the injected
    /// localization. Returns "" when there is no error.
    func errorMessage(_ loc: Localization) -> String {
        switch lastErrorCode {
        case "":
            return ""
        case "invalid_credentials":
            return loc.strings.authErrorInvalidCredentials
        case "email_exists":
            return loc.strings.authErrorEmailExists
        case "weak_password":
            return loc.strings.authErrorWeakPassword
        case "not_configured":
            return loc.strings.authErrorNotConfigured
        case "network":
            return loc.strings.authErrorNetwork
        default:
            return loc.strings.authErrorGeneric
        }
    }

    // MARK: Private

    /// Fold a flat bridge result into the observable state.
    private func handleResult(success: Bool, authed: Bool, email: String, code: String) {
        isWorking = false
        if success {
            isAuthenticated = authed
            self.email = email
            lastErrorCode = ""
        } else {
            lastErrorCode = code
        }
    }
}
