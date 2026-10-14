package co.sirdab.driver.shared.feature.trip.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.analytics.Analytics
import co.sirdab.driver.shared.core.analytics.AnalyticsEvent
import co.sirdab.driver.shared.core.analytics.track
import co.sirdab.driver.shared.core.media.PhotoSource
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.core.model.StopStatus
import co.sirdab.driver.shared.core.model.TripStop
import co.sirdab.driver.shared.feature.trip.api.DriverTripRepository
import co.sirdab.driver.shared.feature.trip.api.ExceptionKind
import co.sirdab.driver.shared.feature.trip.api.ExceptionSeverity
import co.sirdab.driver.shared.feature.trip.api.TripEventRecorder
import co.sirdab.driver.shared.feature.trip.api.TripEventType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class DriverTripDetailUiState(
    val trip: DriverTrip? = null,
    val isLoading: Boolean = true,
    /** A reload the driver asked for, which keeps the trip on screen while it runs. */
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    /** Actions the server refused for good, which the driver has not dismissed yet. */
    val droppedWrites: Int = 0,
    val isReportingException: Boolean = false,
    val exceptionReported: Boolean = false,
)

class DriverTripDetailViewModel(
    private val tripId: String,
    private val repository: DriverTripRepository,
    private val recorder: TripEventRecorder,
    private val analytics: Analytics,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(DriverTripDetailUiState())
    val state: StateFlow<DriverTripDetailUiState> = _state.asStateFlow()

    val pendingWrites: StateFlow<Int> = recorder.pendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        load()
        viewModelScope.launch {
            // Null until the first count, which may be refusals from before this
            // screen opened; the load above already reads the trip fresh for those.
            var seen: Int? = null
            recorder.droppedCount().collect { count ->
                _state.value = _state.value.copy(droppedWrites = count)
                // A refusal means the screen has been showing a step the server
                // never took. Read the truth back rather than keep pretending.
                if (seen != null && count > seen!!) load(pulled = true)
                seen = count
            }
        }
    }

    fun acknowledgeDropped() {
        val count = _state.value.droppedWrites
        if (count > 0) analytics.track(AnalyticsEvent.rejectedWritesDismissed(tripId, count))
        recorder.acknowledgeDropped()
    }

    /** The driver opened directions to [stop]; the screen hands them to the maps app. */
    fun navigationOpened(stop: TripStop) {
        analytics.track(AnalyticsEvent.stopNavigationOpened(tripId, stop))
    }

    /** The driver rang [stop]'s contact; the screen does the dialling. */
    fun contactCalled(stop: TripStop) {
        analytics.track(AnalyticsEvent.stopContactCalled(tripId, stop))
    }

    /** The camera or library is opening for a photo of [stopId]; [attachPhoto] is what comes back. */
    fun photoCaptureStarted(stopId: String, source: PhotoSource) {
        analytics.track(AnalyticsEvent.photoProofStarted(tripId, stopId, source.name.lowercase()))
    }

    /**
     * Photos for this stop that have not reached the server yet.
     *
     * Kept apart from the stop's own `photoProofCount`, which is the server's
     * count. The screen adds the two to unlock Delivered (see [actionsFor]):
     * the queue sends the photo before the delivery tapped after it.
     */
    fun pendingProofs(stopId: String): StateFlow<Int> =
        proofFlows.getOrPut(stopId) {
            recorder.pendingProofs(stopId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
        }

    private val proofFlows = mutableMapOf<String, StateFlow<Int>>()

    /**
     * Keep the photo, then reload.
     *
     * The reload is what turns a queued photo into an unlocked button: it is
     * the server's count coming back, which is the only evidence that the
     * proof is really there.
     */
    fun attachPhoto(stopId: String, bytes: ByteArray, contentType: String) {
        val capturedAtMillis = clock.now().toEpochMilliseconds()
        viewModelScope.launch {
            val result = recorder.attachPhotoProof(
                stopId = stopId,
                bytes = bytes,
                contentType = contentType,
                capturedAtMillis = capturedAtMillis,
            )
            when (result) {
                is AppResult.Success -> {
                    analytics.track(AnalyticsEvent.photoProofAdded(tripId, stopId))
                    load()
                }
                is AppResult.Failure -> {
                    analytics.track(AnalyticsEvent.tripActionFailed(tripId, PHOTO_PROOF, result.error))
                    _state.value = _state.value.copy(errorMessage = result.error.message)
                }
            }
        }
    }

    /**
     * Read the trip again.
     *
     * [pulled] is the driver asking rather than the screen opening: the stops stay where they are
     * and the gesture carries its own indicator, so blanking the trip to a spinner would take away
     * the very thing they pulled to compare against.
     */
    fun load(pulled: Boolean = false) {
        _state.value = _state.value.copy(
            isLoading = !pulled,
            isRefreshing = pulled,
            errorMessage = null,
        )
        viewModelScope.launch {
            // Push before pulling. A proof still sitting in the queue is the
            // reason the stop reads as unproven, so sending it first is what
            // makes the reload that follows show the action unlocked.
            recorder.sync()
            _state.value = when (val result = repository.trip(tripId)) {
                // Whatever the sync could not send is laid back on top: the server's
                // copy predates it, and showing that copy would offer the driver a
                // step they already took, whose second tap the server then refuses.
                is AppResult.Success -> DriverTripDetailUiState(
                    trip = result.data.withQueued(recorder.queuedEvents(tripId)),
                    isLoading = false,
                    droppedWrites = _state.value.droppedWrites,
                )
                is AppResult.Failure -> {
                    analytics.track(AnalyticsEvent.tripLoadFailed(tripId, result.error))
                    _state.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.error.message,
                    )
                }
            }
        }
    }

    /**
     * The driver pulling to refresh.
     *
     * Apart from [load] because the screen also reloads by itself, after a photo and after a
     * refusal, and only the driver's own pulls say they were unsure what the trip shows.
     */
    fun refresh() {
        analytics.track(AnalyticsEvent.tripRefreshed(tripId, pendingWrites.value))
        load(pulled = true)
    }

    fun openExceptionReport() {
        analytics.track(AnalyticsEvent.exceptionReportOpened(tripId, _state.value.trip?.currentStop?.id))
        _state.value = _state.value.copy(isReportingException = true, exceptionReported = false)
    }

    /** The sheet closed without a report; sending one closes it without coming through here. */
    fun dismissExceptionReport() {
        if (!_state.value.isReportingException) return
        analytics.track(AnalyticsEvent.exceptionReportDismissed(tripId))
        _state.value = _state.value.copy(isReportingException = false)
    }

    /**
     * Report a problem against this trip, and against the stop being worked if
     * there is one: a refused delivery belongs to a stop, a breakdown does not.
     */
    fun reportException(kind: ExceptionKind, severity: ExceptionSeverity, note: String) {
        val trip = _state.value.trip ?: return
        val occurredAtMillis = clock.now().toEpochMilliseconds()
        _state.value = _state.value.copy(isReportingException = false)

        viewModelScope.launch {
            val result = recorder.reportException(
                tripId = trip.id,
                kind = kind,
                severity = severity,
                occurredAtMillis = occurredAtMillis,
                stopId = trip.currentStop?.id,
                note = note,
            )
            _state.value = when (result) {
                is AppResult.Success -> {
                    analytics.track(
                        AnalyticsEvent.exceptionReported(
                            tripId = trip.id,
                            stopId = trip.currentStop?.id,
                            kind = kind.wire,
                            severity = severity.wire,
                            hasNote = note.isNotBlank(),
                        ),
                    )
                    _state.value.copy(exceptionReported = true)
                }
                is AppResult.Failure -> {
                    analytics.track(
                        AnalyticsEvent.tripActionFailed(trip.id, TripEventType.EXCEPTION.wire, result.error),
                    )
                    _state.value.copy(errorMessage = result.error.message)
                }
            }
        }
    }

    /**
     * Start the trip.
     *
     * The server refuses every piece of stop work until this lands, so it is the
     * one thing on offer while the trip is still merely assigned.
     */
    fun startTrip() {
        val trip = _state.value.trip ?: return
        val action = tripActionFor(trip) ?: return
        val occurredAtMillis = clock.now().toEpochMilliseconds()

        _state.value = _state.value.copy(trip = trip.applyLocally(action), errorMessage = null)

        viewModelScope.launch {
            val result = recorder.record(
                tripId = trip.id,
                eventType = action.event,
                occurredAtMillis = occurredAtMillis,
            )
            when (result) {
                is AppResult.Success -> analytics.track(
                    AnalyticsEvent.tripStarted(
                        tripId = trip.id,
                        stopCount = trip.stops.size,
                        minutesLate = trip.scheduledAtMillis
                            ?.let { AnalyticsEvent.minutesBetween(it, occurredAtMillis) },
                    ),
                )
                is AppResult.Failure -> {
                    analytics.track(AnalyticsEvent.tripActionFailed(trip.id, action.event.wire, result.error))
                    _state.value = _state.value.copy(trip = trip, errorMessage = result.error.message)
                }
            }
        }
    }

    /**
     * Record what the driver just did.
     *
     * The timestamp is taken here, at the tap, and travels with the queued write
     * as `occurredAt`. It is never refreshed on a retry: a driver who arrives in
     * a dead zone and syncs an hour later arrived when they arrived.
     *
     * The screen advances immediately on the strength of the local write. That is
     * not optimism about the network, it is the point of the queue: the write is
     * already durable, and offline there is no confirmation coming to wait for.
     */
    fun record(stopId: String, action: StopAction) {
        val trip = _state.value.trip ?: return
        val stop = trip.stops.firstOrNull { it.id == stopId } ?: return
        // Checked against the state as it is now, not as the button was drawn: a double tap lands
        // twice before the screen recomposes, and the second, out-of-order event would be queued,
        // refused hours later, and dropped. The photo rule is the screen's to enforce.
        if (action !in actionsFor(trip, stop, requirePhoto = false)) return
        val occurredAtMillis = clock.now().toEpochMilliseconds()

        val updated = trip.applyLocally(stopId, action, occurredAtMillis)
        _state.value = _state.value.copy(trip = updated, errorMessage = null)

        viewModelScope.launch {
            val result = recorder.record(
                tripId = tripId,
                eventType = action.event,
                stopId = stopId,
                occurredAtMillis = occurredAtMillis,
            )
            when (result) {
                is AppResult.Success -> analytics.track(
                    AnalyticsEvent.stopActionRecorded(
                        tripId = tripId,
                        stopId = stopId,
                        action = action.event.wire,
                        stopType = stop.stopType,
                        stopSequence = stop.sequenceNumber,
                        stopsRemaining = updated.stops.count { it.status in UNFINISHED },
                        // Only an arrival has a planned time to be late against.
                        minutesLate = stop.plannedAtMillis
                            ?.takeIf { action == StopAction.ARRIVE }
                            ?.let { AnalyticsEvent.minutesBetween(it, occurredAtMillis) },
                    ),
                )
                // Only a failure to persist reaches here, and it is the one the driver
                // must see: nothing was saved, so the screen must not keep pretending.
                is AppResult.Failure -> {
                    analytics.track(AnalyticsEvent.tripActionFailed(tripId, action.event.wire, result.error))
                    _state.value = _state.value.copy(
                        trip = trip,
                        errorMessage = result.error.message,
                    )
                }
            }
        }
    }
}

/** What a failed photo is called in analytics: it is not a trip event, so it has no wire name. */
private const val PHOTO_PROOF = "photo_proof"

/** Stops the driver still has work at. */
private val UNFINISHED = setOf(StopStatus.PENDING, StopStatus.ARRIVED)
