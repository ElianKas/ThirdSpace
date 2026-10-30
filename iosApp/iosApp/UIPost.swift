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

    /// German check-in label mirroring PostCard.kt ("war hier" / "waren hier").
    var checkInLabel: String {
        checkInCount == 1 ? "war hier" : "waren hier"
    }

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
    let label: String
    let active: Bool

    init(bridged: IosFilterChip) {
        self.label = bridged.label
        self.active = bridged.active
    }
}

enum FeedData {
    /// Pull the bridged posts from the shared framework and map to `UIPost`.
    static func posts() -> [UIPost] {
        FeedBridge.shared.posts().map { UIPost(bridged: $0) }
    }

    /// Pull the bridged map filter chips from the shared framework.
    static func mapFilters() -> [UIFilterChip] {
        FeedBridge.shared.mapFilters().map { UIFilterChip(bridged: $0) }
    }
}
