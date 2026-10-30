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
///   Sending is OPTIMISTIC and LOCAL ONLY — it appends to local `@State` so the
///   user sees feedback. Nothing is persisted or written back through the
///   bridge. No replies/threading.
struct CommentsSheetView: View {
    let post: UIPost

    /// Comments shown in the list. Seeded from the bridge, then locally appended
    /// to by the input row (optimistic, non-persisted).
    @State private var comments: [UIComment] = []
    /// Draft text in the input field (local state).
    @State private var draft: String = ""
    /// Ensures the bridge seed only happens once.
    @State private var didLoad = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
                .padding(.horizontal, 16)
                .padding(.top, 20)
                .padding(.bottom, 12)

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
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
        }
        .background(AppColor.background)
        .onAppear(perform: loadIfNeeded)
    }

    // MARK: Header

    private var header: some View {
        Text("Kommentare · \(comments.count)")
            .font(.system(size: 20, weight: .bold))
            .foregroundColor(AppColor.foreground)
    }

    // MARK: List

    private var list: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 20) {
                ForEach(comments) { comment in
                    CommentRow(comment: comment)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Empty state

    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "bubble.left")
                .font(.system(size: 28))
                .foregroundColor(AppColor.textFaint)
            Text("Noch keine Kommentare. Sei die erste Person.")
                .font(.system(size: 14))
                .foregroundColor(AppColor.mutedForeground)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 32)
    }

    // MARK: Input row

    private var inputRow: some View {
        HStack(alignment: .bottom, spacing: 10) {
            TextField("Kommentar schreiben…", text: $draft, axis: .vertical)
                .font(.system(size: 14))
                .foregroundColor(AppColor.foreground)
                .lineLimit(1...4)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(AppColor.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .stroke(AppColor.border, lineWidth: 1)
                )

            Button(action: send) {
                Image(systemName: "arrow.up")
                    .font(.system(size: 16, weight: .semibold))
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
    }

    /// Optimistic, local-only append. No persistence, no bridge write.
    private func send() {
        let trimmed = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let new = UIComment(
            id: "local-\(UUID().uuidString)",
            authorName: "Du",
            timeLabel: "gerade eben",
            text: trimmed
        )
        comments.append(new)
        draft = ""
    }
}
