package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TripStatus {
    ASSIGNED,
    EN_ROUTE_TO_PICKUP,
    AT_PICKUP,
    LOADING,
    EN_ROUTE_TO_DROPOFF,
    AT_DROPOFF,
    UNLOADING,
    POD_PENDING,
    COMPLETED,
}

@Serializable
data class Trip(
    val id: String,
    val loadId: String,
    val status: TripStatus = TripStatus.ASSIGNED,
    val agreedRateSar: Int,
    val polylineId: String? = null,
    /** 0f..1f progress along the polyline (driven by the GPS actor). */
    val routeProgress: Float = 0f,
    val currentPosition: LatLng? = null,
    val etaMinutes: Int? = null,
    val detentionStartedAtMillis: Long? = null,
    val detentionFreeMinutes: Int = 120,
    val startedAtMillis: Long? = null,
    val completedAtMillis: Long? = null,
)

@Serializable
data class ProofOfDelivery(
    val tripId: String,
    val photoRefs: List<String> = emptyList(),
    val recipientName: String = "",
    val sealNumber: String = "",
    val signatureRef: String? = null,
    val capturedAtMillis: Long? = null,
)
