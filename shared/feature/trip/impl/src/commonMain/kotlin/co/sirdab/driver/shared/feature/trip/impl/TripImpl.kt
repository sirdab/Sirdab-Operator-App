package co.sirdab.driver.shared.feature.trip.impl

import co.sirdab.driver.shared.feature.trip.api.DriverTripRepository
import co.sirdab.driver.shared.feature.trip.api.TripEventRecorder
import co.sirdab.driver.shared.feature.trip.impl.data.DriverTripRepositoryHttp
import co.sirdab.driver.shared.feature.trip.impl.data.TripEventRecorderQueued
import co.sirdab.driver.shared.feature.trip.impl.presentation.DriverTripDetailViewModel
import co.sirdab.driver.shared.feature.trip.impl.presentation.DriverTripsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** Trips as the TMS models them: a list of stops the driver works through. */
val tripModule: Module = module {
    single { DriverTripRepositoryHttp(get()) } bind DriverTripRepository::class
    single { TripEventRecorderQueued(queue = get(), files = get()) } bind TripEventRecorder::class
    viewModel { DriverTripsViewModel(get(), get()) }
    viewModel { (tripId: String) -> DriverTripDetailViewModel(tripId, get(), get()) }
}
