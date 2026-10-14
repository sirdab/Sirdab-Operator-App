package co.sirdab.driver.shared.feature.bidding.impl

import co.sirdab.driver.shared.feature.bidding.api.DriverBiddingRepository
import co.sirdab.driver.shared.feature.bidding.impl.data.DriverBiddingRepositoryHttp
import co.sirdab.driver.shared.feature.bidding.impl.presentation.DriverPostingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** Postings and bids from the TMS. A posting is answered once and the dispatcher awards. */
val biddingModule: Module = module {
    single { DriverBiddingRepositoryHttp(get()) } bind DriverBiddingRepository::class
    // Not viewModelOf: that would ask Koin for the clock too, which only tests replace.
    viewModel { DriverPostingsViewModel(get(), get()) }
}
