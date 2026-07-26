package co.sirdab.driver.shared.feature.wallet.api

import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.WalletState
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
sealed interface WalletRoute : NavKey {
    @Serializable data object Overview : WalletRoute
    @Serializable data object Payout : WalletRoute
}

interface WalletRepository {
    fun observeWallet(): Flow<WalletState>
    suspend fun requestPayout(amountSar: Int): AppResult<Unit>
}
