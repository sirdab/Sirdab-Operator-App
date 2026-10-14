import ComposeApp
import Foundation
import Mixpanel

/**
 * `Analytics` through mixpanel-swift, which keeps events on disk and sends them in batches by
 * itself. The shared Kotlin code decides what to send; this only hands it over.
 *
 * Mixpanel's own automatic events stay off: "App Opened" and "Screen Viewed" come from the shared
 * code, the same on both platforms.
 */
final class MixpanelAnalytics: NSObject, Analytics {
    private let mixpanel: MixpanelInstance

    init(token: String) {
        mixpanel = Mixpanel.initialize(token: token, trackAutomaticEvents: false)
        super.init()
        registerBuild()
    }

    func track(event: String, properties: [String: Any]) {
        mixpanel.track(event: event, properties: mixpanelProperties(properties))
    }

    func identify(userId: String) {
        mixpanel.identify(distinctId: userId)
    }

    func reset() {
        mixpanel.reset()
        // A reset clears every super property, this one too.
        registerBuild()
    }

    func register(properties: [String: Any]) {
        mixpanel.registerSuperProperties(mixpanelProperties(properties))
    }

    func unregister(property: String) {
        mixpanel.unregisterSuperProperty(property)
    }

    func setProfile(properties: [String: Any]) {
        mixpanel.people.set(properties: mixpanelProperties(properties))
    }

    /// Debug builds send too, so a developer's taps can be told apart from a driver's.
    private func registerBuild() {
        #if DEBUG
        mixpanel.registerSuperProperties(["build_type": "debug"])
        #else
        mixpanel.registerSuperProperties(["build_type": "release"])
        #endif
    }
}

/// Kotlin's boxed values arrive as NSNumber subclasses. A Boolean has to go out as a Bool, or
/// Mixpanel records it as 0 or 1.
private func mixpanelProperties(_ properties: [String: Any]) -> Properties {
    properties.mapValues { value -> MixpanelType in
        switch value {
        case let flag as KotlinBoolean: return flag.boolValue
        case let number as NSNumber: return number
        case let text as String: return text
        default: return String(describing: value)
        }
    }
}
