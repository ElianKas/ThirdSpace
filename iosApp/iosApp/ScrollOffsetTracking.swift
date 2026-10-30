import SwiftUI

/// Generic, reusable scroll-offset tracking utilities.
///
/// The primary entry point is the `trackScrollOffset(_:)` view modifier, which
/// attaches to a `ScrollView` (or any view that contains one) and reports the
/// current vertical content offset through a binding, plus a convenience delta
/// callback describing how the offset changed between updates.
///
/// Implementation note: this prefers `onScrollGeometryChange(for:of:action:)`,
/// available from iOS 18. The project's deployment target is iOS 18.2, so that
/// API is always available here and no fallback (preference-key / `GeometryReader`
/// coordinate-space hack) is required. Keeping the dependency on the modern API
/// makes the helper small and robust.
///
/// Reusability: this file has no knowledge of the feed, headers, or refresh
/// logic. Any scrolling screen (Map, Discover, future lists) can adopt it.

/// Describes the direction of the most recent scroll movement.
///
/// `offset` here is the vertical content offset reported by SwiftUI's scroll
/// geometry: it is `0` at the very top and grows **positive** as the user
/// scrolls the content upward (i.e. scrolls *down* through the list).
enum ScrollDirection {
    /// Content moved up on screen — the user is scrolling down through content.
    case down
    /// Content moved down on screen — the user is scrolling up toward the top.
    case up
    /// No meaningful vertical change since the last update.
    case idle
}

extension View {
    /// Report the vertical scroll offset of the nearest scroll view.
    ///
    /// - Parameters:
    ///   - offset: A binding that receives the current vertical content offset.
    ///     `0` at the top; increases as the user scrolls down.
    ///   - onChange: Optional callback invoked on every change with the old and
    ///     new offset, plus the derived `ScrollDirection`. Useful for driving an
    ///     auto-hiding header without having to diff the offset yourself.
    ///   - threshold: Minimum absolute delta (in points) before a change is
    ///     treated as `.up`/`.down` rather than `.idle`. Filters out jitter.
    func trackScrollOffset(
        _ offset: Binding<CGFloat>,
        threshold: CGFloat = 1.0,
        onChange: ((_ oldOffset: CGFloat, _ newOffset: CGFloat, _ direction: ScrollDirection) -> Void)? = nil
    ) -> some View {
        // iOS 18+: read the vertical component of the scroll geometry's
        // contentOffset. The project targets 18.2 so this is always available.
        onScrollGeometryChange(for: CGFloat.self) { geometry in
            geometry.contentOffset.y
        } action: { oldValue, newValue in
            offset.wrappedValue = newValue

            guard let onChange else { return }
            let delta = newValue - oldValue
            let direction: ScrollDirection
            if delta > threshold {
                direction = .down          // offset grew → scrolling down
            } else if delta < -threshold {
                direction = .up            // offset shrank → scrolling up
            } else {
                direction = .idle
            }
            onChange(oldValue, newValue, direction)
        }
    }
}
