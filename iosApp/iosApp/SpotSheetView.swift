import SwiftUI

/// Native SwiftUI Spot detail sheet (V1).
///
/// This is the first interactive surface in the native feed: tapping a
/// `PostCardView` in `NativeFeedView` presents this view as a sheet. It renders
/// a single spot's detail, top to bottom: a placeholder photo area, a category
/// badge (+ verified seal), the spot title, a meta + vote row, a placeholder
/// description, an action row (Route / Speichern / Ich war hier), and full-width
/// comment and share buttons.
///
/// REUSABLE SHEET PATTERN — all future detail/compose surfaces should follow the
/// same approach used by the call site in `NativeFeedView`:
///
///     .sheet(item: $selectedPost) { post in
///         SpotSheetView(post: post)
///             .presentationDetents([.medium, .large])
///             .presentationDragIndicator(.visible)
///     }
///
/// i.e. drive presentation from an `Identifiable` `@State` item, offer
/// `.medium` + `.large` detents, and show the system drag indicator. Future
/// sheets (CreatePost, Comments, AddSpot) should reuse this `.sheet(item:)` +
/// `.presentationDetents` pattern rather than inventing their own.
///
/// V1 is intentionally visual only: every action is a no-op (`action: {}`).
/// There is no vote/save/comment/route/share logic, no sub-sheets, no photo
/// strip, and no "posts about this spot" section.
struct SpotSheetView: View {
    @Environment(Localization.self) private var loc
    let post: UIPost

    /// Drives the nested Comments sheet. Presenting a sheet from within a sheet
    /// is intended here — the comment button opens the comments list.
    @State private var showComments = false

    /// Drives the nested Report ("Melden") sheet, opened from the share/report
    /// row below. Mirrors the comments sheet pattern.
    @State private var showReport = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                photo
                badgeRow
                title
                metaAndVoteRow
                description
                actionRow
                commentButton
                shareAndReportRow
            }
            .padding(.horizontal, 16)
            // Top padding so the content sits below the sheet's drag indicator.
            .padding(.top, 20)
            .padding(.bottom, 24)
        }
        .background(AppColor.background)
        // Nested Comments sheet. Follows the project sheet pattern: the
        // presenter sets detents + drag indicator. `.large` fits a comment list.
        .sheet(isPresented: $showComments) {
            CommentsSheetView(post: post)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        // Nested Report sheet. Same project sheet pattern as comments; opened
        // from the "Melden" button in the share/report row.
        .sheet(isPresented: $showReport) {
            ReportSheetView(post: post)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
    }

    // MARK: 1. Placeholder photo

    private var photo: some View {
        Color(hex: post.colorHex)
            .aspectRatio(4.0 / 3.0, contentMode: .fit)
            .frame(maxWidth: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    // MARK: 2. Badge row

    private var badgeRow: some View {
        HStack(spacing: 8) {
            CategoryPill(label: post.category)
            if post.verified {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 16))
                    .foregroundColor(AppColor.secondary)
            }
            Spacer(minLength: 0)
        }
    }

    // MARK: 3. Title

    private var title: some View {
        HStack(spacing: 6) {
            Text(post.spotName)
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(AppColor.foreground)
                .fixedSize(horizontal: false, vertical: true)
            if post.verified {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 15))
                    .foregroundColor(AppColor.secondary)
            }
        }
    }

    // MARK: 4. Meta + vote row

    private var metaAndVoteRow: some View {
        HStack(spacing: 6) {
            Image(systemName: "mappin")
                .font(.system(size: 15))
                .foregroundColor(AppColor.brand)
            Text(post.neighborhood)
                .font(.system(size: 14))
                .foregroundColor(AppColor.mutedForeground)

            Spacer()

            VoteControlView(score: post.score, voteState: post.voteState)
        }
    }

    // MARK: 5. Description (placeholder)

    // Placeholder: UIPost has no description field yet (V1).
    private var description: some View {
        Text(loc.strings.spotNoDescription)
            .font(.system(size: 14))
            .foregroundColor(AppColor.mutedForeground)
            .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: 6. Action row

    private var actionRow: some View {
        HStack(spacing: 10) {
            SheetActionButton(
                icon: "arrow.triangle.turn.up.right.diamond",
                label: loc.strings.actionRoute,
                style: .bordered,
                action: {}
            )
            SheetActionButton(
                icon: "bookmark",
                label: loc.strings.actionSave,
                style: .bordered,
                action: {}
            )
            SheetActionButton(
                icon: "checkmark",
                label: loc.strings.actionWasHere,
                style: .primary,
                action: {}
            )
            .layoutPriority(1) // primary gets a little extra width
        }
    }

    // MARK: 7. Comment button

    private var commentButton: some View {
        SheetWideButton(
            icon: "bubble.left",
            label: post.commentCount > 0
                ? loc.strings.showAllComments(count: Int32(post.commentCount))
                : loc.strings.writeComment,
            action: { showComments = true }
        )
    }

    // MARK: 8. Share + Report row

    /// Bottom row: a wider "Teilen" control (left) that triggers Apple's native
    /// share sheet via `ShareLink`, and a narrower "Melden" button (right) that
    /// opens the Report sheet. The comment button above remains its own
    /// full-width row.
    private var shareAndReportRow: some View {
        HStack(spacing: 10) {
            reportButton

            shareControl
                .layoutPriority(1) // give share the extra horizontal weight
        }
    }

    /// Native share sheet via `ShareLink` (iOS 16+). The payload is an
    /// intentional STATIC placeholder — a fixed URL plus the spot name — not a
    /// real deep link. The custom label mirrors `SheetWideButton` styling so it
    /// fits visually with the surrounding bordered sheet buttons.
    private var shareControl: some View {
        ShareLink(
            item: URL(string: "https://thirdspace.app")!,
            subject: Text(post.spotName),
            message: Text(loc.strings.shareMessage(spotName: post.spotName))
        ) {
            HStack(spacing: 8) {
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 15, weight: .semibold))
                Text(loc.strings.share)
                    .font(.system(size: 14, weight: .semibold))
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .frame(maxWidth: .infinity, minHeight: Self.shareReportRowHeight)
            .foregroundColor(AppColor.foreground)
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

    /// Shared height for both controls in `shareAndReportRow` so the compact
    /// report button and the wide share control line up at an identical height.
    private static let shareReportRowHeight: CGFloat = 48

    /// Compact icon-only bordered report button opening the Report sheet. It is
    /// a square sized to `shareReportRowHeight`, so it matches the share
    /// control's height exactly. The flag is tinted with `AppColor.destructive`
    /// to signal a report action and centered within the square frame.
    private var reportButton: some View {
        Button {
            showReport = true
        } label: {
            Image(systemName: "flag")
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(AppColor.destructive)
                .frame(width: Self.shareReportRowHeight, height: Self.shareReportRowHeight)
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
        .accessibilityLabel(loc.strings.report)
    }
}

// The reusable sheet building blocks (CategoryPill, SheetActionButton,
// SheetWideButton) previously defined here have been hoisted to
// SheetComponents.swift so the Comments and CreatePost sheets can reuse them.
// SpotSheetView's visible appearance is unchanged — it now references the
// shared (internal) versions with identical styling.
