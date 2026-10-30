import SwiftUI

/// Reusable CONTENT scaffold for native sheets.
///
/// `SheetScaffold` standardizes the inside of a sheet: an optional title row
/// (title text + optional trailing close button) and consistent horizontal /
/// top / bottom padding, then the caller's `content`.
///
/// IMPORTANT — presentation stays at the call site. This scaffold does NOT
/// create the sheet and does NOT set detents or the drag indicator. The
/// presenter is responsible for the binding pattern, e.g.:
///
///     .sheet(item: $selectedItem) { item in
///         SomeSheetView(item: item)
///             .presentationDetents([.medium, .large])
///             .presentationDragIndicator(.visible)
///     }
///
/// This keeps the project's binding sheet pattern (`.sheet(item:)` /
/// `.sheet(isPresented:)` + `.presentationDetents` + `.presentationDragIndicator`)
/// owned by the presenter, exactly as `SpotSheetView`'s call site documents.
///
/// Appearance tokens come only from `AppColor` (no hardcoded hex literals).
struct SheetScaffold<Content: View>: View {
    /// Optional title shown at the top. When `nil`, no title row is rendered.
    let title: String?
    /// Optional close handler. When non-nil (and a title is shown), a trailing
    /// "xmark" close button is rendered that calls this closure.
    let onClose: (() -> Void)?
    @ViewBuilder let content: Content

    init(
        title: String? = nil,
        onClose: (() -> Void)? = nil,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.onClose = onClose
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if title != nil || onClose != nil {
                titleRow
                    .padding(.bottom, 16)
            }
            content
        }
        .padding(.horizontal, 16)
        // Top padding so content sits below the sheet's drag indicator.
        .padding(.top, 20)
        .padding(.bottom, 24)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var titleRow: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            if let title {
                Text(title)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(AppColor.foreground)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 0)

            if let onClose {
                Button(action: onClose) {
                    Image(systemName: "xmark")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(AppColor.mutedForeground)
                        .padding(8)
                        .background(Circle().fill(AppColor.surface))
                        .overlay(Circle().stroke(AppColor.border, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
    }
}
