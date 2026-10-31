import SwiftUI

/// Full-screen LOGIN view — part of the logged-out auth gate (NOT a sheet).
///
/// Shown by `ContentView` when `session.isAuthenticated` is false and the gate
/// is on `.login`. Collects email + password, calls `session.signIn`, shows a
/// spinner while working, surfaces the localized `lastError` inline, and offers
/// a link to switch to the sign-up screen.
///
/// Uses ONLY `AppColor` / `AppDimens` / `AppTypography` tokens and the shared
/// field/button chrome conventions (surface fill + hairline border, brand
/// primary button) consistent with the rest of the app. All static text goes
/// through the injected `Localization`.
struct LoginView: View {
    @Environment(Localization.self) private var loc
    @Environment(Session.self) private var session

    /// Switch to the sign-up screen (owned by the parent gate).
    let onSwitchToSignUp: () -> Void

    @State private var email = ""
    @State private var password = ""

    private var canSubmit: Bool {
        !email.trimmingCharacters(in: .whitespaces).isEmpty
            && !password.isEmpty
            && !session.isWorking
    }

    var body: some View {
        AuthScaffold(title: loc.strings.authLoginTitle) {
            VStack(alignment: .leading, spacing: AppDimens.spacingXL) {
                AuthField(label: loc.strings.authEmailLabel) {
                    AuthTextField(
                        placeholder: loc.strings.authEmailPlaceholder,
                        text: $email,
                        isEmail: true
                    )
                }

                AuthField(label: loc.strings.authPasswordLabel) {
                    AuthSecureField(
                        placeholder: loc.strings.authPasswordPlaceholder,
                        text: $password
                    )
                }

                AuthErrorText(message: session.errorMessage(loc))

                AuthPrimaryButton(
                    label: loc.strings.authLoginButton,
                    isWorking: session.isWorking,
                    enabled: canSubmit
                ) {
                    session.signIn(email: email.trimmingCharacters(in: .whitespaces), password: password)
                }

                AuthSwitchLink(label: loc.strings.authSwitchToSignUp) {
                    session.clearError()
                    onSwitchToSignUp()
                }
            }
        }
    }
}
