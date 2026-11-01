import SwiftUI

/// Reusable, token-styled INPUT-FIELD components (Workstream B, slice B1).
///
/// This is the single home for the app's text-input chrome and the readable
/// placeholder color. Before this existed, the surface + hairline-border field
/// look was hand-rolled in ~6 files with drifting radius/padding/height, and
/// three different placeholder-color strategies coexisted (the plain
/// `TextField` system default at ~1.4:1, the `TextEditor` overlays' `textFaint`
/// at ~2.4:1, and empty placeholders) — exactly the "placeholder unlesbar"
/// complaint. Everything here styles ONLY with `AppColor` / `AppDimens` /
/// `AppTypography` tokens — no hardcoded hex or magic numbers — and renders
/// every placeholder in `AppColor.placeholder` (#6B6760, clears WCAG AA).
///
/// Buttons (slice B2) now also live here — see `AppButton` / `AppIconButton`
/// below. Rows/pills/empty-states (slice B3) are intentionally NOT touched here.

// MARK: - Field chrome modifier

/// The canonical input-field chrome: symmetric `spacingMD` padding, a white
/// `surface` fill, a hairline `border` stroke, rounded to `radiusMD`. This is
/// the single source of truth for input-field chrome and replaces the
/// duplicated `authFieldSurface` (AuthComponents) and `fieldSurface`
/// (ProfileEditView) helpers.
private struct AppFieldSurface: ViewModifier {
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

extension View {
    /// Apply the app's standard surface + hairline-border input-field chrome
    /// (`surface` fill, `border` stroke @ `hairline`, `radiusMD`, symmetric
    /// `spacingMD` padding). Use on anything that should read as an input field.
    func appFieldSurface() -> some View { modifier(AppFieldSurface()) }
}

// MARK: - Field label

/// The repeated "footnote, semibold, foreground" caption shown above an input
/// field. Consolidates the inline label `Text`s plus the duplicated
/// `EditField` (ProfileEditView) and `AuthField` (AuthComponents) label styles.
struct FieldLabel: View {
    let text: String

    init(_ text: String) {
        self.text = text
    }

    var body: some View {
        Text(text)
            .font(.system(size: AppTypography.footnote, weight: .semibold))
            .foregroundColor(AppColor.foreground)
    }
}

/// A labeled field wrapper: a `FieldLabel` above arbitrary field content,
/// stacked with `spacingMD`. Optional convenience over writing the `VStack`
/// yourself; replaces the `EditField` / `AuthField` wrapper structs.
struct LabeledField<Content: View>: View {
    let label: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: AppDimens.spacingMD) {
            FieldLabel(label)
            content()
        }
    }
}

// MARK: - Single-line text field

/// A single-line text input with the standard `appFieldSurface` chrome and a
/// READABLE placeholder (`AppColor.placeholder`, rendered via the `prompt:`
/// parameter with a styled `Text` — a plain `TextField` can't recolor its
/// placeholder otherwise).
///
/// Parameters:
/// - `placeholder`: placeholder/prompt text (shown in `AppColor.placeholder`).
/// - `text`: the bound string.
/// - `isEmail`: when `true`, applies the email keyboard + disables
///   autocapitalization/autocorrection (for email entry).
/// - `axis`: `.horizontal` (default) or `.vertical` for a growing field.
/// - `lineLimit`: optional line-limit range (used with `axis: .vertical`, e.g.
///   `1...4` for the comment input). `nil` leaves the default behavior.
/// - `leadingSystemImage`: optional SF Symbol drawn inside the field, before
///   the text (e.g. the spot-search `magnifyingglass`), tinted `placeholder`.
struct AppTextField: View {
    let placeholder: String
    @Binding var text: String
    var isEmail: Bool = false
    var axis: Axis = .horizontal
    var lineLimit: ClosedRange<Int>? = nil
    var leadingSystemImage: String? = nil

    var body: some View {
        HStack(spacing: AppDimens.spacingSM) {
            if let leadingSystemImage {
                Image(systemName: leadingSystemImage)
                    .font(.system(size: AppTypography.body))
                    .foregroundColor(AppColor.placeholder)
            }
            field
        }
        .appFieldSurface()
    }

    @ViewBuilder
    private var field: some View {
        let base = TextField(
            "",
            text: $text,
            prompt: Text(placeholder).foregroundColor(AppColor.placeholder),
            axis: axis
        )
        .font(.system(size: AppTypography.body))
        .foregroundColor(AppColor.foreground)

        let limited = Group {
            if let lineLimit {
                base.lineLimit(lineLimit)
            } else {
                base
            }
        }

        if isEmail {
            limited
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
        } else {
            limited
        }
    }
}

// MARK: - Secure (masked) field

/// A secure (masked) single-line field with the same chrome + readable
/// placeholder as `AppTextField`. `SecureField`'s `prompt:` parameter DOES
/// recolor the placeholder (same as `TextField`), so no overlay is needed.
/// Kept as a separate type (rather than an `isSecure` flag on `AppTextField`)
/// because `SecureField` has a distinct type and no `axis`/`lineLimit`.
struct AppSecureField: View {
    let placeholder: String
    @Binding var text: String

    var body: some View {
        SecureField(
            "",
            text: $text,
            prompt: Text(placeholder).foregroundColor(AppColor.placeholder)
        )
        .font(.system(size: AppTypography.body))
        .foregroundColor(AppColor.foreground)
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled(true)
        .appFieldSurface()
    }
}

// MARK: - Multi-line editor field

/// A multi-line `TextEditor` with a placeholder, using the `ZStack`-overlay
/// pattern the three existing editors used — but with the placeholder rendered
/// in `AppColor.placeholder` (NOT the old `textFaint`) and the standard
/// `surface` + hairline-border chrome at `radiusMD`. (A bare `TextEditor` has
/// no `prompt:`, so the overlay is still required here.)
///
/// Parameters:
/// - `placeholder`: placeholder text (shown when empty, in `AppColor.placeholder`).
/// - `text`: the bound string.
/// - `minHeight`: minimum editor height (default 90).
struct AppTextEditorField: View {
    let placeholder: String
    @Binding var text: String
    var minHeight: CGFloat = 90

    var body: some View {
        ZStack(alignment: .topLeading) {
            if text.isEmpty {
                Text(placeholder)
                    .font(.system(size: AppTypography.body))
                    .foregroundColor(AppColor.placeholder)
                    .padding(.horizontal, AppDimens.spacingLG)
                    .padding(.vertical, AppDimens.spacingMD)
                    .allowsHitTesting(false)
            }
            TextEditor(text: $text)
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.foreground)
                .scrollContentBackground(.hidden)
                .padding(.horizontal, AppDimens.spacingMD)
                .padding(.vertical, AppDimens.spacingSM)
                .frame(minHeight: minHeight)
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

// MARK: - Buttons (slice B2)

/// Reusable, token-styled TEXT buttons (Workstream B, slice B2).
///
/// Before this existed, `SheetActionButton` / `SheetWideButton` coexisted with
/// ~8 ad-hoc buttons that drifted in three ways: the DISABLED treatment
/// (fill→`mutedForeground`, fill→`textFaint`, `.opacity(0.5)`, `.opacity(0.6)`,
/// or nothing), the HEIGHT model (`spacingMD` vs `spacingLG` vertical padding,
/// or a fixed `height: 44`), and the "soft/secondary" look (`surface`+border vs
/// `page`+border). `AppButton` collapses all of them onto ONE styling model.
///
/// ONE disabled convention: a disabled (or working) button renders at
/// `.opacity(0.5)` on an otherwise-unchanged fill — NO fill swaps — and is
/// `.disabled(!isEnabled || isWorking)`. ONE height model: `.padding(.vertical,
/// AppDimens.spacingMD)` for every style (+ `.padding(.horizontal,
/// AppDimens.spacingLG)` when not full-width, matching the old `SheetWideButton`
/// / delete / logout rows). Corner radius is always `radiusMD`.
///
/// All styling is tokens only (`AppColor` / `AppDimens` / `AppTypography`) — no
/// hardcoded hex or magic numbers.
struct AppButton: View {
    /// The visual styles covered by the audit's button inventory.
    enum Style {
        /// Filled `brand`, white text, no stroke (primary actions).
        case primary
        /// `surface` fill, `foreground` text, hairline `border` stroke
        /// (the bordered sheet-action look).
        case secondary
        /// `page` fill, `foreground` text, hairline `border` stroke
        /// (the "edit profile" / not-following look).
        case soft
        /// Filled `destructive`, white text (destructive-primary).
        case destructive
        /// `surface` fill, `destructive` text, hairline `border` stroke
        /// (the quiet logout look / destructive-secondary).
        case destructiveQuiet

        /// The fill color for this style.
        var fill: Color {
            switch self {
            case .primary: return AppColor.brand
            case .secondary: return AppColor.surface
            case .soft: return AppColor.page
            case .destructive: return AppColor.destructive
            case .destructiveQuiet: return AppColor.surface
            }
        }

        /// The label/icon color for this style.
        var foreground: Color {
            switch self {
            case .primary, .destructive: return .white
            case .secondary, .soft: return AppColor.foreground
            case .destructiveQuiet: return AppColor.destructive
            }
        }

        /// Whether this style draws a hairline `border` stroke.
        var stroked: Bool {
            switch self {
            case .primary, .destructive: return false
            case .secondary, .soft, .destructiveQuiet: return true
            }
        }
    }

    let label: String
    var style: Style = .primary
    /// Optional leading SF Symbol drawn before the label.
    var systemImage: String? = nil
    /// When `true` (default) the button fills the available width; when `false`
    /// it hugs its content and gains symmetric horizontal padding.
    var fullWidth: Bool = true
    /// Enabled state. A disabled button dims to `.opacity(0.5)` and ignores taps.
    var isEnabled: Bool = true
    /// When `true` a spinner replaces nothing (shown before the label) and the
    /// button is treated as disabled (for the auth buttons' in-flight state).
    var isWorking: Bool = false
    let action: () -> Void

    /// Whether the button content is interactive right now.
    private var active: Bool { isEnabled && !isWorking }

    var body: some View {
        Button(action: action) {
            AppButtonLabel(
                label: label,
                style: style,
                systemImage: systemImage,
                fullWidth: fullWidth,
                isWorking: isWorking
            )
            .opacity(active ? 1 : 0.5)
        }
        .buttonStyle(.plain)
        .disabled(!active)
    }
}

/// The styled CONTENT of an `AppButton`, factored out so a `NavigationLink` can
/// adopt the exact same look via its `label:` without being a nested `Button`
/// (e.g. `ProfileView`'s edit link, which must push, not run an action). Styling
/// is identical to `AppButton`'s body; tokens only.
struct AppButtonLabel: View {
    let label: String
    var style: AppButton.Style = .primary
    var systemImage: String? = nil
    var fullWidth: Bool = true
    var isWorking: Bool = false

    var body: some View {
        HStack(spacing: AppDimens.spacingSM) {
            if isWorking {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(style.foreground)
            }
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.system(size: AppTypography.body, weight: .semibold))
            }
            Text(label)
                .font(.system(size: AppTypography.body, weight: .semibold))
                .lineLimit(1)
        }
        // The icon+label HStack is CENTERED in BOTH modes. A full-width button
        // fills the width (`.frame(maxWidth: .infinity)`) with its content
        // centered horizontally by the frame; a hugging button sizes to its
        // content (`maxWidth: nil`), which also reads centered within its own
        // bounds. There is no trailing `Spacer` — it would left-align the
        // content; dropping it is what centers the non-full-width rows (edit
        // profile, logout, delete, follow, create-collection) instead of
        // leaving them leading-aligned.
        .frame(maxWidth: fullWidth ? .infinity : nil)
        .multilineTextAlignment(.center)
        .padding(.vertical, AppDimens.spacingMD)
        .padding(.horizontal, fullWidth ? 0 : AppDimens.spacingLG)
        .foregroundColor(style.foreground)
        .background(
            RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                .fill(style.fill)
        )
        .overlay(
            RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                .stroke(style.stroked ? AppColor.border : Color.clear, lineWidth: AppDimens.hairline)
        )
    }
}

/// A compact, icon-only button (Workstream B, slice B2). Renders an SF Symbol
/// centered in a `size`×`size` circle or rounded square filled with `fill`.
/// Replaces the hand-rolled comment-send circle and the FAB circle, which used
/// bespoke disabled treatments (the send button swapped its fill to
/// `textFaint`). ONE disabled convention: `.opacity(0.5)` on an unchanged fill
/// + `.disabled`.
///
/// Styling is tokens only; `fill`/`foreground` default to `brand`/white but may
/// be overridden (e.g. a frosted map-control variant).
struct AppIconButton: View {
    /// The button's silhouette.
    enum Shape { case circle, roundedSquare }

    let systemImage: String
    var shape: Shape = .circle
    let size: CGFloat
    var fill: Color = AppColor.brand
    var foreground: Color = .white
    var isEnabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: AppTypography.title, weight: .semibold))
                .foregroundColor(foreground)
                .frame(width: size, height: size)
                .background(background)
                .opacity(isEnabled ? 1 : 0.5)
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
    }

    @ViewBuilder
    private var background: some View {
        switch shape {
        case .circle:
            Circle().fill(fill)
        case .roundedSquare:
            RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous).fill(fill)
        }
    }
}

// MARK: - List row (slice B3)

/// Reusable, token-styled LIST ROW (Workstream B, slice B3).
///
/// Before this existed, four separately-declared private row structs shared
/// almost the same layout and drifted independently: `ProfileSpotRow`
/// (ProfileView) and `CollectionSpotRow` (CollectionDetailView) were
/// byte-identical (44×44 color swatch + title + "category · neighborhood"),
/// while `FollowUserRow` (FollowListView) and `CollectionRow` (ProfileView)
/// shared the exact trailing-chevron + two-line-text pattern (differing only in
/// the leading avatar-vs-none and the optional faint third line). `AppListRow`
/// collapses all four onto ONE row so they stay visually identical.
///
/// The row is ONLY the row CONTENT — the surrounding `NavigationLink` / `Button`
/// wrappers and the hairline dividers between rows stay at the call sites,
/// unchanged. Styling is tokens only (`AppColor` / `AppDimens` / `AppTypography`)
/// — no hardcoded hex or magic numbers.
struct AppListRow: View {
    /// The optional leading element.
    enum Leading {
        /// A 44×44 `radiusSM` rounded-rect color swatch filled with `Color(hex:)`
        /// (the profile-spots / collection-detail swatch).
        case swatch(colorHex: String)
        /// A 44pt `AvatarView` seeded with the given name (the follow row).
        case avatar(name: String)
        /// No leading element (the collections row).
        case none
    }

    /// The optional trailing element.
    enum Trailing {
        /// A `chevron.right` (`footnote` semibold `textFaint`) — a row that
        /// pushes/opens a detail (follow row, collections row).
        case chevron
        /// No trailing element (the spot rows).
        case none
    }

    let leading: Leading
    let title: String
    let subtitle: String
    /// Optional faint third line (`footnote` `textFaint`) — e.g. the follow
    /// row's city. `nil` hides it.
    var detail: String? = nil
    var trailing: Trailing = .none

    var body: some View {
        HStack(spacing: AppDimens.spacingMD) {
            leadingView

            VStack(alignment: .leading, spacing: AppDimens.spacingXS) {
                Text(title)
                    .font(.system(size: AppTypography.body, weight: .semibold))
                    .foregroundColor(AppColor.foreground)
                Text(subtitle)
                    .font(.system(size: AppTypography.footnote))
                    .foregroundColor(AppColor.mutedForeground)
                if let detail, !detail.isEmpty {
                    Text(detail)
                        .font(.system(size: AppTypography.footnote))
                        .foregroundColor(AppColor.textFaint)
                }
            }

            Spacer(minLength: 0)

            trailingView
        }
        .padding(.horizontal, AppDimens.spacingXL)
        .padding(.vertical, AppDimens.spacingMD)
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private var leadingView: some View {
        switch leading {
        case let .swatch(colorHex):
            RoundedRectangle(cornerRadius: AppDimens.radiusSM)
                .fill(Color(hex: colorHex))
                .frame(width: 44, height: 44)
        case let .avatar(name):
            AvatarView(name: name, size: 44)
        case .none:
            EmptyView()
        }
    }

    @ViewBuilder
    private var trailingView: some View {
        switch trailing {
        case .chevron:
            Image(systemName: "chevron.right")
                .font(.system(size: AppTypography.footnote, weight: .semibold))
                .foregroundColor(AppColor.textFaint)
        case .none:
            EmptyView()
        }
    }
}

// MARK: - Chip / pill (slice B3)

/// Reusable, token-styled PILL / CHIP (Workstream B, slice B3).
///
/// Before this existed, three "rounded capsule label" implementations drifted:
/// `CategoryPill` (SheetComponents — static `surface` fill, `caption` font,
/// padding-derived height, no active state), `FilterChipView` (MapBaseView —
/// `frost`/`brand` fill, `footnote`, fixed height 30), and the AddSpot category
/// pill (`frost`/category-color, `footnote`, fixed height 30). The latter two
/// were near-identical. `AppChip` unifies all three; styling is tokens only.
struct AppChip: View {
    /// The chip's visual style.
    enum Style {
        /// A static badge: `surface` fill, `border` stroke, `caption` font,
        /// padding-derived height (the old `CategoryPill` look). No active state.
        case badge
        /// A selectable chip: `frost` (inactive) ↔ `accent` (active, white text),
        /// `footnote`, fixed height 30, `border` stroke when inactive / clear
        /// when active (the old `FilterChipView` + AddSpot category-pill look).
        /// `accent` lets AddSpot pass the per-category color; it defaults to
        /// `brand` for the generic filter chip.
        case selectable(active: Bool, accent: Color = AppColor.brand)
    }

    let label: String
    var style: Style = .badge

    var body: some View {
        switch style {
        case .badge:
            Text(label)
                .font(.system(size: AppTypography.caption, weight: .medium))
                .foregroundColor(AppColor.foreground)
                .padding(.horizontal, AppDimens.spacingMD)
                .padding(.vertical, AppDimens.spacingSM)
                .background(Capsule().fill(AppColor.surface))
                .overlay(Capsule().stroke(AppColor.border, lineWidth: AppDimens.hairline))
        case let .selectable(active, accent):
            Text(label)
                .font(.system(size: AppTypography.footnote, weight: .medium))
                .foregroundColor(active ? .white : AppColor.foreground)
                .padding(.horizontal, AppDimens.spacingLG)
                .frame(height: 30)
                .background(Capsule().fill(active ? accent : AppColor.frost))
                .overlay(
                    Capsule().stroke(
                        active ? Color.clear : AppColor.border,
                        lineWidth: AppDimens.hairline
                    )
                )
        }
    }
}

// MARK: - Empty state (slice B3)

/// Reusable, token-styled EMPTY STATE (Workstream B, slice B3).
///
/// Before this existed, three icon+muted-text empty-state VStacks were
/// copy-pasted (`CommentsSheetView`, `CollectionDetailView`, `FollowListView`),
/// plus two inconsistent text-only variants (`CollectionPickerSheetView`,
/// `ProfileView`) that lacked the icon. `EmptyStateView` unifies all five:
/// an SF Symbol (`headline` `textFaint`) over text (`body` `mutedForeground`),
/// centered in a `spacingMD`-gap VStack, with `spacingXXL` padding, filling the
/// available space. Styling is tokens only.
struct EmptyStateView: View {
    let systemImage: String
    let text: String

    var body: some View {
        VStack(spacing: AppDimens.spacingMD) {
            Image(systemName: systemImage)
                .font(.system(size: AppTypography.headline))
                .foregroundColor(AppColor.textFaint)
            Text(text)
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.mutedForeground)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(AppDimens.spacingXXL)
    }
}
