import SwiftUI

/// Shared building blocks for the logged-out auth screens (`LoginView`,
/// `SignUpView`).
///
/// Styling matches the rest of the app (surface fill + hairline border fields,
/// brand primary button) and uses ONLY `AppColor` / `AppDimens` /
/// `AppTypography` tokens — no hardcoded hex. These are the full-screen gate
/// equivalents of the sheet `SheetComponents`.

// MARK: - Scaffold

/// A centered, scrollable full-screen container with a large brand-colored
/// title and a card-like content column. Used by both auth screens.
struct AuthScaffold<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack {
            AppColor.background.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: AppDimens.spacingXXL) {
                    // Wordmark + screen title.
                    VStack(alignment: .leading, spacing: AppDimens.spacingSM) {
                        Text("ThirdSpace")
                            .font(.system(size: AppTypography.footnote, weight: .bold))
                            .foregroundColor(AppColor.brand)
                            .textCase(.uppercase)
                            .tracking(1.5)
                        Text(title)
                            .font(.system(size: AppTypography.headline, weight: .bold))
                            .foregroundColor(AppColor.foreground)
                    }

                    content()
                }
                .padding(.horizontal, AppDimens.spacingXL)
                .padding(.top, AppDimens.spacingXXL)
                .padding(.bottom, AppDimens.spacingXXL)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

// MARK: - Labeled field wrapper

/// A semibold caption above arbitrary field content (mirrors ProfileEditView's
/// private `EditField`, re-declared here so the auth screens don't depend on a
/// private type).
struct AuthField<Content: View>: View {
    let label: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: AppDimens.spacingMD) {
            Text(label)
                .font(.system(size: AppTypography.footnote, weight: .semibold))
                .foregroundColor(AppColor.foreground)
            content()
        }
    }
}

// MARK: - Text / secure fields

/// A single-line text field with the app's standard surface + border chrome.
/// `isEmail` applies email keyboard + no autocapitalize/autocorrect.
struct AuthTextField: View {
    let placeholder: String
    @Binding var text: String
    let isEmail: Bool

    var body: some View {
        let field = TextField(placeholder, text: $text)
            .font(.system(size: AppTypography.body))
            .foregroundColor(AppColor.foreground)

        return Group {
            if isEmail {
                field
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
            } else {
                field
            }
        }
        .authFieldSurface()
    }
}

/// A secure (masked) field with the same chrome as `AuthTextField`.
struct AuthSecureField: View {
    let placeholder: String
    @Binding var text: String

    var body: some View {
        SecureField(placeholder, text: $text)
            .font(.system(size: AppTypography.body))
            .foregroundColor(AppColor.foreground)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled(true)
            .authFieldSurface()
    }
}

// MARK: - Primary button

/// A full-width brand-filled primary button. Shows a spinner (and disables)
/// while `isWorking`; dims when `!enabled`.
struct AuthPrimaryButton: View {
    let label: String
    let isWorking: Bool
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: AppDimens.spacingSM) {
                if isWorking {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(.white)
                }
                Text(label)
                    .font(.system(size: AppTypography.body, weight: .semibold))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppDimens.spacingLG)
            .foregroundColor(.white)
            .background(
                RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                    .fill(AppColor.brand)
            )
            .opacity(enabled ? 1 : 0.5)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

// MARK: - Switch link

/// A centered plain-text link to switch between Login and SignUp.
struct AuthSwitchLink: View {
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: AppTypography.footnote, weight: .semibold))
                .foregroundColor(AppColor.brand)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Inline error text

/// Destructive-colored inline error text. Renders nothing when `message` is
/// empty, so the layout doesn't reserve space for an absent error.
struct AuthErrorText: View {
    let message: String

    var body: some View {
        if !message.isEmpty {
            Text(message)
                .font(.system(size: AppTypography.footnote))
                .foregroundColor(AppColor.destructive)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

// MARK: - Field chrome modifier

private struct AuthFieldSurface: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(.horizontal, AppDimens.spacingMD)
            .padding(.vertical, AppDimens.spacingMD)
            .background(
                RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                    .fill(AppColor.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                    .stroke(AppColor.border, lineWidth: AppDimens.hairline)
            )
    }
}

private extension View {
    /// The app's standard surface + hairline-border field chrome.
    func authFieldSurface() -> some View { modifier(AuthFieldSurface()) }
}
