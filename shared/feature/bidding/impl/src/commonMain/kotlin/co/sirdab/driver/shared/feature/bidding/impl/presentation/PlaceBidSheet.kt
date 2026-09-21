package co.sirdab.driver.shared.feature.bidding.impl.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import co.sirdab.driver.shared.core.model.DriverPosting
import co.sirdab.driver.shared.core.model.DriverTruck
import co.sirdab.driver.shared.core.platform.locale.AppLocale
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.DriverTextField
import co.sirdab.driver.shared.core.ui.components.LanguageOptionRow
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.bid_accept_confirm
import co.sirdab.driver.shared.core.ui.generated.resources.bid_amount
import co.sirdab.driver.shared.core.ui.generated.resources.bid_note
import co.sirdab.driver.shared.core.ui.generated.resources.bid_send
import co.sirdab.driver.shared.core.ui.generated.resources.bid_title
import co.sirdab.driver.shared.core.ui.generated.resources.bid_truck
import co.sirdab.driver.shared.core.ui.generated.resources.unit_sar
import co.sirdab.driver.shared.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The offer sheet, in two shapes.
 *
 * A fixed-rate posting shows the price and one button: accepting is the whole
 * interaction, which is what a short fulfilment window needs. An open posting
 * asks for a number instead. Same endpoint underneath; the difference is whether
 * the dispatcher already named a price.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceBidSheet(
    posting: DriverPosting,
    trucks: List<DriverTruck>,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (amountSar: Int, truckId: String, note: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lang = AppLocale.current()
    val fixed = posting.targetRate

    var amount by remember { mutableStateOf(fixed?.amountSar?.toString().orEmpty()) }
    var truckId by remember { mutableStateOf(trucks.firstOrNull()?.id.orEmpty()) }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
        ) {
            Text(
                stringResource(Res.string.bid_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "${posting.origin.city ?: posting.origin.label} → " +
                    "${posting.destination.city ?: posting.destination.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.lg))
            if (fixed != null) {
                // The price is not the driver's to choose, so it is shown, not asked.
                Text(
                    "${fixed.display(lang)} ${stringResource(unitSar)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                DriverTextField(
                    value = amount,
                    onValueChange = { input -> amount = input.filter { it.isDigit() } },
                    label = stringResource(Res.string.bid_amount),
                    keyboardType = KeyboardType.Number,
                )
            }

            if (trucks.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    stringResource(Res.string.bid_truck),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Spacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    trucks.forEach { truck ->
                        LanguageOptionRow(
                            label = "${truck.licencePlate} · " +
                                stringResource(truck.truckSize.labelRes()),
                            selected = truck.id == truckId,
                            onClick = { truckId = truck.id },
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            DriverTextField(
                value = note,
                onValueChange = { note = it },
                label = stringResource(Res.string.bid_note),
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            val amountSar = fixed?.amountSar ?: amount.toIntOrNull() ?: 0
            DriverButton(
                text = if (fixed != null) {
                    stringResource(
                        Res.string.bid_accept_confirm,
                        "${fixed.display(lang)} ${stringResource(unitSar)}",
                    )
                } else {
                    stringResource(Res.string.bid_send)
                },
                onClick = { onSubmit(amountSar, truckId, note) },
                enabled = amountSar > 0 && truckId.isNotBlank(),
                isLoading = isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
