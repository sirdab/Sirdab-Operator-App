package co.sirdab.driver.shared.feature.trip.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.demo.RouteUtil
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.LatLng
import co.sirdab.driver.shared.core.model.Load
import co.sirdab.driver.shared.core.model.MapBounds
import co.sirdab.driver.shared.core.model.Shipper
import co.sirdab.driver.shared.core.model.Trip
import co.sirdab.driver.shared.feature.loadboard.api.LoadRepository
import co.sirdab.driver.shared.feature.trip.api.TripRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class ActiveTripUiState(
    val trip: Trip? = null,
    val load: Load? = null,
    val shipper: Shipper? = null,
    val routePoints: List<LatLng> = emptyList(),
    val bounds: MapBounds? = null,
    val detentionSeconds: Long = 0,
)

private data class TripExtra(
    val load: Load? = null,
    val shipper: Shipper? = null,
    val route: List<LatLng> = emptyList(),
    val bounds: MapBounds? = null,
)

@OptIn(ExperimentalTime::class)
class ActiveTripViewModel(
    private val tripRepository: TripRepository,
    private val loadRepository: LoadRepository,
) : ViewModel() {

    private val extra = MutableStateFlow(TripExtra())
    private val tick = MutableStateFlow(0L)

    private fun now() = Clock.System.now().toEpochMilliseconds()

    init {
        viewModelScope.launch {
            tripRepository.observeActiveTrip().distinctUntilChangedBy { it?.loadId }.collect { trip ->
                if (trip == null) {
                    extra.value = TripExtra()
                    return@collect
                }
                val route = tripRepository.routePoints(trip.id)
                val load = (loadRepository.loadDetail(trip.loadId) as? AppResult.Success)?.data
                val shipper = load?.let { loadRepository.shipper(it.shipperId) }
                extra.value = TripExtra(load, shipper, route, RouteUtil.boundsFor(route))
            }
        }
        viewModelScope.launch {
            while (true) {
                tick.value = now()
                delay(1000)
            }
        }
    }

    val state = combine(tripRepository.observeActiveTrip(), extra, tick) { trip, ex, t ->
        val detention = trip?.detentionStartedAtMillis?.let { ((t - it) / 1000).coerceAtLeast(0) } ?: 0
        ActiveTripUiState(
            trip = trip,
            load = ex.load,
            shipper = ex.shipper,
            routePoints = ex.route,
            bounds = ex.bounds,
            detentionSeconds = detention,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ActiveTripUiState())

    fun advance() {
        val id = state.value.trip?.id ?: return
        viewModelScope.launch { tripRepository.advanceStatus(id) }
    }
}
