import SwiftUI

@main
struct iOSApp: App {
    /// App-wide localization state, injected into the view environment so every
    /// screen reads the same `IosStrings` snapshot. DE is the default; there is
    /// no language-selection UI yet. To preview EN, change this to
    /// `Localization(code: "en")` temporarily, then revert.
    @State private var localization = Localization(code: "de")

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(localization)
        }
    }
}
