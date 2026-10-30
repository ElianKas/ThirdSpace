import SwiftUI
import Shared

// MARK: - Design tokens (mirrored from shared AppColors.kt)

/// App color tokens mirrored from `shared/.../ui/theme/AppColors.kt` so the
/// native SwiftUI feed matches the Compose feed. Kept in sync manually; this is
/// a comparison prototype, not a generated token pipeline.
enum AppColor {
    static let background = Color(hex: "#FAF9F7")
    static let page = Color(hex: "#F4F2EE")
    static let surface = Color(hex: "#FFFFFF")
    static let foreground = Color(hex: "#1A1A1A")
    static let brand = Color(hex: "#F26419")
    static let brandDark = Color(hex: "#C94E0E")
    static let secondary = Color(hex: "#7C3AED") // purple (verified badge)
    static let mutedForeground = Color(hex: "#6B6760")
    static let textFaint = Color(hex: "#9E9A94")
    static let border = Color(hex: "#E8E4DE")
    static let hairline = Color(hex: "#F0EDE8")
    static let navInactive = Color(hex: "#9E9A94")
    static let bodyText = Color(hex: "#3D3D3A")
    /// Red used for destructive actions (e.g. the "Report senden" button).
    /// Mirrors the legacy report button color (#DC2626). This enum is the only
    /// place `Color(hex:)` literals legitimately live.
    static let destructive = Color(hex: "#DC2626")

    /// Translucent white used by the frosted floating surfaces on the map
    /// (search bar, control stack). Mirrors AppColors.frost (ARGB 0xE6FFFFFF).
    static let frost = Color.white.opacity(0.85)
    /// Stronger frosted surface. Mirrors AppColors.frostStrong (ARGB 0xEBFFFFFF).
    static let frostStrong = Color.white.opacity(0.92)
}

// MARK: - Color(hex:) support

extension Color {
    /// Create a `Color` from a "#RRGGBB" (or "RRGGBB") hex string.
    /// Falls back to gray for malformed input so the UI never crashes.
    init(hex: String) {
        var cleaned = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleaned.hasPrefix("#") { cleaned.removeFirst() }

        guard cleaned.count == 6, let value = UInt64(cleaned, radix: 16) else {
            self = Color.gray
            return
        }

        let r = Double((value >> 16) & 0xFF) / 255.0
        let g = Double((value >> 8) & 0xFF) / 255.0
        let b = Double(value & 0xFF) / 255.0
        self = Color(red: r, green: g, blue: b)
    }
}

// MARK: - UIPost (Swift mirror of Kotlin IosPost)

/// Swift-side value type consumed by the SwiftUI views. Built from the bridged
/// `IosPost` returned by `FeedBridge.shared.posts()`.
struct UIPost: Identifiable {
    let id: String
    let spotName: String
    let category: String
    let neighborhood: String
    let verified: Bool
    let checkInCount: Int
    let authorName: String
    let dateLabel: String
    let text: String          // empty string means "no text"
    let score: Int
    let voteState: String     // "UP" / "DOWN" / "NONE"
    let commentCount: Int
    let colorHex: String      // "#RRGGBB"

    var hasText: Bool { !text.isEmpty }

    init(bridged: IosPost) {
        self.id = bridged.id
        self.spotName = bridged.spotName
        self.category = bridged.category
        self.neighborhood = bridged.neighborhood
        self.verified = bridged.verified
        self.checkInCount = Int(bridged.checkInCount)
        self.authorName = bridged.authorName
        self.dateLabel = bridged.dateLabel
        self.text = bridged.text
        self.score = Int(bridged.score)
        self.voteState = bridged.voteState
        self.commentCount = Int(bridged.commentCount)
        self.colorHex = bridged.colorHex
    }
}

// MARK: - UIFilterChip (Swift mirror of Kotlin IosFilterChip)

/// Swift-side value type for a map filter chip. Built from the bridged
/// `IosFilterChip` returned by `FeedBridge.shared.mapFilters()`. The shared
/// module is the single source of truth for the chip list (no Swift-only list).
struct UIFilterChip: Identifiable {
    let id = UUID()
    /// Stable machine key (e.g. "chip_cafes"). The shared module owns the
    /// chip order + active flags; the `label` is already localized by the
    /// bridge for the current language.
    let key: String
    let label: String
    let active: Bool

    init(bridged: IosFilterChip) {
        self.key = bridged.key
        self.label = bridged.label
        self.active = bridged.active
    }
}

// MARK: - UIComment (Swift mirror of Kotlin IosComment)

/// Swift-side value type for a comment. Built from the bridged `IosComment`
/// returned by `FeedBridge.shared.comments(postId:)`. The shared module is the
/// single source of truth for comments (no Swift-only list). No replies.
struct UIComment: Identifiable {
    let id: String
    let authorName: String
    let timeLabel: String
    let text: String

    init(bridged: IosComment) {
        self.id = bridged.id
        self.authorName = bridged.authorName
        self.timeLabel = bridged.timeLabel
        self.text = bridged.text
    }

    /// Local-only initializer for optimistic, non-persisted comments typed in
    /// the Comments sheet (never written back through the bridge).
    init(id: String, authorName: String, timeLabel: String, text: String) {
        self.id = id
        self.authorName = authorName
        self.timeLabel = timeLabel
        self.text = text
    }
}

// MARK: - UIReportReason (Swift mirror of Kotlin IosReportReason)

/// Swift-side value type for a report reason. Built from the bridged
/// `IosReportReason` returned by `FeedBridge.shared.reportReasons()`. The shared
/// module is the single source of truth for the reason list (no Swift-only
/// list). `key` is the stable machine id; `label` is the German display string.
struct UIReportReason: Identifiable {
    let key: String
    let label: String
    var id: String { key }

    init(bridged: IosReportReason) {
        self.key = bridged.key
        self.label = bridged.label
    }
}

// MARK: - UIProfile (Swift mirror of Kotlin IosProfile)

/// Swift-side value type for the current user's own profile. Built from the
/// bridged `IosProfile` returned by `FeedBridge.shared.profile()`. The shared
/// module is the single source of truth for the profile (no Swift-only data).
/// There is no avatar field — the avatar initial is derived Swift-side from
/// `name` via `AvatarView`. `monthYear` stays as-is; the localized
/// "seit"/"since" prefix is applied in the view via `loc.strings.joinedSince`.
struct UIProfile {
    let name: String
    let username: String
    let city: String
    let monthYear: String
    let bio: String
    let email: String
    let spotCount: Int
    let postCount: Int
    let followerCount: Int
    let followingCount: Int

    init(bridged: IosProfile) {
        self.name = bridged.name
        self.username = bridged.username
        self.city = bridged.city
        self.monthYear = bridged.monthYear
        self.bio = bridged.bio
        self.email = bridged.email
        self.spotCount = Int(bridged.spotCount)
        self.postCount = Int(bridged.postCount)
        self.followerCount = Int(bridged.followerCount)
        self.followingCount = Int(bridged.followingCount)
    }
}

enum FeedData {
    /// Pull the bridged posts from the shared framework and map to `UIPost`.
    static func posts() -> [UIPost] {
        FeedBridge.shared.posts().map { UIPost(bridged: $0) }
    }

    /// Pull the bridged current-user profile from the shared framework. Single
    /// source of truth for the profile screen.
    static func profile() -> UIProfile {
        UIProfile(bridged: FeedBridge.shared.profile())
    }

    /// Pull the bridged report reasons from the shared framework. Single source
    /// of truth for the report sheet's selectable reasons.
    static func reportReasons() -> [UIReportReason] {
        FeedBridge.shared.reportReasons().map { UIReportReason(bridged: $0) }
    }

    /// Pull the bridged map filter chips from the shared framework.
    static func mapFilters() -> [UIFilterChip] {
        FeedBridge.shared.mapFilters().map { UIFilterChip(bridged: $0) }
    }

    /// Pull the bridged comments for a post from the shared framework. Empty
    /// when the post has no comments (drives the Comments sheet empty state).
    static func comments(postId: String) -> [UIComment] {
        FeedBridge.shared.comments(postId: postId).map { UIComment(bridged: $0) }
    }
}
