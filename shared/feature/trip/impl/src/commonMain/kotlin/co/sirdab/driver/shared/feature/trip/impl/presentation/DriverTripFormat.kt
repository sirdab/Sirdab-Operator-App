package co.sirdab.driver.shared.feature.trip.impl.presentation

import co.sirdab.driver.shared.feature.trip.api.ExceptionKind
import co.sirdab.driver.shared.feature.trip.api.ExceptionSeverity
import co.sirdab.driver.shared.core.model.StopStatus
import co.sirdab.driver.shared.core.model.StopType
import co.sirdab.driver.shared.core.model.TripLifecycle
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.ek_delay
import co.sirdab.driver.shared.core.ui.generated.resources.ek_damage
import co.sirdab.driver.shared.core.ui.generated.resources.ek_refused
import co.sirdab.driver.shared.core.ui.generated.resources.ek_access_denied
import co.sirdab.driver.shared.core.ui.generated.resources.ek_vehicle_breakdown
import co.sirdab.driver.shared.core.ui.generated.resources.ek_wrong_address
import co.sirdab.driver.shared.core.ui.generated.resources.es_low
import co.sirdab.driver.shared.core.ui.generated.resources.es_medium
import co.sirdab.driver.shared.core.ui.generated.resources.es_high
import co.sirdab.driver.shared.core.ui.generated.resources.sact_arrive
import co.sirdab.driver.shared.core.ui.generated.resources.sact_deliver
import co.sirdab.driver.shared.core.ui.generated.resources.sact_depart
import co.sirdab.driver.shared.core.ui.generated.resources.sact_finish_loading
import co.sirdab.driver.shared.core.ui.generated.resources.sact_start_loading
import co.sirdab.driver.shared.core.ui.generated.resources.sst_arrived
import co.sirdab.driver.shared.core.ui.generated.resources.sst_completed
import co.sirdab.driver.shared.core.ui.generated.resources.sst_departed
import co.sirdab.driver.shared.core.ui.generated.resources.sst_pending
import co.sirdab.driver.shared.core.ui.generated.resources.sst_skipped
import co.sirdab.driver.shared.core.ui.generated.resources.tl_assigned
import co.sirdab.driver.shared.core.ui.generated.resources.tl_cancelled
import co.sirdab.driver.shared.core.ui.generated.resources.tl_complete
import co.sirdab.driver.shared.core.ui.generated.resources.tl_created
import co.sirdab.driver.shared.core.ui.generated.resources.tl_failed
import co.sirdab.driver.shared.core.ui.generated.resources.tl_in_transit
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_dropoff
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_pickup
import co.sirdab.driver.shared.core.util.localizeDigits
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal fun TripLifecycle.label(): StringResource = when (this) {
    TripLifecycle.CREATED -> Res.string.tl_created
    TripLifecycle.ASSIGNED -> Res.string.tl_assigned
    TripLifecycle.IN_TRANSIT -> Res.string.tl_in_transit
    TripLifecycle.COMPLETE -> Res.string.tl_complete
    TripLifecycle.CANCELLED -> Res.string.tl_cancelled
    TripLifecycle.FAILED -> Res.string.tl_failed
}

internal fun TripLifecycle.tone(): ChipTone = when (this) {
    TripLifecycle.CREATED -> ChipTone.NEUTRAL
    TripLifecycle.ASSIGNED -> ChipTone.PRIMARY
    TripLifecycle.IN_TRANSIT -> ChipTone.WARNING
    TripLifecycle.COMPLETE -> ChipTone.SUCCESS
    TripLifecycle.CANCELLED, TripLifecycle.FAILED -> ChipTone.DANGER
}

internal fun StopStatus.label(): StringResource = when (this) {
    StopStatus.PENDING -> Res.string.sst_pending
    StopStatus.ARRIVED -> Res.string.sst_arrived
    StopStatus.DEPARTED -> Res.string.sst_departed
    StopStatus.COMPLETED -> Res.string.sst_completed
    StopStatus.SKIPPED -> Res.string.sst_skipped
}

internal fun StopStatus.tone(): ChipTone = when (this) {
    StopStatus.PENDING -> ChipTone.NEUTRAL
    StopStatus.ARRIVED -> ChipTone.WARNING
    StopStatus.DEPARTED -> ChipTone.PRIMARY
    StopStatus.COMPLETED -> ChipTone.SUCCESS
    StopStatus.SKIPPED -> ChipTone.DANGER
}

internal fun StopType.label(): StringResource = when (this) {
    StopType.PICKUP -> Res.string.trip_stop_pickup
    StopType.DROPOFF -> Res.string.trip_stop_dropoff
}

/** Day and time, in the device's zone. The API sends an offset, so the instant is unambiguous. */
@OptIn(ExperimentalTime::class)
internal fun formatStopTime(millis: Long, lang: String): String {
    val at = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    val hh = at.hour.toString().padStart(2, '0')
    val mm = at.minute.toString().padStart(2, '0')
    return "${at.dayOfMonth}/${at.monthNumber}  $hh:$mm".localizeDigits(lang)
}

internal fun StopAction.label(): StringResource = when (this) {
    StopAction.ARRIVE -> Res.string.sact_arrive
    StopAction.START_LOADING -> Res.string.sact_start_loading
    StopAction.FINISH_LOADING -> Res.string.sact_finish_loading
    StopAction.DEPART -> Res.string.sact_depart
    StopAction.DELIVER -> Res.string.sact_deliver
}

internal fun ExceptionKind.label(): StringResource = when (this) {
    ExceptionKind.DELAY -> Res.string.ek_delay
    ExceptionKind.DAMAGE -> Res.string.ek_damage
    ExceptionKind.REFUSED -> Res.string.ek_refused
    ExceptionKind.ACCESS_DENIED -> Res.string.ek_access_denied
    ExceptionKind.VEHICLE_BREAKDOWN -> Res.string.ek_vehicle_breakdown
    ExceptionKind.WRONG_ADDRESS -> Res.string.ek_wrong_address
}

internal fun ExceptionSeverity.label(): StringResource = when (this) {
    ExceptionSeverity.LOW -> Res.string.es_low
    ExceptionSeverity.MEDIUM -> Res.string.es_medium
    ExceptionSeverity.HIGH -> Res.string.es_high
}

internal fun ExceptionSeverity.tone(): ChipTone = when (this) {
    ExceptionSeverity.LOW -> ChipTone.NEUTRAL
    ExceptionSeverity.MEDIUM -> ChipTone.WARNING
    ExceptionSeverity.HIGH -> ChipTone.DANGER
}
