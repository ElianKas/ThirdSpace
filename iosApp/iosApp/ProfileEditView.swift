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
                    .padding(.top, 8)

                tabContent
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 32)
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
                .frame(height: 1)
        }
    }

    private func tabButton(_ tab: EditTab, title: String) -> some View {
        let active = selectedTab == tab
        return Button {
            selectedTab = tab
        } label: {
            VStack(spacing: 8) {
                Text(title)
                    .font(.system(size: 14, weight: active ? .semibold : .regular))
                    .foregroundColor(active ? AppColor.brand : AppColor.mutedForeground)
                Rectangle()
                    .fill(active ? AppColor.brand : Color.clear)
                    .frame(height: 2)
            }
            // Full-width, comfortably padded, fully hit-testable tap target.
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
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
        VStack(alignment: .leading, spacing: 20) {
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
        VStack(alignment: .leading, spacing: 10) {
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
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

            HStack(spacing: 10) {
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
        HStack(spacing: 14) {
            AvatarView(name: name, size: 64)

            VStack(alignment: .leading, spacing: 8) {
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
                        .font(.system(size: 14))
                        .foregroundColor(AppColor.textFaint)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                }
                TextEditor(text: $bio)
                    .font(.system(size: 14))
                    .foregroundColor(AppColor.foreground)
                    .scrollContentBackground(.hidden)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .frame(minHeight: 90)
            }
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(AppColor.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(AppColor.border, lineWidth: 1)
            )
        }
    }

    /// Private-profile toggle row: title + subtitle + a brand-tinted Toggle
    /// bound to local state (no persistence).
    private var privateProfileRow: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(loc.strings.privateProfile)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(AppColor.foreground)
                Text(loc.strings.privateProfileSubtitle)
                    .font(.system(size: 13))
                    .foregroundColor(AppColor.mutedForeground)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            Toggle("", isOn: $isPrivate)
                .labelsHidden()
                .tint(AppColor.brand)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(AppColor.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(AppColor.border, lineWidth: 1)
        )
    }

    /// Blocked-users row: bordered row with a trailing count. No-op (no
    /// destination yet).
    private var blockedUsersRow: some View {
        HStack(spacing: 10) {
            Text(loc.strings.blockedUsers)
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(AppColor.foreground)
            Spacer(minLength: 8)
            Text("0")
                .font(.system(size: 15))
                .foregroundColor(AppColor.mutedForeground)
            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(AppColor.textFaint)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .contentShape(Rectangle())
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(AppColor.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(AppColor.border, lineWidth: 1)
        )
    }

    /// Log-out button: bordered with destructive-colored text. No-op.
    private var logoutButton: some View {
        Button(action: {}) {
            HStack(spacing: 8) {
                Image(systemName: "rectangle.portrait.and.arrow.right")
                    .font(.system(size: 15, weight: .semibold))
                Text(loc.strings.logout)
                    .font(.system(size: 14, weight: .semibold))
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .padding(.horizontal, 14)
            .foregroundColor(AppColor.destructive)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(AppColor.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(AppColor.border, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    /// Delete-account button: destructive-tinted filled button. No-op.
    private var deleteAccountButton: some View {
        Button(action: {}) {
            HStack(spacing: 8) {
                Image(systemName: "trash")
                    .font(.system(size: 15, weight: .semibold))
                Text(loc.strings.deleteAccount)
                    .font(.system(size: 14, weight: .semibold))
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .padding(.horizontal, 14)
            .foregroundColor(.white)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(AppColor.destructive)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: Konto tab

    private var accountTab: some View {
        VStack(alignment: .leading, spacing: 24) {
            changeEmailSection
            Rectangle()
                .fill(AppColor.hairline)
                .frame(height: 1)
            changePasswordSection
        }
    }

    private var changeEmailSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(loc.strings.changeEmail)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(AppColor.foreground)
            // Dynamic content: the current email is NOT translated; the
            // "Aktuell:"/"Current:" prefix comes from i18n.
            Text(loc.strings.accountCurrentEmail(email: profile.email))
                .font(.system(size: 13))
                .foregroundColor(AppColor.mutedForeground)

            fieldSurface {
                TextField(loc.strings.newEmailPlaceholder, text: $newEmail)
                    .font(.system(size: 14))
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
        VStack(alignment: .leading, spacing: 10) {
            Text(loc.strings.changePassword)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(AppColor.foreground)

            fieldSurface {
                SecureField(loc.strings.newPasswordPlaceholder, text: $newPassword)
                    .font(.system(size: 14))
                    .foregroundColor(AppColor.foreground)
            }
            fieldSurface {
                SecureField(loc.strings.confirmPasswordPlaceholder, text: $confirmPassword)
                    .font(.system(size: 14))
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
                .font(.system(size: 14))
                .foregroundColor(AppColor.foreground)
        }
    }

    /// Wraps [content] in the app's standard surface + border field chrome
    /// (same look as CreatePostView's search field).
    private func fieldSurface<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        content()
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(AppColor.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(AppColor.border, lineWidth: 1)
            )
    }
}

// MARK: - Private subviews

/// A labeled field wrapper: a semibold caption above arbitrary field content.
private struct EditField<Content: View>: View {
    let label: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(label)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(AppColor.foreground)
            content()
        }
    }
}
