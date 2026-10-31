import SwiftUI

/// Native SwiftUI "Profil bearbeiten" (edit profile) screen — a PUSH sub-page
/// of the Profile tab (NOT a sheet). Reached from the "Profil bearbeiten"
/// button in `ProfileView`, inside the Profile tab's `NavigationStack`; the
/// nav bar (with a back button) is provided by that stack.
///
/// Two inline tabs — "Profil" and "Konto" — adapted from the legacy
/// `ProfileSettingsSheet` (the follower / requests tabs are deliberately
/// omitted). Everything here is no-op / local: there is NO backend, auth, or
/// upload. Photo rows are placeholders only (no real picker); text fields and
/// the private-profile toggle edit LOCAL `@State` seeded from the shared dummy
/// profile; every button does nothing.
///
/// Styling follows `CreatePostView` (surface/border fields, placeholder overlay
/// on the bio editor) and uses only `AppColor` tokens. All static UI text goes
/// through the injected `Localization` (`loc.strings.*`); only dynamic content
/// (the current email, and the seeded name/bio/city) is interpolated verbatim.
struct ProfileEditView: View {
    @Environment(Localization.self) private var loc

    /// Two local tabs for the edit content. Mirrors `ProfileView`'s inline
    /// tab enum on purpose (no shared segmented control exists).
    private enum EditTab { case profile, account }

    @State private var selectedTab: EditTab = .profile

    /// The current user's dummy profile — source of the seeded field values and
    /// the account tab's current-email line.
    private let profile = FeedData.profile()

    // Local, non-persisted edit state (seeded from the dummy profile).
    @State private var name: String
    @State private var bio: String
    @State private var city: String
    @State private var isPrivate: Bool = false

    // Account tab local state.
    @State private var newEmail: String = ""
    @State private var newPassword: String = ""
    @State private var confirmPassword: String = ""

    init() {
        let profile = FeedData.profile()
        _name = State(initialValue: profile.name)
        _bio = State(initialValue: profile.bio)
        _city = State(initialValue: profile.city)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                tabBar
                    .padding(.top, AppDimens.spacingSM)

                tabContent
                    .padding(.horizontal, AppDimens.spacingXL)
                    .padding(.top, AppDimens.spacingXL)
                    .padding(.bottom, AppDimens.spacingXXL)
            }
        }
        .background(AppColor.background)
        .navigationTitle(loc.strings.profileEditTitle)
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: Inline two-tab segmented control (mirrors ProfileView.tabBar)

    private var tabBar: some View {
        HStack(spacing: 0) {
            tabButton(.profile, title: loc.strings.editTabProfile)
            tabButton(.account, title: loc.strings.editTabAccount)
        }
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(AppColor.hairline)
                .frame(height: AppDimens.hairline)
        }
    }

    private func tabButton(_ tab: EditTab, title: String) -> some View {
        let active = selectedTab == tab
        return Button {
            selectedTab = tab
        } label: {
            VStack(spacing: AppDimens.spacingSM) {
                Text(title)
                    .font(.system(size: AppTypography.body, weight: active ? .semibold : .regular))
                    .foregroundColor(active ? AppColor.brand : AppColor.mutedForeground)
                Rectangle()
                    .fill(active ? AppColor.brand : Color.clear)
                    .frame(height: 2)
            }
            // Full-width, comfortably padded, fully hit-testable tap target.
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppDimens.spacingSM)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: Tab content

    @ViewBuilder
    private var tabContent: some View {
        switch selectedTab {
        case .profile:
            profileTab
        case .account:
            accountTab
        }
    }

    // MARK: Profil tab

    private var profileTab: some View {
        VStack(alignment: .leading, spacing: AppDimens.spacingXL) {
            bannerRow
            avatarRow
            EditField(label: loc.strings.fieldName) {
                textField($name)
            }
            bioField
            EditField(label: loc.strings.fieldCity) {
                textField($city)
            }
            privateProfileRow
            SheetActionButton(
                icon: "checkmark",
                label: loc.strings.save,
                style: .primary,
                action: {}
            )
            blockedUsersRow
            logoutButton
            deleteAccountButton
        }
    }

    /// Banner placeholder: a short gradient strip + two no-op actions. No real
    /// picker. Built only from AppColor tokens (no raw hex literals).
    private var bannerRow: some View {
        VStack(alignment: .leading, spacing: AppDimens.spacingMD) {
            LinearGradient(
                colors: [
                    AppColor.brand.opacity(0.85),
                    AppColor.secondary.opacity(0.65),
                    AppColor.page,
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .frame(height: 72)
            .clipShape(RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous))

            HStack(spacing: AppDimens.spacingMD) {
                SheetActionButton(
                    icon: "photo",
                    label: loc.strings.chooseBanner,
                    style: .bordered,
                    action: {}
                )
                SheetActionButton(
                    icon: "trash",
                    label: loc.strings.remove,
                    style: .bordered,
                    action: {}
                )
            }
        }
    }

    /// Avatar placeholder: the current avatar + two no-op actions. The avatar
    /// initial is derived from the locally edited `name`.
    private var avatarRow: some View {
        HStack(spacing: AppDimens.spacingLG) {
            AvatarView(name: name, size: 64)

            VStack(alignment: .leading, spacing: AppDimens.spacingSM) {
                SheetActionButton(
                    icon: "photo",
                    label: loc.strings.chooseAvatar,
                    style: .bordered,
                    action: {}
                )
                SheetActionButton(
                    icon: "trash",
                    label: loc.strings.remove,
                    style: .bordered,
                    action: {}
                )
            }
        }
    }

    /// Bio editor with the CreatePostView placeholder-overlay pattern.
    private var bioField: some View {
        EditField(label: loc.strings.fieldBio) {
            ZStack(alignment: .topLeading) {
                if bio.isEmpty {
                    Text(loc.strings.bioPlaceholder)
                        .font(.system(size: AppTypography.body))
                        .foregroundColor(AppColor.textFaint)
                        .padding(.horizontal, AppDimens.spacingLG)
                        .padding(.vertical, AppDimens.spacingMD)
                }
                TextEditor(text: $bio)
                    .font(.system(size: AppTypography.body))
                    .foregroundColor(AppColor.foreground)
                    .scrollContentBackground(.hidden)
                    .padding(.horizontal, AppDimens.spacingMD)
                    .padding(.vertical, AppDimens.spacingSM)
                    .frame(minHeight: 90)
            }
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

    /// Private-profile toggle row: title + subtitle + a brand-tinted Toggle
    /// bound to local state (no persistence).
    private var privateProfileRow: some View {
        HStack(alignment: .top, spacing: AppDimens.spacingMD) {
            VStack(alignment: .leading, spacing: AppDimens.spacingXS) {
                Text(loc.strings.privateProfile)
                    .font(.system(size: AppTypography.body, weight: .semibold))
                    .foregroundColor(AppColor.foreground)
                Text(loc.strings.privateProfileSubtitle)
                    .font(.system(size: AppTypography.footnote))
                    .foregroundColor(AppColor.mutedForeground)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: AppDimens.spacingSM)
            Toggle("", isOn: $isPrivate)
                .labelsHidden()
                .tint(AppColor.brand)
        }
        .padding(.horizontal, AppDimens.spacingLG)
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

    /// Blocked-users row: bordered row with a trailing count. No-op (no
    /// destination yet).
    private var blockedUsersRow: some View {
        HStack(spacing: AppDimens.spacingMD) {
            Text(loc.strings.blockedUsers)
                .font(.system(size: AppTypography.body, weight: .medium))
                .foregroundColor(AppColor.foreground)
            Spacer(minLength: AppDimens.spacingSM)
            Text("0")
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.mutedForeground)
            Image(systemName: "chevron.right")
                .font(.system(size: AppTypography.footnote, weight: .semibold))
                .foregroundColor(AppColor.textFaint)
        }
        .padding(.horizontal, AppDimens.spacingLG)
        .padding(.vertical, AppDimens.spacingLG)
        .contentShape(Rectangle())
        .background(
            RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                .fill(AppColor.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                .stroke(AppColor.border, lineWidth: AppDimens.hairline)
        )
    }

    /// Log-out button: bordered with destructive-colored text. No-op.
    private var logoutButton: some View {
        Button(action: {}) {
            HStack(spacing: AppDimens.spacingSM) {
                Image(systemName: "rectangle.portrait.and.arrow.right")
                    .font(.system(size: AppTypography.body, weight: .semibold))
                Text(loc.strings.logout)
                    .font(.system(size: AppTypography.body, weight: .semibold))
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppDimens.spacingMD)
            .padding(.horizontal, AppDimens.spacingLG)
            .foregroundColor(AppColor.destructive)
            .background(
                RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                    .fill(AppColor.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                    .stroke(AppColor.border, lineWidth: AppDimens.hairline)
            )
        }
        .buttonStyle(.plain)
    }

    /// Delete-account button: destructive-tinted filled button. No-op.
    private var deleteAccountButton: some View {
        Button(action: {}) {
            HStack(spacing: AppDimens.spacingSM) {
                Image(systemName: "trash")
                    .font(.system(size: AppTypography.body, weight: .semibold))
                Text(loc.strings.deleteAccount)
                    .font(.system(size: AppTypography.body, weight: .semibold))
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppDimens.spacingMD)
            .padding(.horizontal, AppDimens.spacingLG)
            .foregroundColor(.white)
            .background(
                RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                    .fill(AppColor.destructive)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: Konto tab

    private var accountTab: some View {
        VStack(alignment: .leading, spacing: AppDimens.spacingXL) {
            changeEmailSection
            Rectangle()
                .fill(AppColor.hairline)
                .frame(height: AppDimens.hairline)
            changePasswordSection
        }
    }

    private var changeEmailSection: some View {
        VStack(alignment: .leading, spacing: AppDimens.spacingMD) {
            Text(loc.strings.changeEmail)
                .font(.system(size: AppTypography.body, weight: .semibold))
                .foregroundColor(AppColor.foreground)
            // Dynamic content: the current email is NOT translated; the
            // "Aktuell:"/"Current:" prefix comes from i18n.
            Text(loc.strings.accountCurrentEmail(email: profile.email))
                .font(.system(size: AppTypography.footnote))
                .foregroundColor(AppColor.mutedForeground)

            fieldSurface {
                TextField(loc.strings.newEmailPlaceholder, text: $newEmail)
                    .font(.system(size: AppTypography.body))
                    .foregroundColor(AppColor.foreground)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
            }

            SheetActionButton(
                icon: "envelope",
                label: loc.strings.sendConfirmationEmail,
                style: .primary,
                action: {}
            )
        }
    }

    private var changePasswordSection: some View {
        VStack(alignment: .leading, spacing: AppDimens.spacingMD) {
            Text(loc.strings.changePassword)
                .font(.system(size: AppTypography.body, weight: .semibold))
                .foregroundColor(AppColor.foreground)

            fieldSurface {
                SecureField(loc.strings.newPasswordPlaceholder, text: $newPassword)
                    .font(.system(size: AppTypography.body))
                    .foregroundColor(AppColor.foreground)
            }
            fieldSurface {
                SecureField(loc.strings.confirmPasswordPlaceholder, text: $confirmPassword)
                    .font(.system(size: AppTypography.body))
                    .foregroundColor(AppColor.foreground)
            }

            SheetActionButton(
                icon: "lock",
                label: loc.strings.changePasswordButton,
                style: .primary,
                action: {}
            )
        }
    }

    // MARK: Small local helpers

    /// A single-line text field bound to [binding] with the standard surface
    /// styling.
    private func textField(_ binding: Binding<String>) -> some View {
        fieldSurface {
            TextField("", text: binding)
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.foreground)
        }
    }

    /// Wraps [content] in the app's standard surface + border field chrome
    /// (same look as CreatePostView's search field).
    private func fieldSurface<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        content()
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

// MARK: - Private subviews

/// A labeled field wrapper: a semibold caption above arbitrary field content.
private struct EditField<Content: View>: View {
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
