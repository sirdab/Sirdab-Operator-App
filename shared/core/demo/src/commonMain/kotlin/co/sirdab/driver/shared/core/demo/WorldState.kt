package co.sirdab.driver.shared.core.demo

import co.sirdab.driver.shared.core.model.AppNotification
import co.sirdab.driver.shared.core.model.AutoBidRule
import co.sirdab.driver.shared.core.model.Bid
import co.sirdab.driver.shared.core.model.City
import co.sirdab.driver.shared.core.model.Document
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.model.Load
import co.sirdab.driver.shared.core.model.RoutePolyline
import co.sirdab.driver.shared.core.model.Shipper
import co.sirdab.driver.shared.core.model.Trip
import co.sirdab.driver.shared.core.model.VerificationState
import co.sirdab.driver.shared.core.model.WalletState
import kotlinx.serialization.Serializable

/**
 * The single source of truth for the entire simulated demo. Serialized to DataStore on every
 * mutation so the app survives force-close (plan §7).
 */
@Serializable
data class WorldState(
    val driver: Driver = Driver(
        id = "driver-me",
        fullNameEn = "",
        fullNameAr = "",
        phone = "",
        verification = VerificationState.UNVERIFIED,
    ),
    val personas: List<Driver> = emptyList(),
    val cities: List<City> = emptyList(),
    val shippers: List<Shipper> = emptyList(),
    val loads: List<Load> = emptyList(),
    val polylines: List<RoutePolyline> = emptyList(),
    val bids: List<Bid> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val wallet: WalletState = WalletState(),
    val documents: List<Document> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val autoBidRule: AutoBidRule? = null,
    val onboardingComplete: Boolean = false,
)
