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

/// A semibold caption above arbitrary field content. Thin wrapper over the
/// shared `LabeledField` (slice B1) so existing call sites keep the `AuthField`
/// name; the label style + spacing now come from `FieldLabel`/`LabeledField`.
struct AuthField<Content: View>: View {
    let label: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        LabeledField(label: label, content: content)
    }
}

// MARK: - Text / secure fields

/// A single-line text field. Thin wrapper over the shared `AppTextField`
/// (slice B1), so the auth screens get the unified chrome + the readable
/// `AppColor.placeholder`. `isEmail` applies email keyboard + no
/// autocapitalize/autocorrect, identical to before.
struct AuthTextField: View {
    let placeholder: String
    @Binding var text: String
    let isEmail: Bool

    var body: some View {
        AppTextField(placeholder: placeholder, text: $text, isEmail: isEmail)
    }
}

/// A secure (masked) field. Thin wrapper over the shared `AppSecureField`
/// (slice B1), with the same chrome + readable placeholder.
struct AuthSecureField: View {
    let placeholder: String
    @Binding var text: String

    var body: some View {
        AppSecureField(placeholder: placeholder, text: $text)
    }
}

// MARK: - Primary button

/// A full-width brand-filled primary button. Shows a spinner (and disables)
/// while `isWorking`; dims when `!enabled`.
///
/// As of slice B2 this is a THIN WRAPPER over the shared `AppButton(.primary)`,
/// forwarding `isWorking` / `isEnabled`. This unifies its previously taller
/// `spacingLG` padding onto the standard `spacingMD` height and its
/// `.opacity(0.5)` onto the one disabled convention. The public name/signature
/// (used by `LoginView` / `SignUpView`) are unchanged.
struct AuthPrimaryButton: View {
    let label: String
    let isWorking: Bool
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        AppButton(
            label: label,
            style: .primary,
            isEnabled: enabled,
            isWorking: isWorking,
            action: action
        )
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

// The field chrome lives in `AppControls.swift` as `.appFieldSurface()`; the
// auth fields now delegate to the shared `AppTextField`/`AppSecureField`.
