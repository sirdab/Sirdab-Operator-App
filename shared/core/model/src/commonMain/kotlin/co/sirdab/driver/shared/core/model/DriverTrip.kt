package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

/** One page of a cursor-paginated list. The cursor is opaque: pass it back untouched. */
@Serializable
data class Page<T>(
    val items: List<T>,
    val nextCursor: String? = null,
)

/**
 * A trip's status in the TMS.
 *
 * Deliberately not [TripStatus], which is the demo world's nine-step script. A TMS trip has no
 * status of its own: the server derives this from the trip's legs, so the app reads it and never
 * sets it. What the driver does instead is record events against stops.
 */
@Serializable
enum class TripLifecycle { CREATED, ASSIGNED, IN_TRANSIT, COMPLETE, CANCELLED, FAILED }

@Serializable
enum class StopType { PICKUP, DROPOFF }

@Serializable
enum class StopStatus { PENDING, ARRIVED, DEPARTED, COMPLETED, SKIPPED }

/**
 * A stop's address, as the Saudi national address records it.
 *
 * Every part is nullable because the server holds what it was given, and an
 * address entered in a hurry may be a label and a city. Composing the lines is
 * the app's job now: the server used to send a flattened `line1`/`line2`, which
 * gave every client the same guess about how to say it out loud.
 */
@Serializable
data class StopAddress(
    val id: String,
    val label: String,
    val street: String? = null,
    val district: String? = null,
    val buildingNumber: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
) {
    /** Building and street, or the label when there is no street to give. */
    val addressLine: String
        get() = listOfNotNull(buildingNumber, street)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { label }

    /** District and postcode, the part that narrows it down. Null when neither is known. */
    val districtLine: String?
        get() = listOfNotNull(district, zipCode)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { null }
}

@Serializable
data class StopContact(
    val name: String,
    val phone: String,
)

@Serializable
data class TripStop(
    val id: String,
    val legId: String,
    val stopType: StopType,
    val sequenceNumber: Int,
    val status: StopStatus,
    val plannedAtMillis: Long? = null,
    val arrivedAtMillis: Long? = null,
    val departedAtMillis: Long? = null,
    val address: StopAddress,
    val contact: StopContact? = null,
    /** Photos already attached to this stop, so the driver can see proof landed. */
    val photoProofCount: Int = 0,
)

@Serializable
data class TripLeg(
    val id: String,
    val sequence: Int,
    val originAddressId: String,
    val destinationAddressId: String,
)

/**
 * A truck-day of work, as the TMS models it.
 *
 * [stops] and [legs] are empty on a summary from the list endpoint and populated on a detail. The
 * driver runs the trip by working down [stops] in [TripStop.sequenceNumber] order.
 */
@Serializable
data class DriverTrip(
    val id: String,
    val reference: String,
    val status: TripLifecycle,
    val scheduledAtMillis: Long? = null,
    /** True when the whole trip stays inside one city. */
    val inCity: Boolean = false,
    val stopCount: Int = 0,
    val legs: List<TripLeg> = emptyList(),
    val stops: List<TripStop> = emptyList(),
) {
    /** The stop the driver is working now: the first that is not finished with. */
    val currentStop: TripStop?
        get() = stops.sortedBy { it.sequenceNumber }
            .firstOrNull { it.status != StopStatus.COMPLETED && it.status != StopStatus.SKIPPED }
}
