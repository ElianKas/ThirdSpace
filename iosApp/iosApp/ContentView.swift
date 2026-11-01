import SwiftUI

/// App root + AUTH GATE.
///
/// The UI is fully native SwiftUI (the Compose-Multiplatform entry points were
/// removed from the shared module). This view gates the app on the session
/// state injected from `iOSApp`:
///
/// - logged OUT (`session.isAuthenticated == false`): show the auth flow,
///   switching between `LoginView` and `SignUpView` via a local `@State` enum.
/// - logged IN: show `RootTabView()` (the four-tab shell).
///
/// With the shipped (unconfigured) client, `isAuthenticated` stays false, so the
/// app launches straight to the login gate; tapping log in / sign up surfaces
/// the localized "backend not configured" error without crashing. Real auth is
/// BLOCKED until the user's Supabase keys + migrations are applied.
struct ContentView: View {
    @Environment(Session.self) private var session
    @Environment(ProfileStore.self) private var profileStore

    /// Which logged-out screen is showing. Local to the gate.
    private enum AuthScreen { case login, signUp }
    @State private var authScreen: AuthScreen = .login

    var body: some View {
        Group {
            if session.isAuthenticated {
                RootTabView()
            } else {
                switch authScreen {
                case .login:
                    LoginView(onSwitchToSignUp: { authScreen = .signUp })
                case .signUp:
                    SignUpView(onSwitchToLogin: { authScreen = .login })
                }
            }
        }
        // Drive the ProfileStore off the auth transition `Session` already
        // observes, instead of a construction-order dependency in `iOSApp`.
        // `ProfileStore.init`'s `loadProfile` no-ops before the async Supabase
        // session restore completes, so the real profile would otherwise stay
        // blank on a cold relaunch with a restorable session. When auth flips
        // true (fresh login OR a restored session), reload the profile; when it
        // flips false (sign-out), clear it so a different user never sees the
        // previous user's data.
        .onChange(of: session.isAuthenticated) { _, authed in
            if authed {
                profileStore.reload()
            } else {
                profileStore.clear()
            }
        }
        // Belt-and-suspenders: the session restore can complete BEFORE this
        // view appears, so the false->true `onChange` may never fire. Cover
        // that by reloading on appear when already authenticated. The shared
        // refresh coalesces via its in-flight guard, so a duplicate kick-off
        // alongside `onChange` is harmless (at most one in-flight fetch).
        .onAppear {
            if session.isAuthenticated {
                profileStore.reload()
            }
        }
    }
}
