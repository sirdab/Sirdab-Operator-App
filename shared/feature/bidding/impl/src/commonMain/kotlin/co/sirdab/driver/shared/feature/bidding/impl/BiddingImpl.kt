package co.sirdab.driver.shared.feature.bidding.impl

import co.sirdab.driver.shared.feature.bidding.api.DriverBiddingRepository
import co.sirdab.driver.shared.feature.bidding.impl.data.DriverBiddingRepositoryHttp
import co.sirdab.driver.shared.feature.bidding.impl.presentation.DriverPostingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Postings and bids from the TMS.
 *
 * The demo board's negotiation model went with its screens: it had counters,
 * outbid events and auto-bid rules the TMS has no concept of. A posting is
 * answered once and the dispatcher awards.
 */
val tmsBiddingModule: Module = module {
    single { DriverBiddingRepositoryHttp(get()) } bind DriverBiddingRepository::class
    viewModelOf(::DriverPostingsViewModel)
}
