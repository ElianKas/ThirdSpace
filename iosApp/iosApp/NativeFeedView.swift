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
/// The posts are held in local `@State` so the refresh scaffold can re-set them.
/// There is no real backend: `refresh()` waits briefly, then re-reads the static
/// bridge `FeedData.posts()`.
struct NativeFeedView: View {
    @Environment(Localization.self) private var loc
    @State private var posts: [UIPost] = FeedData.posts()
    @State private var selectedPost: UIPost?
    @State private var showCreate = false

    var body: some View {
        List {
            Section {
                ForEach(Array(posts.enumerated()), id: \.element.id) { index, post in
                    VStack(alignment: .leading, spacing: 0) {
                        PostCardView(post: post, onTap: { selectedPost = post })

                        // 1px hairline between cards (not after the last one),
                        // matching the previous ScrollView/LazyVStack look.
                        if index < posts.count - 1 {
                            Rectangle()
                                .fill(AppColor.hairline)
                                .frame(height: 1)
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
    }

    // MARK: Refresh scaffold

    /// Placeholder refresh hook. Real data loading comes later; for now it waits
    /// a short, artificial delay and re-reads the static bridge so the spinner
    /// has something to show.
    private func refresh() async {
        try? await Task.sleep(nanoseconds: 1_000_000_000) // 1s
        posts = FeedData.posts()
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(loc.strings.feedEyebrow)
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(1.2)
                        .foregroundColor(AppColor.textFaint)
                    Text(loc.strings.feedTitle)
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(AppColor.foreground)
                }

                Spacer()

                HStack(spacing: 20) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 18))
                        .foregroundColor(AppColor.foreground)
                    Button {
                        showCreate = true
                    } label: {
                        Image(systemName: "square.and.pencil")
                            .font(.system(size: 18))
                            .foregroundColor(AppColor.foreground)
                    }
                    .buttonStyle(.plain)
                    Image(systemName: "bell")
                        .font(.system(size: 18))
                        .foregroundColor(AppColor.foreground)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 12)

            Rectangle()
                .fill(AppColor.hairline)
                .frame(height: 1)
        }
        // Opaque background behind the pinned header so rows do not show through
        // as they scroll under it.
        .background(AppColor.background)
    }
}
