package co.sirdab.driver.shared.feature.bidding.impl.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.model.DriverBid
import co.sirdab.driver.shared.core.model.DriverPosting
import co.sirdab.driver.shared.core.model.DriverTruck
import co.sirdab.driver.shared.core.model.Money
import co.sirdab.driver.shared.core.platform.locale.AppLocale
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.DriverTextField
import co.sirdab.driver.shared.core.ui.components.OptionRow
import co.sirdab.driver.shared.core.ui.components.equipmentLabel
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.bid_accept_confirm
import co.sirdab.driver.shared.core.ui.generated.resources.bid_amount
import co.sirdab.driver.shared.core.ui.generated.resources.bid_edit_title
import co.sirdab.driver.shared.core.ui.generated.resources.bid_go_lower
import co.sirdab.driver.shared.core.ui.generated.resources.bid_lowest_competing
import co.sirdab.driver.shared.core.ui.generated.resources.bid_match
import co.sirdab.driver.shared.core.ui.generated.resources.bid_note
import co.sirdab.driver.shared.core.ui.generated.resources.bid_send
import co.sirdab.driver.shared.core.ui.generated.resources.bid_title
import co.sirdab.driver.shared.core.ui.generated.resources.bid_truck
import co.sirdab.driver.shared.core.ui.generated.resources.bid_undercut_body
import co.sirdab.driver.shared.core.ui.generated.resources.bid_undercut_title
import co.sirdab.driver.shared.core.ui.generated.resources.bid_update
import co.sirdab.driver.shared.core.ui.generated.resources.err_bid_out_of_range
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The offer sheet, in two shapes.
 *
 * A fixed-rate posting shows the price and one button: accepting is the whole
 * interaction, which is what a short fulfilment window needs. An open posting
 * asks for a number instead, inside the posting's range when it has one. Same
 * endpoint underneath; the difference is whether the dispatcher already named a price.
 *
 * With [editing], the sheet reprices the driver's own pending bid instead: the price is
 * the only thing an edit changes, so the truck and note are not asked again. When another
 * carrier is asking less, it leads with "go lower or match?": matching sends that bid's
 * amount to the halala, going lower fills in the most whole riyals under it to adjust.
 *
 * The range is checked here the way the server checks it, in halalas, so an
 * amount it would refuse never goes out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceBidSheet(
    posting: DriverPosting,
    trucks: List<DriverTruck>,
    editing: DriverBid?,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (amountSar: Int, truckId: String, note: String) -> Unit,
    onUpdate: (amountCents: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lang = AppLocale.current()
    val fixed = posting.targetRate
    // A fixed rate sits inside the range by the server's own rule, so only a typed price is held to it.
    val range = posting.bidRange.takeIf { fixed == null }

    var amount by remember { mutableStateOf((fixed ?: editing?.amount)?.amountSar?.toString().orEmpty()) }
    var truckId by remember { mutableStateOf(trucks.firstOrNull()?.id.orEmpty()) }
    var note by remember { mutableStateOf("") }
    val amountFocus = remember { FocusRequester() }
    val undercut = editing?.undercut(range)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
        ) {
            Text(
                stringResource(if (editing != null) Res.string.bid_edit_title else Res.string.bid_title),
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
            if (editing != null && undercut != null) {
                GoLowerOrMatch(
                    undercut = undercut,
                    yours = editing.amount,
                    lang = lang,
                    enabled = !isSubmitting,
                    onMatch = onUpdate,
                    onGoLower = { sar ->
                        amount = sar.toString()
                        amountFocus.requestFocus()
                    },
                )
                Spacer(Modifier.height(Spacing.md))
            }
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
                    modifier = Modifier.focusRequester(amountFocus),
                )
                range?.label(lang)?.let { hint ->
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val typed = amount.toIntOrNull()
                if (range != null && typed != null && typed * 100L !in range) {
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        stringResource(Res.string.err_bid_out_of_range),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                // Undercut, the block above already names it; otherwise it is good to know.
                editing?.lowestCompetingBid?.takeIf { undercut == null }?.let { lowest ->
                    Spacer(Modifier.height(Spacing.xxs))
                    // Amber while the typed price is still above it: someone is offering less.
                    val undercut = typed != null && typed * 100L > lowest.amountCents
                    Text(
                        stringResource(
                            Res.string.bid_lowest_competing,
                            "${lowest.display(lang)} ${stringResource(unitSar)}",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (undercut) AppColors.Amber.c800 else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (editing == null && trucks.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    stringResource(Res.string.bid_truck),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Spacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    trucks.forEach { truck ->
                        OptionRow(
                            label = listOf(
                                truck.licencePlate,
                                equipmentLabel(truck.bodyType, truck.sizeClass, truck.temperature),
                            ).filter { it.isNotEmpty() }.joinToString(" · "),
                            selected = truck.id == truckId,
                            onClick = { truckId = truck.id },
                        )
                    }
                }
            }

            if (editing == null) {
                Spacer(Modifier.height(Spacing.lg))
                DriverTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = stringResource(Res.string.bid_note),
                )
            }

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
            val inRange = range == null || amountSar * 100L in range
            DriverButton(
                text = when {
                    editing != null -> stringResource(Res.string.bid_update)
                    fixed != null -> stringResource(
                        Res.string.bid_accept_confirm,
                        "${fixed.display(lang)} ${stringResource(unitSar)}",
                    )
                    else -> stringResource(Res.string.bid_send)
                },
                onClick = { if (editing != null) onUpdate(amountSar * 100) else onSubmit(amountSar, truckId, note) },
                // An edit to the same price would only tell the dispatcher nothing changed.
                enabled = amountSar > 0 && inRange && if (editing != null) {
                    amountSar * 100 != editing.amount.amountCents
                } else {
                    truckId.isNotBlank()
                },
                isLoading = isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The prompt an undercut bid opens on: who is asking less, and the two ways to answer it. */
@Composable
private fun GoLowerOrMatch(
    undercut: Undercut,
    yours: Money,
    lang: String,
    enabled: Boolean,
    onMatch: (amountCents: Int) -> Unit,
    onGoLower: (amountSar: Int) -> Unit,
) {
    val unit = stringResource(unitSar)
    // Held together, so a line never breaks between a number and its currency.
    val lowest = "${undercut.lowest.display(lang)}\u00A0$unit"
    Surface(color = AppColors.Amber.c50, shape = RoundedCornerShape(Radius.md)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = AppColors.Amber.c800,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    stringResource(Res.string.bid_undercut_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Amber.c800,
                )
            }
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                stringResource(Res.string.bid_undercut_body, lowest, "${yours.display(lang)}\u00A0$unit"),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Gray.c800,
            )
            // An answer the posting's range would refuse is not offered at all.
            if (undercut.matchCents != null || undercut.goLowerSar != null) {
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    undercut.matchCents?.let { cents ->
                        Button(
                            onClick = { onMatch(cents) },
                            enabled = enabled,
                            shape = RoundedCornerShape(Radius.md),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        ) {
                            Text(stringResource(Res.string.bid_match, lowest), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    undercut.goLowerSar?.let { sar ->
                        OutlinedButton(
                            onClick = { onGoLower(sar) },
                            enabled = enabled,
                            shape = RoundedCornerShape(Radius.md),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        ) {
                            Text(stringResource(Res.string.bid_go_lower), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}
