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

    /// Which logged-out screen is showing. Local to the gate.
    private enum AuthScreen { case login, signUp }
    @State private var authScreen: AuthScreen = .login

    var body: some View {
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
}
