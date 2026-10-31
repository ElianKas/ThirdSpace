import SwiftUI

/// Full-screen SIGN-UP view — part of the logged-out auth gate (NOT a sheet).
///
/// Shown by `ContentView` when the gate is on `.signUp`. Collects name + email
/// + password + city (city defaults to "Berlin"), calls `session.signUp`
/// (which passes name/city into the shared sign-up metadata so the DB trigger
/// populates the profile), shows a spinner while working, surfaces the
/// localized `lastError` inline, and offers a link back to login.
///
/// Uses the same `AuthScaffold` / token-styled field + button building blocks
/// as `LoginView`.
struct SignUpView: View {
    @Environment(Localization.self) private var loc
    @Environment(Session.self) private var session

    /// Switch back to the login screen (owned by the parent gate).
    let onSwitchToLogin: () -> Void

    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    /// City defaults to "Berlin" per Phase C scope (dynamic content, not i18n).
    @State private var city = "Berlin"

    private var canSubmit: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
            && !email.trimmingCharacters(in: .whitespaces).isEmpty
            && !password.isEmpty
            && !session.isWorking
    }

    var body: some View {
        AuthScaffold(title: loc.strings.authSignUpTitle) {
            VStack(alignment: .leading, spacing: AppDimens.spacingXL) {
                AuthField(label: loc.strings.authNameLabel) {
                    AuthTextField(
                        placeholder: loc.strings.authNamePlaceholder,
                        text: $name,
                        isEmail: false
                    )
                }

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

                AuthField(label: loc.strings.authCityLabel) {
                    AuthTextField(
                        placeholder: loc.strings.authCityPlaceholder,
                        text: $city,
                        isEmail: false
                    )
                }

                AuthErrorText(message: session.errorMessage(loc))

                AuthPrimaryButton(
                    label: loc.strings.authSignUpButton,
                    isWorking: session.isWorking,
                    enabled: canSubmit
                ) {
                    session.signUp(
                        name: name.trimmingCharacters(in: .whitespaces),
                        email: email.trimmingCharacters(in: .whitespaces),
                        password: password,
                        city: city.trimmingCharacters(in: .whitespaces)
                    )
                }

                AuthSwitchLink(label: loc.strings.authSwitchToLogin) {
                    session.clearError()
                    onSwitchToLogin()
                }
            }
        }
    }
}
