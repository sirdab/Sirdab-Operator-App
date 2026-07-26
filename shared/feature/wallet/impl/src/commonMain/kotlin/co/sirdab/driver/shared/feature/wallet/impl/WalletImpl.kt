package co.sirdab.driver.shared.feature.wallet.impl

import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.demo.demoLatency
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.WalletState
import co.sirdab.driver.shared.feature.wallet.api.WalletRepository
import co.sirdab.driver.shared.feature.wallet.impl.presentation.WalletViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

class WalletRepositoryMock(private val world: DemoWorld) : WalletRepository {
    override fun observeWallet(): Flow<WalletState> = world.state.map { it.wallet }

    override suspend fun requestPayout(amountSar: Int): AppResult<Unit> {
        demoLatency()
        world.update { w ->
            w.copy(wallet = w.wallet.copy(availableSar = (w.wallet.availableSar - amountSar).coerceAtLeast(0)))
        }
        return AppResult.Success(Unit)
    }
}

val walletModule: Module = module {
    single { WalletRepositoryMock(get()) } bind WalletRepository::class
    viewModelOf(::WalletViewModel)
}
