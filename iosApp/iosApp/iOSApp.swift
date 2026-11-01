import SwiftUI
import Shared

@main
struct iOSApp: App {
    /// App-wide localization state, injected into the view environment so every
    /// screen reads the same `IosStrings` snapshot. DE is the default; there is
    /// no language-selection UI yet. To preview EN, change this to
    /// `Localization(code: "en")` temporarily, then revert.
    @State private var localization = Localization(code: "de")

    /// App-wide auth/session state, injected alongside `localization`. Reads
    /// the initial logged-in/out state from the shared `FeedBridge` and drives
    /// the auth gate in `ContentView`. Must be constructed AFTER
    /// `configureSupabase()` runs (see `init`) so its initial reads and session
    /// restore see the configured (or unconfigured) client.
    @State private var session: Session

    /// App-wide current-user profile state, injected alongside `session`.
    /// Seeds from the shared `FeedBridge` (a neutral snapshot pre-fetch), then
    /// refreshes the real `profiles` row from the backend. Constructed AFTER
    /// `configureSupabase()` so its initial read + refresh see the configured
    /// (or unconfigured) client.
    @State private var profileStore: ProfileStore

    /// App-wide social-graph (follows) state, injected alongside `profileStore`.
    /// Backs the Phase F5 follower/following list screens + the other-profile
    /// follow button. Thin `@Observable` wrapper over the shared `FeedBridge`
    /// follow seam; constructed AFTER `configureSupabase()` so its reads see the
    /// configured (or unconfigured) client, mirroring `profileStore`.
    @State private var followStore: FollowStore

    init() {
        Self.configureSupabase()
        _session = State(initialValue: Session())
        _profileStore = State(initialValue: ProfileStore())
        _followStore = State(initialValue: FollowStore())
    }

    /// Read the Supabase project URL + publishable key from Info.plist (seeded
    /// from the gitignored `Supabase.xcconfig`) and hand them to the shared
    /// `FeedBridge`. The shared `SupabaseClientProvider` treats empty/placeholder
    /// values as "not configured" and builds no client, so the app launches
    /// fine without a backend. No network call happens here.
    ///
    /// The URL is stored in xcconfig WITHOUT a scheme (host only) because
    /// xcconfig treats `//` as a comment and would truncate `https://...`; we
    /// re-add the scheme here before passing it across the bridge.
    private static func configureSupabase() {
        let info = Bundle.main.infoDictionary
        let rawURL = (info?["SupabaseURL"] as? String) ?? ""
        let publishableKey = (info?["SupabasePublishableKey"] as? String) ?? ""

        let host = rawURL.trimmingCharacters(in: .whitespacesAndNewlines)
        let url: String
        if host.isEmpty {
            url = ""
        } else if host.hasPrefix("http://") || host.hasPrefix("https://") {
            url = host
        } else {
            url = "https://\(host)"
        }

        FeedBridge.shared.configureSupabase(url: url, publishableKey: publishableKey)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(localization)
                .environment(session)
                .environment(profileStore)
                .environment(followStore)
        }
    }
}
