import SwiftUI
import Shared

/// App-wide localization state for the native SwiftUI UI.
///
/// Thin `@Observable` wrapper around the shared `FeedBridge` language state. It
/// holds the current language code and a flat `IosStrings` snapshot resolved
/// from the shared i18n layer. Views read it from the environment:
///
///     @Environment(Localization.self) private var loc
///     Text(loc.strings.feedTitle)
///     Text(loc.strings.checkInLabel(count: count))
///
/// There is NO language-selection UI yet. Switching works technically via
/// `setLanguage(_:)` + the backing `FeedBridge.shared.setLanguage`, but nothing
/// in the UI calls it. DE is the default. (To preview EN without a menu,
/// temporarily initialize `Localization(code: "en")` in `iOSApp.swift`.)
///
/// `@Observable` + `@Environment(Type.self)` is the iOS 17+ Observation pattern
/// and is valid at the project's iOS 18.2 target.
@Observable
final class Localization {
    private(set) var strings: IosStrings
    private(set) var languageCode: String

    init(code: String = "de") {
        FeedBridge.shared.setLanguage(code: code)
        languageCode = FeedBridge.shared.currentLanguageCode()
        strings = FeedBridge.shared.currentStrings()
    }

    /// Switch the UI language ("de" / "en"). Unknown codes fall back to DE in
    /// the shared layer. Re-reads the snapshot so observing views update.
    func setLanguage(_ code: String) {
        FeedBridge.shared.setLanguage(code: code)
        languageCode = FeedBridge.shared.currentLanguageCode()
        strings = FeedBridge.shared.currentStrings()
    }
}
