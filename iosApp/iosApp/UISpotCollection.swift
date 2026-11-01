import Shared

// MARK: - UISpotCollection (Swift mirror of Kotlin IosSpotCollection)

/// Swift-side value type for a single saved-spot collection (Phase C4). Built
/// from the bridged `IosSpotCollection` returned by
/// `FeedBridge.shared.collections()`. The shared module is the single source of
/// truth for collections (no Swift-only list). `name` is dynamic content (NOT
/// translated); `spotCount` drives the localized `collectionSpotCount` label;
/// `createdAtLabel` is a shared-formatted relative-time string.
struct UISpotCollection: Identifiable {
    let id: String
    let name: String
    let spotCount: Int
    let createdAtLabel: String

    init(bridged: IosSpotCollection) {
        self.id = bridged.id
        self.name = bridged.name
        self.spotCount = Int(bridged.spotCount)
        self.createdAtLabel = bridged.createdAtLabel
    }
}
