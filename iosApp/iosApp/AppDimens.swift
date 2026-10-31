import CoreGraphics

/// Central spacing, radius and hairline tokens (SwiftUI-only; a native-UI
/// concern, analogous to `AppColor` and `AppLayout`). Not in the shared module.
///
/// Keep the scale small and intentional. When migrating views, snap existing
/// hardcoded values to the NEAREST token (e.g. 10→8 or →12, 15→14, 24→20 or →32).
enum AppDimens {
    // Spacing scale (padding / gaps), 6 steps.
    static let spacingXS: CGFloat = 4
    static let spacingSM: CGFloat = 8
    static let spacingMD: CGFloat = 12
    static let spacingLG: CGFloat = 16
    static let spacingXL: CGFloat = 20
    static let spacingXXL: CGFloat = 32

    // Corner radius scale, 3 steps (12 = default; 20 = pill / large).
    static let radiusSM: CGFloat = 8
    static let radiusMD: CGFloat = 12
    static let radiusLG: CGFloat = 20

    /// Hairline / border / divider thickness.
    static let hairline: CGFloat = 1
}
