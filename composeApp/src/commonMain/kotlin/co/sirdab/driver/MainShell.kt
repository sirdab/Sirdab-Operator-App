package co.sirdab.driver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import co.sirdab.driver.shared.core.ui.generated.resources.tab_loads
import co.sirdab.driver.shared.core.ui.generated.resources.tab_profile
import co.sirdab.driver.shared.core.ui.generated.resources.tab_trip
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.bidding.impl.presentation.DriverPostingsScreen
import co.sirdab.driver.shared.feature.profile.impl.presentation.ProfileScreen
import co.sirdab.driver.shared.feature.trip.impl.presentation.DriverTripsScreen
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
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
    authRepository: AuthRepository = koinInject(),
    backendMode: BackendMode = koinInject(),
) {
    // Postings and trips both come from the TMS now that the demo world's own
    // board and trip simulation are gone, so demo mode is the profile alone.
    val tmsOnly = backendMode == BackendMode.TMS
    val tabs = listOfNotNull(
        TabSpec(MainTab.LOADS, Icons.Default.LocalShipping, Res.string.tab_loads).takeIf { tmsOnly },
        TabSpec(MainTab.TRIP, Icons.Default.Map, Res.string.tab_trip).takeIf { tmsOnly },
        TabSpec(MainTab.PROFILE, Icons.Default.Person, Res.string.tab_profile),
    )

    var selected by rememberSaveable {
        mutableStateOf(if (tabs.none { it.tab == initialTab }) MainTab.PROFILE else initialTab)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { spec ->
                    NavigationBarItem(
                        selected = selected == spec.tab,
                        onClick = { selected = spec.tab },
                        icon = { Icon(spec.icon, contentDescription = null) },
                        label = { Text(stringResource(spec.label), maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                MainTab.LOADS -> DriverPostingsScreen()
                MainTab.TRIP -> DriverTripsScreen(onOpenTrip = onOpenTrip)
                MainTab.PROFILE -> ProfileScreen(onOpenHistory = onOpenHistory)
            }
        }
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
