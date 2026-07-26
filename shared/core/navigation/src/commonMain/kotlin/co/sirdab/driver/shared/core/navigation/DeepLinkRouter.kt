package co.sirdab.driver.shared.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge from platform deep-link / notification-tap callbacks into the Nav3 back stack.
 * Navigation 3 has no declarative deep-link DSL, so App() observes this StateFlow and pushes
 * the matching route. Notifications fired by the simulation carry a deep-link string that lands here.
 */
object DeepLinkRouter {
    private val _pendingDeepLink = MutableStateFlow<String?>(null)
    val pendingDeepLink: StateFlow<String?> = _pendingDeepLink.asStateFlow()

    fun onDeepLink(target: String?) {
        _pendingDeepLink.value = target
    }

    fun consume() {
        _pendingDeepLink.value = null
    }
}
