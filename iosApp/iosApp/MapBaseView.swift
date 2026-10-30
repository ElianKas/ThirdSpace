import SwiftUI

/// Native SwiftUI map BASE — a faithful replication of the former Compose
/// `MapScreen`, with NO real map engine.
///
/// Layout (matching the Compose original):
/// - Page-colored background with a subtle centered "Karte folgt" label.
/// - Top frosted search bar: magnifyingglass + placeholder + round avatar.
/// - Horizontally scrollable filter chips sourced from the shared bridge
///   (`FeedBridge.shared.mapFilters()`), "Meine Pins" active.
/// - Bottom-left vertical control stack (locate + users SF Symbols).
/// - Bottom-right FAB (plus).
///
/// Every control is a no-op (UI only).
struct MapBaseView: View {
    private let filters: [UIFilterChip] = FeedData.mapFilters()

    var body: some View {
        ZStack {
            // Placeholder map area
            AppColor.page.ignoresSafeArea()

            Text("Karte folgt")
                .font(.system(size: 15))
                .foregroundColor(AppColor.textFaint)

            // Top controls: frosted search bar + filter chips
            VStack(spacing: 8) {
                searchBar
                filterChips
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)

            // Bottom-left vertical control stack
            VStack {
                Spacer()
                HStack {
                    controlStack
                    Spacer()
                }
            }
            .padding(.leading, 16)
            .padding(.bottom, 32)

            // Bottom-right FAB
            VStack {
                Spacer()
                HStack {
                    Spacer()
                    fab
                }
            }
            .padding(.trailing, 16)
            .padding(.bottom, 32)
        }
    }

    // MARK: Frosted search bar

    private var searchBar: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 16))
                .foregroundColor(AppColor.mutedForeground)
            Text("Ort oder Adresse suchen")
                .font(.system(size: 15))
                .foregroundColor(AppColor.textFaint)
            Spacer(minLength: 8)
            AvatarView(name: "Du", size: 24)
        }
        .padding(.horizontal, 12)
        .frame(height: 44)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(AppColor.frost)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(AppColor.border, lineWidth: 1)
        )
    }

    // MARK: Filter chips (from shared bridge)

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(filters) { chip in
                    FilterChipView(label: chip.label, active: chip.active)
                }
            }
        }
    }

    // MARK: Bottom-left control stack

    private var controlStack: some View {
        VStack(spacing: 0) {
            mapControl(systemImage: "location")
            Rectangle()
                .fill(AppColor.hairline)
                .frame(width: 44, height: 1)
            mapControl(systemImage: "person.2")
        }
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(AppColor.frost)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(AppColor.border, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func mapControl(systemImage: String) -> some View {
        Button(action: { /* no-op */ }) {
            Image(systemName: systemImage)
                .font(.system(size: 16))
                .foregroundColor(AppColor.foreground)
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
    }

    // MARK: Bottom-right FAB

    private var fab: some View {
        Button(action: { /* no-op */ }) {
            Image(systemName: "plus")
                .font(.system(size: 24, weight: .semibold))
                .foregroundColor(.white)
                .frame(width: 52, height: 52)
                .background(Circle().fill(AppColor.brand))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Filter chip

/// Native replication of the Compose `FilterChip`: a pill with a label. Active
/// chips use the brand fill with white text; inactive chips use a frosted
/// surface with a hairline border.
struct FilterChipView: View {
    let label: String
    let active: Bool

    var body: some View {
        Text(label)
            .font(.system(size: 13, weight: .medium))
            .foregroundColor(active ? .white : AppColor.foreground)
            .padding(.horizontal, 14)
            .frame(height: 30)
            .background(
                Capsule().fill(active ? AppColor.brand : AppColor.frost)
            )
            .overlay(
                Capsule().stroke(active ? Color.clear : AppColor.border, lineWidth: 1)
            )
    }
}
