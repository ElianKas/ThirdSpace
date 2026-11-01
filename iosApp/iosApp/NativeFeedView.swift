import SwiftUI

/// The NATIVE SwiftUI feed.
///
/// Built on a plain `List` (`.listStyle(.plain)`):
/// - The feed header (eyebrow "POSTS" + headline "Feed" + search/compose/bell
///   icons + hairline) is a PINNED section header. A plain list keeps the
///   section header pinned to the top while the rows scroll beneath it. The
///   header sits in normal layout flow at rest, so it never overlaps the first
///   post (the previous overlay approach caused that bug).
/// - Native pull-to-refresh via `.refreshable`. The system spinner renders at
///   the top of the list, ABOVE the content, and is NOT hidden behind the
///   header (the previous custom ScrollView spinner rendered under the overlay).
/// - The cards keep their full-bleed look: every row (and the header) uses
///   zero `listRowInsets`, hidden row separators (we draw our own 1px
///   hairlines), and a clear row background over an `AppColor.background` list
///   background.
///
/// Auto-hide-on-scroll is intentionally NOT implemented here; that is a separate
/// later step that will be redesigned on top of this `List` (the reusable
/// `ScrollOffsetTracking` helper is kept for it).
///
/// The posts are held in local `@State`, seeded cache-first from the shared
/// `FeedData.posts()` (never blank offline). On appear and on pull-to-refresh
/// the view calls `FeedData.loadFeed { … }` to kick off a background remote
/// refresh and re-reads `posts` when it completes; a `FeedData.observeFeed`
/// listener (registered once) re-reads `posts` live when a late-arriving remote
/// result updates the shared cache. All bridge completions/listeners are
/// delivered on the main thread, so mutating `@State` in them is safe.
struct NativeFeedView: View {
    @Environment(Localization.self) private var loc
    @State private var posts: [UIPost] = FeedData.posts()
    @State private var selectedPost: UIPost?
    @State private var showCreate = false
    /// Drives the author-profile sheet (Phase F5). The feed is NOT inside a
    /// `NavigationStack` (only the Profile tab is), so an author tap PRESENTS
    /// `OtherProfileView` as a SHEET — wrapped in a `NavigationStack` inside the
    /// sheet so the other-profile page can itself push its follower/following
    /// lists. This mirrors how `SpotSheetView` is presented via `.sheet(item:)`.
    @State private var authorProfile: AuthorProfileTarget?
    /// Guards `observeFeed` registration so the long-lived listener is wired
    /// exactly once per view lifetime (mirrors other once-registered observers).
    @State private var didRegisterObserver = false

    var body: some View {
        List {
            Section {
                ForEach(Array(posts.enumerated()), id: \.element.id) { index, post in
                    VStack(alignment: .leading, spacing: 0) {
                        PostCardView(
                            post: post,
                            onTap: { selectedPost = post },
                            onVote: { direction in
                                // Register the tap through the flat bridge; the
                                // shared layer applies the optimistic
                                // score/my_vote and fires onChanged. Re-read
                                // posts() in the completion so the card updates
                                // promptly (observeFeed also re-reads live).
                                // Phase 4: unreachable for a bare spot (the vote
                                // control is hidden), so a bare spot can't vote.
                                FeedData.vote(postId: post.id, direction: direction) {
                                    posts = FeedData.posts()
                                }
                            },
                            // Phase F5: tapping the author opens their profile as
                            // a sheet. PostCardView suppresses the tap for bare
                            // spots / unknown / self, so this only fires for a
                            // real other author. Declared before `isBareSpot` to
                            // match PostCardView's parameter order.
                            onAuthorTap: {
                                authorProfile = AuthorProfileTarget(userId: post.authorUserId)
                            },
                            // Phase 4: a bare spot (no post) hides vote/comment/
                            // caption; the tap still opens the spot detail sheet.
                            isBareSpot: post.isBareSpot
                        )

                        // 1px hairline between cards (not after the last one),
                        // matching the previous ScrollView/LazyVStack look.
                        if index < posts.count - 1 {
                            Rectangle()
                                .fill(AppColor.hairline)
                                .frame(height: AppDimens.hairline)
                        }
                    }
                    .listRowInsets(EdgeInsets())      // edge-to-edge, full-bleed
                    .listRowSeparator(.hidden)        // we draw our own hairlines
                    .listRowBackground(Color.clear)
                }
            } header: {
                header
                    .listRowInsets(EdgeInsets())
                    .listRowSeparator(.hidden)
            }
        }
        .listStyle(.plain)                            // plain → pinned section header
        .environment(\.defaultMinListRowHeight, 0)
        .scrollContentBackground(.hidden)             // hide default list background
        .background(AppColor.background)
        .refreshable { await refresh() }              // native system pull-to-refresh
        .onAppear(perform: onAppear)                  // first-load + observe registration
        // Spot detail sheet. Driven by an Identifiable @State item; this is the
        // reusable sheet pattern future surfaces (CreatePost, Comments, AddSpot)
        // should follow. See SpotSheetView's doc comment.
        .sheet(item: $selectedPost) { post in
            SpotSheetView(post: post)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        // CreatePost sheet, triggered from the header compose button. Same
        // sheet pattern: presenter sets detents + drag indicator.
        .sheet(isPresented: $showCreate) {
            CreatePostView()
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        // Author profile sheet (Phase F5). The feed is not in a NavigationStack,
        // so we PRESENT the other-profile page as a sheet and wrap it in its own
        // `NavigationStack` so its follower/following pushes work. Same
        // `.sheet(item:)` + detents + drag-indicator pattern as the spot sheet.
        .sheet(item: $authorProfile) { target in
            NavigationStack {
                OtherProfileView(userId: target.userId)
            }
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
    }

    // MARK: Load / refresh

    /// First appearance: kick off a remote refresh (cache shows instantly, the
    /// remote result updates `posts` when it lands) and register the live
    /// change observer exactly once.
    private func onAppear() {
        FeedData.loadFeed { posts = FeedData.posts() }

        guard !didRegisterObserver else { return }
        didRegisterObserver = true
        // Re-read from the shared cache whenever a background refresh/flush
        // changes it. The bridge delivers on the main thread, so the `@State`
        // mutation is safe; we touch only the value path, never `self`.
        FeedData.observeFeed { posts = FeedData.posts() }
    }

    /// Native pull-to-refresh: trigger a real background remote refresh via the
    /// flat bridge and re-read `posts` once the refresh kick-off completes.
    /// Bridges the escaping completion into the async `refreshable` with a
    /// continuation so the spinner stays until the completion fires on main.
    private func refresh() async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            FeedData.loadFeed {
                posts = FeedData.posts()
                continuation.resume()
            }
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: AppDimens.spacingXS) {
                    Text(loc.strings.feedEyebrow)
                        .font(.system(size: AppTypography.caption, weight: .semibold))
                        .tracking(1.2)
                        .foregroundColor(AppColor.textFaint)
                    Text(loc.strings.feedTitle)
                        .font(.system(size: AppTypography.headline, weight: .bold))
                        .foregroundColor(AppColor.foreground)
                }

                Spacer()

                HStack(spacing: AppDimens.spacingXL) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: AppTypography.title))
                        .foregroundColor(AppColor.foreground)
                    Button {
                        showCreate = true
                    } label: {
                        Image(systemName: "square.and.pencil")
                            .font(.system(size: AppTypography.title))
                            .foregroundColor(AppColor.foreground)
                    }
                    .buttonStyle(.plain)
                    Image(systemName: "bell")
                        .font(.system(size: AppTypography.title))
                        .foregroundColor(AppColor.foreground)
                }
            }
            .padding(.horizontal, AppDimens.spacingXL)
            .padding(.top, AppDimens.spacingMD)
            .padding(.bottom, AppDimens.spacingMD)

            Rectangle()
                .fill(AppColor.hairline)
                .frame(height: AppDimens.hairline)
        }
        // Opaque background behind the pinned header so rows do not show through
        // as they scroll under it.
        .background(AppColor.background)
    }
}

/// Identifiable wrapper so a tapped author's user id can drive `.sheet(item:)`
/// (SwiftUI needs an `Identifiable` item, not a bare `String?`). The user id is
/// the identity. Used by `NativeFeedView` and `CommentsSheetView`.
struct AuthorProfileTarget: Identifiable {
    let userId: String
    var id: String { userId }
}
