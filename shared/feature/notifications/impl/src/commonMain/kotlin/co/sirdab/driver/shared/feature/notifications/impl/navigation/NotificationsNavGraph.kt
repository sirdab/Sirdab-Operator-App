package co.sirdab.driver.shared.feature.notifications.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.feature.notifications.api.NotificationsRoute
import co.sirdab.driver.shared.feature.notifications.impl.presentation.InboxScreen

fun EntryProviderScope<NavKey>.notificationsEntries(
    onBack: () -> Unit,
) {
    entry<NotificationsRoute.Inbox> { InboxScreen(onBack = onBack) }
}
