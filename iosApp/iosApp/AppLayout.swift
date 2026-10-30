import SwiftUI

/// Central iOS layout tokens (SwiftUI-only; a native-UI concern, NOT in the
/// shared module). Analogous to `AppColor`.
enum AppLayout {
    /// Extra spacing BELOW the system top safe-area inset for interactive top
    /// chrome on borderless pages. Defined once so every borderless page uses
    /// the same gap. (8 preserves the Profile button's prior "+ 8" look.)
    static let topChromeSpacing: CGFloat = 8
}

extension View {
    /// Places interactive top chrome reliably BELOW the status bar / Dynamic
    /// Island, even when the base surface ignores the top safe area. Uses
    /// `safeAreaInset(edge:.top)` so the system reserves the real inset — it
    /// does NOT read `safeAreaInsets` manually (which returns 0 inside an
    /// ignored subtree). Adds `AppLayout.topChromeSpacing` below the inset.
    func topChrome<Chrome: View>(
        alignment: HorizontalAlignment = .leading,
        @ViewBuilder _ chrome: () -> Chrome
    ) -> some View {
        safeAreaInset(edge: .top, alignment: alignment, spacing: 0) {
            chrome()
                .padding(.top, AppLayout.topChromeSpacing)
        }
    }
}
