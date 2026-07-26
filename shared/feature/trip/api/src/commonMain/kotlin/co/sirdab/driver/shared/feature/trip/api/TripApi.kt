package co.sirdab.driver.shared.feature.trip.api

import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.LatLng
import co.sirdab.driver.shared.core.model.ProofOfDelivery
import co.sirdab.driver.shared.core.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
sealed interface TripRoute : NavKey {
    @Serializable data object Active : TripRoute
    @Serializable data class Pod(val tripId: String) : TripRoute
}

interface TripRepository {
    fun observeActiveTrip(): Flow<Trip?>
    suspend fun advanceStatus(tripId: String): AppResult<Trip>
    suspend fun submitPod(pod: ProofOfDelivery): AppResult<Unit>
    /** Sampled polyline for the trip's lane, for drawing on the map. */
    suspend fun routePoints(tripId: String): List<LatLng>
}

interface LocationRepository {
    fun observeDriverLocation(): Flow<LatLng?>
}
