package co.sirdab.driver.shared.feature.wallet.impl.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.TxnStatus
import co.sirdab.driver.shared.core.model.WalletState
import co.sirdab.driver.shared.core.model.WalletTxn
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.TagChip
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.txn_pending
import co.sirdab.driver.shared.core.ui.generated.resources.txn_settled
import co.sirdab.driver.shared.core.ui.generated.resources.unit_sar
import co.sirdab.driver.shared.core.ui.generated.resources.wallet_available
import co.sirdab.driver.shared.core.ui.generated.resources.wallet_empty
import co.sirdab.driver.shared.core.ui.generated.resources.wallet_payout
import co.sirdab.driver.shared.core.ui.generated.resources.wallet_payout_note
import co.sirdab.driver.shared.core.ui.generated.resources.wallet_pending
import co.sirdab.driver.shared.core.ui.generated.resources.wallet_title
import co.sirdab.driver.shared.core.ui.generated.resources.wallet_transactions
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.core.util.toGroupedString
import co.sirdab.driver.shared.feature.wallet.api.WalletRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs

class WalletViewModel(private val walletRepository: WalletRepository) : ViewModel() {
    val state = walletRepository.observeWallet()
        .stateIn(viewModelScope, SharingStarted.Eagerly, WalletState())

    fun requestPayout() {
        val amount = state.value.availableSar
        if (amount <= 0) return
        viewModelScope.launch { walletRepository.requestPayout(amount) }
    }
}

@Composable
fun WalletScreen(viewModel: WalletViewModel = koinViewModel()) {
    val wallet by viewModel.state.collectAsState()
    val lang = Locale.current.language

    Column(Modifier.fillMaxSize().padding(Spacing.md)) {
        Text(stringResource(Res.string.wallet_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.md))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BalanceCard(stringResource(Res.string.wallet_available), wallet.availableSar, lang, primary = true, modifier = Modifier.weight(1f))
            BalanceCard(stringResource(Res.string.wallet_pending), wallet.pendingSar, lang, primary = false, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(Spacing.sm))
        DriverButton(
            text = stringResource(Res.string.wallet_payout),
            onClick = viewModel::requestPayout,
            enabled = wallet.availableSar > 0,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(Res.string.wallet_payout_note),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xxs),
        )

        Spacer(Modifier.height(Spacing.lg))
        Text(stringResource(Res.string.wallet_transactions), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.xs))
        if (wallet.transactions.isEmpty()) {
            Text(stringResource(Res.string.wallet_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(wallet.transactions, key = { it.id }) { txn -> TxnRow(txn, lang) }
            }
        }
    }
}

@Composable
private fun BalanceCard(label: String, amount: Int, lang: String, primary: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = if (primary) AppColors.Primary.c600 else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (primary) AppColors.White else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                "${amount.toGroupedString(lang)} ${stringResource(Res.string.unit_sar)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (primary) AppColors.White else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TxnRow(txn: WalletTxn, lang: String) {
    Surface(shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.md).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (lang == "ar") txn.descriptionAr else txn.descriptionEn, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                TagChip(
                    if (txn.status == TxnStatus.SETTLED) stringResource(Res.string.txn_settled) else stringResource(Res.string.txn_pending),
                    tone = if (txn.status == TxnStatus.SETTLED) ChipTone.SUCCESS else ChipTone.WARNING,
                )
            }
            val sign = if (txn.amountSar < 0) "-" else "+"
            Text(
                "$sign${abs(txn.amountSar).toGroupedString(lang)} ${stringResource(Res.string.unit_sar)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (txn.amountSar < 0) AppColors.Red.c600 else AppColors.Green.c600,
            )
        }
    }
}
