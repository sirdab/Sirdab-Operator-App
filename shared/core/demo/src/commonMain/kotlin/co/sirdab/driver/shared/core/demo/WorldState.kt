package co.sirdab.driver.shared.core.demo

import co.sirdab.driver.shared.core.model.AppNotification
import co.sirdab.driver.shared.core.model.Document
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.model.VerificationState
import co.sirdab.driver.shared.core.model.TripEarning
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
    val earnings: List<TripEarning> = emptyList(),
    val documents: List<Document> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val onboardingComplete: Boolean = false,
)
