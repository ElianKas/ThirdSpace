import SwiftUI

/// Native SwiftUI Comments sheet.
///
/// Presented from `SpotSheetView`'s comment button as a nested sheet (the
/// presenter sets `.presentationDetents([.large])` + `.presentationDragIndicator(.visible)`).
///
/// Layout, top to bottom:
/// - Header "Kommentare · N" where N is the count of LOADED comments (see the
///   documented trade-off in `SampleData.comments`: this is NOT the feed's
///   stored `commentCount`).
/// - Scrollable list of `CommentRow` from `FeedData.comments(postId:)`, or an
///   empty state when there are none.
/// - A bottom input row: an app-styled text field + a round primary send button.
///   Sending PERSISTS through the shared bridge: `FeedData.addComment(...)` does
///   an optimistic write-through + offline queue shared-side, and the view
///   re-reads `FeedData.comments(postId:)` in the completion so the new comment
///   shows immediately (and persists / queues when offline). No replies/threading.
struct CommentsSheetView: View {
    @Environment(Localization.self) private var loc
    let post: UIPost

    /// Comments shown in the list. Seeded from the bridge, then re-read from the
    /// bridge after each persisted send (shared layer does the optimistic
    /// write-through + offline queue).
    @State private var comments: [UIComment] = []
    /// Draft text in the input field (local state).
    @State private var draft: String = ""
    /// Ensures the bridge seed only happens once.
    @State private var didLoad = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
                .padding(.horizontal, AppDimens.spacingLG)
                .padding(.top, AppDimens.spacingXL)
                .padding(.bottom, AppDimens.spacingMD)

            Divider()
                .overlay(AppColor.hairline)

            if comments.isEmpty {
                emptyState
            } else {
                list
            }

            Divider()
                .overlay(AppColor.hairline)

            inputRow
                .padding(.horizontal, AppDimens.spacingLG)
                .padding(.vertical, AppDimens.spacingMD)
        }
        .background(AppColor.background)
        .onAppear(perform: loadIfNeeded)
    }

    // MARK: Header

    private var header: some View {
        Text(loc.strings.commentsHeader(count: Int32(comments.count)))
            .font(.system(size: AppTypography.titleLarge, weight: .bold))
            .foregroundColor(AppColor.foreground)
    }

    // MARK: List

    private var list: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: AppDimens.spacingXL) {
                ForEach(comments) { comment in
                    CommentRow(comment: comment)
                }
            }
            .padding(.horizontal, AppDimens.spacingLG)
            .padding(.vertical, AppDimens.spacingLG)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Empty state

    private var emptyState: some View {
        VStack(spacing: AppDimens.spacingMD) {
            Image(systemName: "bubble.left")
                .font(.system(size: AppTypography.headline))
                .foregroundColor(AppColor.textFaint)
            Text(loc.strings.commentsEmpty)
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.mutedForeground)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, AppDimens.spacingXXL)
    }

    // MARK: Input row

    private var inputRow: some View {
        HStack(alignment: .bottom, spacing: AppDimens.spacingMD) {
            TextField(loc.strings.commentPlaceholder, text: $draft, axis: .vertical)
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.foreground)
                .lineLimit(1...4)
                .padding(.horizontal, AppDimens.spacingLG)
                .padding(.vertical, AppDimens.spacingMD)
                .background(
                    RoundedRectangle(cornerRadius: AppDimens.radiusLG, style: .continuous)
                        .fill(AppColor.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: AppDimens.radiusLG, style: .continuous)
                        .stroke(AppColor.border, lineWidth: AppDimens.hairline)
                )

            Button(action: send) {
                Image(systemName: "arrow.up")
                    .font(.system(size: AppTypography.title, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
                    .background(
                        Circle().fill(canSend ? AppColor.brand : AppColor.textFaint)
                    )
            }
            .buttonStyle(.plain)
            .disabled(!canSend)
        }
    }

    // MARK: State / actions

    private var canSend: Bool {
        !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func loadIfNeeded() {
        guard !didLoad else { return }
        comments = FeedData.comments(postId: post.id)
        didLoad = true
        // Reads are now pure cache reads shared-side (no refresh-on-read, to
        // kill the per-second GET loop), so explicitly pull this post's
        // comments from the backend once and re-read when it completes.
        FeedData.loadComments(postId: post.id) {
            comments = FeedData.comments(postId: post.id)
        }
    }

    /// Persist via the shared bridge. The shared layer already does the
    /// optimistic write-through + offline queue, so we clear the draft and
    /// re-read the bridge in the completion (on the main thread) to surface the
    /// new comment immediately. No Swift-only local append (avoids de-dup).
    private func send() {
        let trimmed = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        draft = ""
        FeedData.addComment(postId: post.id, text: trimmed) {
            comments = FeedData.comments(postId: post.id)
        }
    }
}
