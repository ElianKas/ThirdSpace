import SwiftUI

/// Native SwiftUI report ("Melden") sheet.
///
/// Reached from the three-dots (ellipsis) menu on each `PostCardView`. Lets the
/// user pick a single reason, optionally add details, and submit. Submit is a
/// no-op for now (real reporting logic comes later) — it just dismisses.
///
/// Follows the project sheet pattern: the PRESENTER (the ellipsis menu in
/// `PostCardView`) owns `.sheet(isPresented:)` + `.presentationDetents` +
/// `.presentationDragIndicator`. This view only renders the content, reusing
/// `SheetScaffold` for the title row + consistent padding.
///
/// Mirrors the legacy `ReportSheet.tsx`: flag icon + "Melden" title, optional
/// context subtitle, an anonymity hint, a single-select list of reasons (from
/// the shared module via `FeedData.reportReasons()`), an optional details
/// field, and a red "Report senden" button disabled until a reason is chosen.
///
/// Visual/local only: no backend, no persistence. Uses `AppColor` tokens only.
struct ReportSheetView: View {
    @Environment(Localization.self) private var loc
    let post: UIPost
    /// Optional close handler. Falls back to the environment dismiss so the
    /// view works whether or not the presenter passes one.
    var onClose: (() -> Void)? = nil

    @Environment(\.dismiss) private var dismiss

    /// Selected reason key (single select). `nil` until the user picks one,
    /// which keeps the submit button disabled.
    @State private var selectedReasonKey: String? = nil
    /// Optional free-text details (local only, never submitted anywhere yet).
    @State private var details: String = ""

    // Computed (not a stored `let`) so the reason labels re-resolve from the
    // shared bridge on every render. A language change re-renders this view
    // (its body reads `loc.strings.*`), so the reasons pick up the new language
    // live instead of staying frozen at their first-resolved values.
    private var reasons: [UIReportReason] { FeedData.reportReasons() }

    private func close() {
        if let onClose {
            onClose()
        } else {
            dismiss()
        }
    }

    var body: some View {
        ScrollView {
            SheetScaffold(title: nil, onClose: { close() }) {
                VStack(alignment: .leading, spacing: AppDimens.spacingLG) {
                    header
                    if !contextLabel.isEmpty {
                        Text(contextLabel)
                            .font(.system(size: AppTypography.body))
                            .foregroundColor(AppColor.mutedForeground)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    hint
                    reasonList
                    detailsEditor
                    submitButton
                }
            }
        }
        .background(AppColor.background)
    }

    // MARK: - Context subtitle

    /// Short context describing what is being reported (spot + author).
    private var contextLabel: String {
        "\(post.spotName) · \(post.authorName)"
    }

    // MARK: - Header (flag icon + title)

    private var header: some View {
        HStack(spacing: AppDimens.spacingSM) {
            Image(systemName: "flag")
                .font(.system(size: AppTypography.titleLarge, weight: .semibold))
                .foregroundColor(AppColor.destructive)
            Text(loc.strings.reportTitle)
                .font(.system(size: AppTypography.titleLarge, weight: .bold))
                .foregroundColor(AppColor.foreground)
            Spacer(minLength: 0)
        }
    }

    // MARK: - Anonymity hint

    private var hint: some View {
        Text(loc.strings.reportHint)
            .font(.system(size: AppTypography.footnote))
            .foregroundColor(AppColor.mutedForeground)
            .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: - Reason list (single select)

    private var reasonList: some View {
        VStack(spacing: AppDimens.spacingSM) {
            ForEach(reasons) { reason in
                ReportReasonRow(
                    label: reason.label,
                    selectedLabel: loc.strings.reportSelected,
                    selected: selectedReasonKey == reason.key,
                    action: { selectedReasonKey = reason.key }
                )
            }
        }
    }

    // MARK: - Details editor (optional)

    private var detailsEditor: some View {
        AppTextEditorField(
            placeholder: loc.strings.reportDetailsPlaceholder,
            text: $details,
            minHeight: 88
        )
    }

    // MARK: - Submit button (red, disabled until a reason is chosen)

    private var submitButton: some View {
        // Shared destructive button. Its previously taller `spacingLG` padding
        // is unified onto the standard height; the enabled logic
        // (a reason must be chosen) + the ONE disabled convention stay via
        // `isEnabled`.
        AppButton(
            label: loc.strings.reportSubmit,
            style: .destructive,
            isEnabled: selectedReasonKey != nil,
            action: {
                // No-op for now (real reporting logic added later). Just dismiss.
                close()
            }
        )
    }
}

// MARK: - Reason row

/// A single selectable reason row. When selected it gains a brand-tinted
/// border + fill and shows an "Ausgewählt" marker, mirroring the legacy sheet.
/// Uses only `AppColor` tokens.
private struct ReportReasonRow: View {
    let label: String
    /// Localized "selected" marker text (e.g. "Ausgewählt" / "Selected").
    let selectedLabel: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: AppDimens.spacingSM) {
                Text(label)
                    .font(.system(size: AppTypography.body, weight: selected ? .semibold : .regular))
                    .foregroundColor(AppColor.foreground)
                Spacer(minLength: 0)
                if selected {
                    HStack(spacing: AppDimens.spacingXS) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: AppTypography.body))
                        Text(selectedLabel)
                            .font(.system(size: AppTypography.caption, weight: .semibold))
                    }
                    .foregroundColor(AppColor.brand)
                }
            }
            .padding(.horizontal, AppDimens.spacingLG)
            .padding(.vertical, AppDimens.spacingMD)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                    .fill(selected ? AppColor.brand.opacity(0.08) : AppColor.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                    .stroke(selected ? AppColor.brand : AppColor.border, lineWidth: selected ? 1.5 : AppDimens.hairline)
            )
        }
        .buttonStyle(.plain)
    }
}
