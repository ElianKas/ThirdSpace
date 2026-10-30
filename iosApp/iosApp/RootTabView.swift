import SwiftUI

/// Native SwiftUI `TabView` — the primary app navigation.
///
/// Three fully native tabs (no Compose):
/// - "Posts":   the native SwiftUI feed (`NativeFeedView`).
/// - "Map":     the native map base (`MapBaseView`) — no real map engine yet.
/// - "Discover": an inert placeholder for a future screen.
struct RootTabView: View {
    @Environment(Localization.self) private var loc

    var body: some View {
        TabView {
            NativeFeedView()
                .ignoresSafeArea(edges: .bottom)
                .tabItem {
                    Label(loc.strings.tabPosts, systemImage: "list.bullet")
                }

            MapBaseView()
                .ignoresSafeArea()
                .tabItem {
                    Label(loc.strings.tabMap, systemImage: "map")
                }

            DiscoverPlaceholderView()
                .tabItem {
                    Label(loc.strings.tabDiscover, systemImage: "sparkles")
                }
        }
        .tint(AppColor.brand)
    }
}

/// Inert placeholder for the future Discover screen. No interactive behavior.
struct DiscoverPlaceholderView: View {
    @Environment(Localization.self) private var loc

    var body: some View {
        ZStack {
            AppColor.page.ignoresSafeArea()
            VStack(spacing: 8) {
                Image(systemName: "sparkles")
                    .font(.system(size: 28))
                    .foregroundColor(AppColor.textFaint)
                Text(loc.strings.discoverPlaceholder)
                    .font(.system(size: 15))
                    .foregroundColor(AppColor.textFaint)
            }
        }
    }
}
