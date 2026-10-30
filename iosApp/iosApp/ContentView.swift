import SwiftUI

/// App root. The UI is now fully native SwiftUI; the Compose-Multiplatform
/// entry points have been removed from the shared module.
struct ContentView: View {
    var body: some View {
        RootTabView()
    }
}
