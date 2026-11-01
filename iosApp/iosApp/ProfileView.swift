import SwiftUI

/// The NATIVE SwiftUI profile screen (STEP 1: static, read-only).
///
/// Shows the current user's own dummy profile sourced entirely from the shared
/// module via `FeedData.profile()` (no Swift-only data). All static UI text
/// goes through the injected `Localization` (`loc.strings.*`); only dynamic
/// content (name/username/city/bio/monthYear and the spot/post content) is
/// interpolated verbatim.
///
/// A live language picker (globe button) sits at the top-right of the banner;
/// tapping it opens a native `Menu` listing the available languages and
/// switches the entire UI language live (in-memory, not persisted).
///
/// Deliberately excluded in this step (per the approved plan): profile editing,
/// follow / DM, opening follower/following lists (counts are display-only),
/// count-up animations, skeletons, and real avatar/banner images.
///
/// Lives as its own bottom-navigation tab (`RootTabView`). It is no longer a
/// sheet, so there is no close/gear chrome and nothing to dismiss; the banner
/// is a plain decorative header that extends to the top like the other tabs.
struct ProfileView: View {
    @Environment(Localization.self) private var loc

    /// Three local tabs for the profile content. No shared segmented control
    /// exists; this stays an inline, non-abstracted enum on purpose.
    private enum ProfileTab { case spots, posts, collections }

    @State private var selectedTab: ProfileTab = .posts

    /// Inline new-collection name field for the collections tab's create
    /// affordance (mirrors `CollectionPickerSheetView.newName`).
    @State private var newCollectionName = ""

    /// Guards the create button against re-entrancy while a create is in flight
    /// (mirrors `CollectionPickerSheetView.creating`).
    @State private var creatingCollection = false

    /// App-wide collections store (single owner of the user's collection list),
    /// read from the environment and consumed by the collections tab.
    @Environment(CollectionsStore.self) private var collectionsStore

    /// The current user's profile, from the shared offline-first store (the
    /// real `profiles` row when logged in; a neutral non-dummy fallback
    /// otherwise). Replaces the old `FeedData.profile()` SampleData snapshot.
    @Environment(ProfileStore.self) private var profileStore
    private var profile: UIProfile { profileStore.profile }

    /// The current user's OWN posts / spots (Phase 4), from the shared
    /// offline-first store (posts where `author_id == uid`; spots where
    /// `owner_id == uid`). Replaces the old global-feed `FeedData.posts()` +
    /// distinct-by-name derivation. Refreshed by the store's
    /// `loadProfile`/`observeProfile` flow, so the tabs update with the profile.
    private var posts: [UIPost] { profileStore.ownPosts }
    private var spots: [UISpot] { profileStore.ownSpots }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                banner

                // Avatar overlaps the banner bottom edge.
                avatar
                    .padding(.leading, 20)
                    .offset(y: -35)
                    .padding(.bottom, -35)

                identity
                    .padding(.horizontal, AppDimens.spacingXL)
                    .padding(.top, AppDimens.spacingMD)

                statsRow
                    .padding(.horizontal, AppDimens.spacingXL)
                    .padding(.top, AppDimens.spacingLG)

                editButton
                    .padding(.horizontal, AppDimens.spacingXL)
                    .padding(.top, AppDimens.spacingLG)

                tabBar
                    .padding(.top, AppDimens.spacingXL)

                tabContent
                    .padding(.top, AppDimens.spacingSM)
            }
        }
        .background(AppColor.background)
        // Native system pull-to-refresh, mirroring NativeFeedView. `.refreshable`
        // attaches to the root ScrollView; the store's @Observable drives the
        // re-render once the shared refresh lands, so no local @State copy is
        // needed here.
        .refreshable {
            await refreshProfile()
            // Keep the collections tab fresh on pull-to-refresh too. Idempotent:
            // the store seeds from the pure cache and coalesces the refresh.
            collectionsStore.loadCollections()
        }
        // Load the collection list when the collections tab becomes visible (and
        // once on first appearance if it's already selected). Idempotent — the
        // store coalesces the refresh — so this never fights the profile refresh.
        .onChange(of: selectedTab) { _, newTab in
            if newTab == .collections {
                collectionsStore.loadCollections()
            }
        }
        .onAppear {
            if selectedTab == .collections {
                collectionsStore.loadCollections()
            }
        }
        // As a tab the banner is a header: let the gradient extend under the
        // status bar at the top, consistent with the other tab screens' chrome.
        .ignoresSafeArea(edges: .top)
        // Language pill overlaid at the top-trailing edge. The overlay is
        // attached OUTSIDE the `.ignoresSafeArea(edges: .top)` ScrollView, so it
        // lives in the safe-area-respecting space: its top aligns with the
        // status-bar bottom, and `AppLayout.topChromeSpacing` adds the same gap
        // the old "+ 8" produced — WITHOUT reading `safeAreaInsets` manually and
        // WITHOUT reserving a strip that would push the borderless banner down.
        .overlay(alignment: .topTrailing) {
            languageButton
                .padding(.trailing, AppDimens.spacingLG)
                .padding(.top, AppLayout.topChromeSpacing)
        }
        // This screen is inside the Profile tab's NavigationStack (so the edit
        // button can push). Hide the nav bar HERE so the gradient banner still
        // reaches the top on the main profile page; the pushed edit page shows
        // its own nav bar + back button.
        .toolbar(.hidden, for: .navigationBar)
    }

    // MARK: Refresh

    /// Native pull-to-refresh: trigger a real background remote refresh of the
    /// profile + counts + own posts/spots via the flat bridge. Mirrors
    /// `NativeFeedView.refresh()`: bridges the escaping `FeedData.loadProfile`
    /// completion into the async `refreshable` with a continuation so the system
    /// spinner stays until the completion fires on the main thread. No local
    /// state is touched — `ProfileStore` re-reads itself in the completion /
    /// `observeProfile`, and its `@Observable` drives the re-render. Offline /
    /// unauthenticated no-ops in the shared layer and completes without crashing.
    private func refreshProfile() async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            FeedData.loadProfile {
                continuation.resume()
            }
        }
    }

    // MARK: Banner

    /// ~140pt rounded-bottom gradient placeholder built ONLY from AppColor
    /// tokens (no raw hex literals in the view). Approximates the legacy banner
    /// gradient; exact legacy hues are intentionally not reproduced here.
    ///
    /// A plain decorative header with NO icon buttons. As a tab (not a sheet)
    /// there is nothing to close and no gear placeholder; the gradient extends
    /// to the very top of the screen like a header.
    private var banner: some View {
        LinearGradient(
            colors: [
                AppColor.brand.opacity(0.85),
                AppColor.secondary.opacity(0.65),
                AppColor.page,
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .frame(height: 140)
        .clipShape(
            .rect(
                bottomLeadingRadius: AppDimens.radiusLG,
                bottomTrailingRadius: AppDimens.radiusLG
            )
        )
    }

    // MARK: Language picker (globe button + native Menu)

    /// A bordered pill showing the active language code + a globe glyph. Tapping
    /// opens a native `Menu` listing every available language by its endonym; a
    /// checkmark marks the active one. Selecting an entry switches the entire UI
    /// language live via `loc.setLanguage`. The pill reads `loc.languageCode`
    /// (and the a11y label `loc.strings.languageMenu`) so it updates live.
    ///
    /// Uses only `AppColor` tokens: a frosted strong fill + hairline border so
    /// it reads on the gradient, with foreground text in `AppColor.foreground`.
    private var languageButton: some View {
        Menu {
            ForEach(loc.availableLanguages) { lang in
                Button {
                    loc.setLanguage(lang.code)
                } label: {
                    if lang.code == loc.languageCode {
                        Label(lang.displayName, systemImage: "checkmark")
                    } else {
                        Text(lang.displayName)
                    }
                }
            }
        } label: {
            HStack(spacing: AppDimens.spacingSM) {
                Text(loc.languageCode.uppercased())
                    .font(.system(size: AppTypography.footnote, weight: .semibold))
                Image(systemName: "globe")
                    .font(.system(size: AppTypography.body, weight: .semibold))
            }
            .foregroundColor(AppColor.foreground)
            .padding(.horizontal, AppDimens.spacingMD)
            .frame(height: 34)
            .background(
                Capsule().fill(AppColor.frostStrong)
            )
            .overlay(
                Capsule().stroke(AppColor.border, lineWidth: AppDimens.hairline)
            )
        }
        .accessibilityLabel(loc.strings.languageMenu)
    }

    // MARK: Avatar

    private var avatar: some View {
        AvatarView(name: profile.name, size: 88)
            .overlay(
                Circle()
                    .stroke(AppColor.background, lineWidth: 3)
            )
    }

    // MARK: Identity (name / @username / location+joined / bio)

    private var identity: some View {
        VStack(alignment: .leading, spacing: AppDimens.spacingSM) {
            Text(profile.name)
                .font(.system(size: AppTypography.titleLarge, weight: .bold))
                .foregroundColor(AppColor.foreground)

            Text("@\(profile.username)")
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.mutedForeground)

            HStack(spacing: AppDimens.spacingXS) {
                Image(systemName: "mappin")
                    .font(.system(size: AppTypography.footnote))
                    .foregroundColor(AppColor.brand)
                // City is dynamic content; "seit"/"since" comes from i18n.
                Text("\(profile.city) · \(loc.strings.joinedSince(monthYear: profile.monthYear))")
                    .font(.system(size: AppTypography.footnote))
                    .foregroundColor(AppColor.mutedForeground)
            }
            .padding(.top, AppDimens.spacingXS)

            Text(profile.bio)
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.bodyText)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, AppDimens.spacingXS)
        }
    }

    // MARK: Stats row

    private var statsRow: some View {
        HStack(spacing: 0) {
            // Spots / Posts stay non-tappable (no list to open for them).
            StatColumn(value: profile.spotCount, label: loc.strings.profileStatSpots)
            StatColumn(value: profile.postCount, label: loc.strings.profileStatPosts)
            // Follower / Following push the CURRENT user's list (we're already
            // in the Profile tab's NavigationStack). The current user's id comes
            // from the flat `FeedData.currentUserId()` bridge getter.
            NavigationLink {
                FollowListView(userId: FeedData.currentUserId(), mode: .followers)
            } label: {
                StatColumn(value: profile.followerCount, label: loc.strings.profileStatFollowers)
            }
            .buttonStyle(.plain)
            NavigationLink {
                FollowListView(userId: FeedData.currentUserId(), mode: .following)
            } label: {
                StatColumn(value: profile.followingCount, label: loc.strings.profileStatFollowing)
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: Edit button (pushes the edit sub-page)

    /// Pushes `ProfileEditView` onto the Profile tab's `NavigationStack`. The
    /// soft-fill bordered look is unchanged from before; `.buttonStyle(.plain)`
    /// keeps it from rendering as a default tappable link.
    private var editButton: some View {
        // A NavigationLink (so it PUSHES the edit sub-page) wearing the shared
        // `.soft` button look via `AppButtonLabel` — NOT a nested `Button`, so
        // the push still works. Styling is now unified with the follow /
        // create-collection buttons.
        NavigationLink {
            ProfileEditView()
        } label: {
            AppButtonLabel(label: loc.strings.profileEdit, style: .soft)
        }
        .buttonStyle(.plain)
    }

    // MARK: Inline two-tab segmented control

    private var tabBar: some View {
        HStack(spacing: 0) {
            tabButton(.spots, title: loc.strings.profileTabSpots)
            tabButton(.posts, title: loc.strings.profileTabPosts)
            tabButton(.collections, title: loc.strings.collectionsTabTitle)
        }
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(AppColor.hairline)
                .frame(height: AppDimens.hairline)
        }
    }

    private func tabButton(_ tab: ProfileTab, title: String) -> some View {
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
            // Fill the full tab width AND pad vertically so the whole tab area
            // (text + indicator + surrounding space) is a comfortable tap
            // target, then make that entire rectangle hit-testable.
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
        case .posts:
            postsContent
        case .spots:
            spotsContent
        case .collections:
            collectionsContent
        }
    }

    @ViewBuilder
    private var postsContent: some View {
        if posts.isEmpty {
            emptyState("doc.text", loc.strings.profileEmptyPosts)
        } else {
            LazyVStack(spacing: 0) {
                ForEach(Array(posts.enumerated()), id: \.element.id) { index, post in
                    VStack(alignment: .leading, spacing: 0) {
                        PostCardView(post: post)
                        if index < posts.count - 1 {
                            Rectangle()
                                .fill(AppColor.hairline)
                                .frame(height: AppDimens.hairline)
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var spotsContent: some View {
        // The user's OWN spots (Phase 4), mapped to the lightweight `ProfileSpot`
        // row model and rendered via the shared `AppListRow`. No VoteControl in V1.
        let rows = spots.map(ProfileSpot.init(spot:))
        if rows.isEmpty {
            emptyState("mappin", loc.strings.profileEmptySpots)
        } else {
            LazyVStack(spacing: 0) {
                ForEach(rows) { spot in
                    AppListRow(
                        leading: .swatch(colorHex: spot.colorHex),
                        title: spot.name,
                        subtitle: "\(spot.category) · \(spot.neighborhood)"
                    )
                    Rectangle()
                        .fill(AppColor.hairline)
                        .frame(height: AppDimens.hairline)
                        .padding(.leading, AppDimens.spacingXL)
                }
            }
        }
    }

    // MARK: Collections tab

    /// The current user's collections (Phase C6). An inline create affordance
    /// sits at the TOP (text field + button, mirroring
    /// `CollectionPickerSheetView.createRow`), followed by the list of
    /// collections. Each row is a `NavigationLink` that PUSHES
    /// `CollectionDetailView` inside the Profile tab's `NavigationStack`. The
    /// list reads the observable `collectionsStore.collections`, so it
    /// re-renders when a create / background refresh lands. An empty list shows
    /// `emptyCollections`. Collection names are dynamic content (verbatim); all
    /// other text comes from `loc.strings.*`.
    @ViewBuilder
    private var collectionsContent: some View {
        VStack(spacing: AppDimens.spacingSM) {
            createCollectionRow
                .padding(.horizontal, AppDimens.spacingXL)
                .padding(.top, AppDimens.spacingSM)

            if collectionsStore.collections.isEmpty {
                emptyState("folder", loc.strings.emptyCollections)
            } else {
                LazyVStack(spacing: 0) {
                    ForEach(collectionsStore.collections) { collection in
                        NavigationLink {
                            CollectionDetailView(
                                collectionId: collection.id,
                                collectionName: collection.name
                            )
                        } label: {
                            AppListRow(
                                leading: .none,
                                title: collection.name,
                                subtitle: loc.strings.collectionSpotCount(count: Int32(collection.spotCount)),
                                trailing: .chevron
                            )
                        }
                        .buttonStyle(.plain)

                        Rectangle()
                            .fill(AppColor.hairline)
                            .frame(height: AppDimens.hairline)
                            .padding(.leading, AppDimens.spacingXL)
                    }
                }
            }
        }
    }

    /// Inline "new collection" affordance: a text field
    /// (`collectionNamePlaceholder`) + a `createCollection` button guarded by
    /// `creatingCollection`. Mirrors `CollectionPickerSheetView.createRow`
    /// (tokens only); on success the field is cleared.
    private var createCollectionRow: some View {
        HStack(spacing: AppDimens.spacingSM) {
            AppTextField(placeholder: loc.strings.collectionNamePlaceholder, text: $newCollectionName)
                .textInputAutocapitalization(.words)
                .autocorrectionDisabled()

            // Shared primary button (hugging, not full-width) — the old
            // fixed `height: 44` + fill-swap-to-`mutedForeground` drift is gone;
            // it now matches the field height via the standard vertical padding
            // and dims via the ONE disabled convention when `!canCreateCollection`.
            AppButton(
                label: loc.strings.createCollection,
                style: .primary,
                fullWidth: false,
                isEnabled: canCreateCollection,
                action: createCollection
            )
        }
    }

    /// Whether the create button is enabled: a non-blank name and no in-flight
    /// create.
    private var canCreateCollection: Bool {
        !creatingCollection
            && !newCollectionName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func createCollection() {
        let name = newCollectionName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, !creatingCollection else { return }
        creatingCollection = true
        collectionsStore.create(name: name) { _ in
            // The list already updated optimistically via observeCollections /
            // the store's re-read. Clear the field regardless of the remote
            // outcome (the optimistic row is kept).
            newCollectionName = ""
            creatingCollection = false
        }
    }

    /// A thin wrapper over the shared `EmptyStateView` for the three profile
    /// tabs (posts/spots/collections). Each call site passes its own coherent SF
    /// Symbol so all empty states match the icon+muted-text look used elsewhere
    /// (previously these were text-only, the fourth inconsistent variant).
    private func emptyState(_ systemImage: String, _ text: String) -> some View {
        EmptyStateView(systemImage: systemImage, text: text)
    }
}

// MARK: - Private subviews

/// A single stat column (big number + muted caption) in the profile stats row.
private struct StatColumn: View {
    let value: Int
    let label: String

    var body: some View {
        VStack(spacing: AppDimens.spacingXS) {
            // Plain number, no count-up animation (deliberately excluded).
            Text("\(value)")
                .font(.system(size: AppTypography.title, weight: .bold))
                .foregroundColor(AppColor.foreground)
            Text(label)
                .font(.system(size: AppTypography.caption))
                .foregroundColor(AppColor.mutedForeground)
        }
        .frame(maxWidth: .infinity)
    }
}

/// A lightweight spot descriptor for the profile Spots tab. As of Phase 4 it is
/// built from the user's OWN `UISpot` (identity by the real spot `id`, so two
/// spots with the same name stay distinct) rather than derived from feed posts.
private struct ProfileSpot: Identifiable {
    let id: String
    let name: String
    let category: String
    let neighborhood: String
    let colorHex: String

    init(spot: UISpot) {
        self.id = spot.id
        self.name = spot.name
        self.category = spot.category
        self.neighborhood = spot.neighborhood
        self.colorHex = spot.colorHex
    }
}


