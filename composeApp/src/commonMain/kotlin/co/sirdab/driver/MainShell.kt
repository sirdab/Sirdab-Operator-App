package co.sirdab.driver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.network.BackendMode
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.coming_soon
import co.sirdab.driver.shared.core.ui.generated.resources.section_in_progress
import co.sirdab.driver.shared.core.ui.generated.resources.board_locked
import co.sirdab.driver.shared.core.ui.generated.resources.paperwork_outstanding
import co.sirdab.driver.shared.core.ui.generated.resources.review_banner
import co.sirdab.driver.shared.core.ui.generated.resources.review_fix_rejected
import co.sirdab.driver.shared.core.ui.generated.resources.tab_loads
import co.sirdab.driver.shared.core.ui.generated.resources.tab_profile
import co.sirdab.driver.shared.core.ui.generated.resources.tab_trip
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.bidding.impl.presentation.DriverPostingsScreen
import co.sirdab.driver.shared.feature.profile.impl.presentation.ProfileScreen
import co.sirdab.driver.shared.feature.trip.impl.presentation.DriverTripsScreen
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverOnboardingRepository
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private data class TabSpec(val tab: MainTab, val icon: ImageVector, val label: StringResource)

@Composable
fun MainShell(
    initialTab: MainTab = MainTab.LOADS,
    onOpenInbox: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onLoggedOut: () -> Unit,
    onFixPaperwork: () -> Unit,
    onOpenReview: () -> Unit,
    authRepository: AuthRepository = koinInject(),
    onboardingRepository: DriverOnboardingRepository = koinInject(),
    backendMode: BackendMode = koinInject(),
) {
    // Postings and trips both come from the TMS now that the demo world's own
    // board and trip simulation are gone, so demo mode is the profile alone.
    val tmsOnly = backendMode == BackendMode.TMS

    // Carrying no fleet is an ordinary, permanent state, not a queue: a driver signs themselves up,
    // ops approves them, and they work independently. Only a fleet that has taken a driver on gets
    // a say in whether they may work, through `canAcceptLoads` — it accounts for an expired licence
    // and for a deactivated driver or truck, none of which the document list shows. No answer means
    // nobody with standing to give one, so nothing is withheld on the strength of it.
    val verification by authRepository.observeVerification().collectAsState(initial = null)
    val canWork = tmsOnly && verification?.canAcceptLoads != false

    // A driver who finished sign-up can still owe something later: ops rejects a document, a
    // licence lapses, a truck is added to their profile. The server reopens `missing` for it and
    // says nothing else, so this is the only place they would ever hear about it.
    val profile by onboardingRepository.profile.collectAsState()
    val outstanding = profile?.missing.orEmpty().size

    // Read once on entry rather than only at sign-in: a rejection that landed while the app was
    // closed is the common case, and there is no push to tell us about it.
    LaunchedEffect(Unit) { onboardingRepository.refresh() }

    // Every tab, whatever the driver may do with them. A driver waiting on approval has handed over
    // everything asked of them, and meeting that with one lonely tab reads as a broken account
    // rather than a queue — so the app is whole, the board and the trip list say plainly that
    // nothing has arrived yet, and the banner says why.
    val tabs = listOfNotNull(
        TabSpec(MainTab.LOADS, Icons.Default.LocalShipping, Res.string.tab_loads).takeIf { tmsOnly },
        TabSpec(MainTab.TRIP, Icons.Default.Map, Res.string.tab_trip).takeIf { tmsOnly },
        TabSpec(MainTab.PROFILE, Icons.Default.Person, Res.string.tab_profile),
    )

    var selected by rememberSaveable { mutableStateOf(initialTab) }
    // Derived rather than assigned: a tab can disappear under the driver when the session changes,
    // and rewriting their choice would forget it once it comes back.
    val shown = if (tabs.none { it.tab == selected }) MainTab.PROFILE else selected

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { spec ->
                    NavigationBarItem(
                        selected = shown == spec.tab,
                        onClick = { selected = spec.tab },
                        icon = { Icon(spec.icon, contentDescription = null) },
                        label = { Text(stringResource(spec.label), maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                // Why there is no board comes first: it is the thing a driver is looking at the
                // empty screen wondering about.
                tmsOnly && !canWork -> BoardLockedBanner(
                    message = when {
                        verification?.hasRejection == true -> Res.string.review_fix_rejected
                        else -> Res.string.board_locked
                    },
                )
                // Working, but something is owed. Tappable, because unlike the banner above there
                // is something the driver can do about it right now.
                tmsOnly && outstanding > 0 -> BoardLockedBanner(
                    message = Res.string.paperwork_outstanding,
                    count = outstanding,
                    onClick = onFixPaperwork,
                )
                // Working, and waiting on ops. Nothing is owed and nothing is blocked; the driver
                // can open it to see which document is where.
                tmsOnly && profile?.isUnderReview == true -> BoardLockedBanner(
                    message = Res.string.review_banner,
                    onClick = onOpenReview,
                )
            }
            Box(Modifier.fillMaxSize()) {
                when (shown) {
                    MainTab.LOADS -> DriverPostingsScreen()
                    MainTab.TRIP -> DriverTripsScreen(onOpenTrip = onOpenTrip)
                    MainTab.PROFILE -> ProfileScreen(
                        onOpenHistory = onOpenHistory,
                        onLoggedOut = onLoggedOut,
                    )
                }
            }
        }
    }
}

/**
 * Says why there is no board, or what is still owed, in the one place a driver will look for it.
 *
 * [onClick] is given only when there is something to do about it: a banner that looks tappable and
 * is not is worse than one that plainly is not.
 */
@Composable
private fun BoardLockedBanner(
    message: StringResource,
    count: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Text(
            if (count != null) stringResource(message, count) else stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

@Composable
private fun ComingSoon(titleRes: StringResource, authRepository: AuthRepository) {
    val driver by authRepository.observeDriver().collectAsState(initial = Driver("", "", "", ""))
    Box(Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val name = driver.fullNameEn.ifBlank { driver.fullNameAr }
            if (name.isNotBlank()) {
                Text("👋 $name", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Spacing.sm))
            }
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.xs))
            Text(stringResource(Res.string.coming_soon), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                stringResource(Res.string.section_in_progress),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
