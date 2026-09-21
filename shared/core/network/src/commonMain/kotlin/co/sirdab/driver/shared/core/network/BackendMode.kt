package co.sirdab.driver.shared.core.network

/**
 * Which world the app is running against.
 *
 * The UI needs this because the two backends do not offer the same thing yet: the demo world has a
 * scripted single active trip, the TMS has a list of real ones. Screens read it to pick which they
 * render, rather than probing Koin for whether a binding happens to exist.
 */
enum class BackendMode { DEMO, TMS }
