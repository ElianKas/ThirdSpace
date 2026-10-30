import SwiftUI

/// Native SwiftUI `TabView` — the primary app navigation.
///
/// Three fully native tabs (no Compose):
/// - "Posts":   the native SwiftUI feed (`NativeFeedView`).
/// - "Map":     the native map base (`MapBaseView`) — no real map engine yet.
/// - "Discover": an inert placeholder for a future screen.
struct RootTabView: View {
    var body: some View {
        TabView {
            NativeFeedView()
                .ignoresSafeArea(edges: .bottom)
                .tabItem {
                    Label("Posts", systemImage: "list.bullet")
                }

            MapBaseView()
                .ignoresSafeArea()
                .tabItem {
                    Label("Map", systemImage: "map")
                }

            DiscoverPlaceholderView()
                .tabItem {
                    Label("Discover", systemImage: "sparkles")
                }
        }
        .tint(AppColor.brand)
    }
}

/// Inert placeholder for the future Discover screen. No interactive behavior.
struct DiscoverPlaceholderView: View {
    var body: some View {
        ZStack {
            AppColor.page.ignoresSafeArea()
            VStack(spacing: 8) {
                Image(systemName: "sparkles")
                    .font(.system(size: 28))
                    .foregroundColor(AppColor.textFaint)
                Text("Discover folgt")
                    .font(.system(size: 15))
                    .foregroundColor(AppColor.textFaint)
            }
        }
    }
}
