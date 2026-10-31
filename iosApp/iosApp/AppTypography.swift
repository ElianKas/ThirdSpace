import CoreGraphics

/// Central font-SIZE tokens (SwiftUI-only). Sizes only — the font WEIGHT stays
/// free at each call site, e.g. `.font(.system(size: AppTypography.body, weight: .bold))`.
///
/// Six intentional steps; when migrating, snap existing sizes to the nearest
/// role (11–12→caption, 13→footnote, 14–15→body, 16–18→title, 20→titleLarge,
/// 28→headline).
enum AppTypography {
    static let caption: CGFloat = 12     // meta / small labels (11–12)
    static let footnote: CGFloat = 13    // secondary text (13)
    static let body: CGFloat = 14        // default body (14–15)
    static let title: CGFloat = 17       // card / row titles (16–18)
    static let titleLarge: CGFloat = 20  // sheet / section titles
    static let headline: CGFloat = 28    // large headline (e.g. "Feed")
}
