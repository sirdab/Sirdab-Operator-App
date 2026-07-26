package co.sirdab.driver.shared.feature.loadboard.impl.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import co.sirdab.driver.shared.core.demo.DemoClock
import co.sirdab.driver.shared.core.demo.DemoControls
import co.sirdab.driver.shared.core.demo.DemoScenario
import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.demo.ForcedBidOutcome
import co.sirdab.driver.shared.core.platform.notification.Notifier
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.demo_fire_notif
import co.sirdab.driver.shared.core.ui.generated.resources.demo_force_outcome
import co.sirdab.driver.shared.core.ui.generated.resources.demo_inject
import co.sirdab.driver.shared.core.ui.generated.resources.demo_outcome_auto
import co.sirdab.driver.shared.core.ui.generated.resources.demo_outcome_counter
import co.sirdab.driver.shared.core.ui.generated.resources.demo_outcome_lose
import co.sirdab.driver.shared.core.ui.generated.resources.demo_outcome_win
import co.sirdab.driver.shared.core.ui.generated.resources.demo_reset
import co.sirdab.driver.shared.core.ui.generated.resources.demo_scenarios
import co.sirdab.driver.shared.core.ui.generated.resources.demo_time
import co.sirdab.driver.shared.core.ui.generated.resources.demo_title
import co.sirdab.driver.shared.core.ui.generated.resources.scn_bid_pending
import co.sirdab.driver.shared.core.ui.generated.resources.scn_browsing
import co.sirdab.driver.shared.core.ui.generated.resources.scn_new_driver
import co.sirdab.driver.shared.core.ui.generated.resources.scn_paid
import co.sirdab.driver.shared.core.ui.generated.resources.scn_pod
import co.sirdab.driver.shared.core.ui.generated.resources.scn_trip
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.loadboard.api.LoadRepository
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoControlPanel(
    onDismiss: () -> Unit,
    world: DemoWorld = koinInject(),
    clock: DemoClock = koinInject(),
    controls: DemoControls = koinInject(),
    notifier: Notifier = koinInject(),
    loadRepository: LoadRepository = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val multiplier by clock.multiplier.collectAsState()
    val forced by controls.forcedOutcome.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Text(stringResource(Res.string.demo_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Spacing.lg))

            // Time speed
            Text(stringResource(Res.string.demo_time), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(1, 60, 300).forEach { m ->
                    Toggle("${m}×", selected = multiplier == m) { clock.setMultiplier(m) }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            // Force next bid outcome
            Text(stringResource(Res.string.demo_force_outcome), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Toggle(stringResource(Res.string.demo_outcome_auto), forced == ForcedBidOutcome.AUTO) { controls.forcedOutcome.value = ForcedBidOutcome.AUTO }
                Toggle(stringResource(Res.string.demo_outcome_win), forced == ForcedBidOutcome.WIN) { controls.forcedOutcome.value = ForcedBidOutcome.WIN }
                Toggle(stringResource(Res.string.demo_outcome_counter), forced == ForcedBidOutcome.COUNTER) { controls.forcedOutcome.value = ForcedBidOutcome.COUNTER }
                Toggle(stringResource(Res.string.demo_outcome_lose), forced == ForcedBidOutcome.LOSE) { controls.forcedOutcome.value = ForcedBidOutcome.LOSE }
            }

            Spacer(Modifier.height(Spacing.lg))
            // Jump to scenario
            Text(stringResource(Res.string.demo_scenarios), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                val scenarios = listOf(
                    stringResource(Res.string.scn_new_driver) to DemoScenario.NEW_DRIVER,
                    stringResource(Res.string.scn_browsing) to DemoScenario.BROWSING,
                    stringResource(Res.string.scn_bid_pending) to DemoScenario.BID_PENDING,
                    stringResource(Res.string.scn_trip) to DemoScenario.TRIP_MID,
                    stringResource(Res.string.scn_pod) to DemoScenario.POD_PENDING,
                    stringResource(Res.string.scn_paid) to DemoScenario.PAID,
                )
                scenarios.forEach { (label, scn) ->
                    OutlinedButton(onClick = { scope.launch { world.applyScenario(scn) }; onDismiss() }) { Text(label) }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            OutlinedButton(onClick = { scope.launch { loadRepository.refresh() } }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.demo_inject))
            }
            Spacer(Modifier.height(Spacing.xs))
            OutlinedButton(
                onClick = { notifier.post("demo-test", "Sirdab Operators", "Test notification 🔔") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.demo_fire_notif)) }
            Spacer(Modifier.height(Spacing.xs))
            OutlinedButton(
                onClick = { world.update { it.copy(bids = emptyList(), trips = emptyList(), notifications = emptyList()) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.demo_reset)) }

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun Toggle(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}
