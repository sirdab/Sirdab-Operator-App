package co.sirdab.driver.shared.core.analytics

import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.DriverDocumentKind
import co.sirdab.driver.shared.core.model.DriverPosting
import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.core.model.EquipmentBody
import co.sirdab.driver.shared.core.model.EquipmentSize
import co.sirdab.driver.shared.core.model.EquipmentTemperature
import co.sirdab.driver.shared.core.model.Nationality
import co.sirdab.driver.shared.core.model.StopStatus
import co.sirdab.driver.shared.core.model.StopType
import co.sirdab.driver.shared.core.model.TripLifecycle
import co.sirdab.driver.shared.core.model.TripStop

/**
 * One thing that happened, as Mixpanel sees it.
 *
 * Every event the app sends is built here, so the project has one vocabulary and this file is its
 * catalogue. Names are Title Case "Object Action", property keys are snake_case, and enum values
 * travel as the wire strings the TMS uses, so an event can be lined up against the server's rows.
 *
 * Ids only. Nothing here carries a name, phone number, national ID, licence number or plate:
 * Mixpanel is a third party, and the driver gave those to the TMS, not to it. The one exception is
 * the distinct id itself, which is the driver's phone number (see `AnalyticsSession`).
 */
data class AnalyticsEvent(val name: String, val properties: Map<String, Any> = emptyMap()) {

    companion object {

        // App

        /** A cold start, and which of the app's worlds it opened in (`sign_in`, `main`, ...). */
        fun appOpened(destination: String) = event("App Opened", "destination" to destination)

        /** [tripId] only for the trip screen, which is one screen per trip. */
        fun screenViewed(screen: String, tripId: String? = null) =
            event("Screen Viewed", "screen" to screen, "trip_id" to tripId)

        /** [source] is `welcome` or `profile`. */
        fun languageSelected(language: String, source: String) =
            event("Language Selected", "language" to language, "source" to source)

        // Sign-in

        fun otpRequested(resend: Boolean) = event("OTP Requested", "resend" to resend)

        fun otpRequestFailed(resend: Boolean, error: AppError) =
            event("OTP Request Failed", "resend" to resend, *error.properties())

        /** A code accepted, and where the profile sent the driver from there. */
        fun signedIn(destination: String) = event("Signed In", "destination" to destination)

        fun signInFailed(error: AppError) = event("Sign In Failed", *error.properties())

        /**
         * The driver asking to leave. [unsentWrites] is the work that goes with the session: a
         * driver signing out in a dead zone loses it, so how often that is non-zero matters.
         *
         * [source] is `profile` or `blocked`.
         */
        fun signOutRequested(source: String, unsentWrites: Int? = null) =
            event("Sign Out Requested", "source" to source, "unsent_writes" to unsentWrites)

        /**
         * A session ending, for any reason: signing out, deleting the account, or a refresh token
         * the server refused. Sent by `AnalyticsSession`, the one place that sees all of them,
         * just before the install forgets who it was.
         */
        fun signedOut() = event("Signed Out")

        // Sign-up

        fun detailsSaved(nationality: Nationality?) =
            event("Details Saved", "nationality" to nationality?.wire)

        fun detailsSaveFailed(error: AppError) = event("Details Save Failed", *error.properties())

        fun routesSaved(routeCount: Int) = event("Routes Saved", "route_count" to routeCount)

        fun routesSaveFailed(error: AppError) = event("Routes Save Failed", *error.properties())

        fun truckAdded(
            body: EquipmentBody,
            size: EquipmentSize,
            temperature: EquipmentTemperature,
            truckCount: Int,
        ) = event(
            "Truck Added",
            "body_type" to body.wire,
            "size_class" to size.wire,
            "temperature" to temperature.wire,
            "truck_count" to truckCount,
        )

        fun truckAddFailed(error: AppError) = event("Truck Add Failed", *error.properties())

        fun truckRemoved(truckCount: Int) = event("Truck Removed", "truck_count" to truckCount)

        fun truckRemoveFailed(error: AppError) = event("Truck Remove Failed", *error.properties())

        /** [replacedRejected]: this photo answers one ops sent back. */
        fun documentUploaded(kind: DriverDocumentKind, replacedRejected: Boolean) = event(
            "Document Uploaded",
            "document_kind" to kind.wire,
            "replaced_rejected" to replacedRejected,
        )

        fun documentUploadFailed(kind: DriverDocumentKind, error: AppError) =
            event("Document Upload Failed", "document_kind" to kind.wire, *error.properties())

        /**
         * The server has nothing left to ask for.
         *
         * [returning] is a driver who finished sign-up before and came back to replace a document
         * ops rejected, so first-time completion is `returning = false`.
         */
        fun signUpCompleted(destination: String, returning: Boolean) = event(
            "Sign Up Completed",
            "destination" to destination,
            "returning" to returning,
        )

        // Board

        /**
         * The board read, on opening the tab or on a pull ([pulled]).
         *
         * [truckCount] 0 is a driver who can see postings but cannot bid on any: an offer has to
         * commit a truck.
         */
        fun boardLoaded(
            postings: List<DriverPosting>,
            hasMore: Boolean,
            myBidCount: Int,
            truckCount: Int,
            pulled: Boolean,
        ) = event(
            "Board Loaded",
            "posting_count" to postings.size,
            "fixed_rate_count" to postings.count { it.isFixedRate },
            "my_bid_count" to myBidCount,
            "truck_count" to truckCount,
            "has_more" to hasMore,
            "pulled" to pulled,
        )

        /**
         * Not every one of these is a fault, and the driver sees these three as an empty board
         * rather than an error: `error_reason` `unavailable` is the board not built yet
         * server-side, `not_provisioned` an independent driver nobody posts to, and
         * `not_a_bidder` a company driver whose company does the bidding.
         */
        fun boardLoadFailed(pulled: Boolean, error: AppError) =
            event("Board Load Failed", "pulled" to pulled, *error.properties())

        /** The driver scrolled past the first page. [totalCount] is the board's length after it. */
        fun morePostingsLoaded(pageSize: Int, totalCount: Int, hasMore: Boolean) = event(
            "More Postings Loaded",
            "page_size" to pageSize,
            "total_count" to totalCount,
            "has_more" to hasMore,
        )

        fun morePostingsLoadFailed(error: AppError) =
            event("More Postings Load Failed", *error.properties())

        /** [editing] is the sheet opened on the driver's own pending bid, to reprice it. */
        fun bidSheetOpened(posting: DriverPosting, truckCount: Int, nowMillis: Long, editing: Boolean = false) = event(
            "Bid Sheet Opened",
            "posting_id" to posting.id,
            *posting.properties(nowMillis),
            "truck_count" to truckCount,
            "editing" to editing,
        )

        /**
         * The sheet closed without an offer going out. [afterFailure] is a driver giving up on a
         * bid the server refused, rather than one who changed their mind.
         */
        fun bidSheetDismissed(postingId: String, afterFailure: Boolean) = event(
            "Bid Sheet Dismissed",
            "posting_id" to postingId,
            "after_failure" to afterFailure,
        )

        /** [posting] is null only when the board no longer holds it, and then only the ids go. */
        fun bidPlaced(
            postingId: String,
            bidId: String,
            amountSar: Int,
            hasNote: Boolean,
            posting: DriverPosting?,
            nowMillis: Long,
        ) = event(
            "Bid Placed",
            "posting_id" to postingId,
            *posting?.properties(nowMillis).orEmpty(),
            "bid_id" to bidId,
            "amount_sar" to amountSar,
            "has_note" to hasNote,
        )

        fun bidFailed(
            postingId: String,
            amountSar: Int,
            error: AppError,
            posting: DriverPosting?,
            nowMillis: Long,
        ) = event(
            "Bid Failed",
            "posting_id" to postingId,
            *posting?.properties(nowMillis).orEmpty(),
            "amount_sar" to amountSar,
            *error.properties(),
        )

        /**
         * A pending bid repriced. [matched] is the driver taking "match" on a competing bid.
         * [lowestCompetingSar] is what the server answered with, so `still_undercut` says whether
         * the new price beat everyone else's.
         */
        fun bidUpdated(
            postingId: String,
            bidId: String,
            amountSar: Int,
            previousAmountSar: Int,
            matched: Boolean,
            lowestCompetingSar: Int?,
            posting: DriverPosting?,
            nowMillis: Long,
        ) = event(
            "Bid Updated",
            "posting_id" to postingId,
            *posting?.properties(nowMillis).orEmpty(),
            "bid_id" to bidId,
            "amount_sar" to amountSar,
            "previous_amount_sar" to previousAmountSar,
            "matched" to matched,
            "lowest_competing_sar" to lowestCompetingSar,
            "still_undercut" to (lowestCompetingSar != null && lowestCompetingSar < amountSar),
        )

        fun bidUpdateFailed(
            postingId: String,
            bidId: String,
            amountSar: Int,
            error: AppError,
            posting: DriverPosting?,
            nowMillis: Long,
        ) = event(
            "Bid Update Failed",
            "posting_id" to postingId,
            *posting?.properties(nowMillis).orEmpty(),
            "bid_id" to bidId,
            "amount_sar" to amountSar,
            *error.properties(),
        )

        // Trips

        /** The trips list read, on opening the tab or on a pull ([pulled]). */
        fun tripsLoaded(trips: List<DriverTrip>, hasMore: Boolean, pulled: Boolean) = event(
            "Trips Loaded",
            "trip_count" to trips.size,
            "not_started_count" to trips.count { it.status in NOT_STARTED },
            "in_transit_count" to trips.count { it.status == TripLifecycle.IN_TRANSIT },
            "has_more" to hasMore,
            "pulled" to pulled,
        )

        /** `error_reason` `not_provisioned` is a driver working independently, shown no trips. */
        fun tripsLoadFailed(pulled: Boolean, error: AppError) =
            event("Trips Load Failed", "pulled" to pulled, *error.properties())

        fun moreTripsLoaded(pageSize: Int, totalCount: Int, hasMore: Boolean) = event(
            "More Trips Loaded",
            "page_size" to pageSize,
            "total_count" to totalCount,
            "has_more" to hasMore,
        )

        fun moreTripsLoadFailed(error: AppError) = event("More Trips Load Failed", *error.properties())

        /** A trip tapped in the list. [position] counts from 0, top of the list. */
        fun tripOpened(trip: DriverTrip, position: Int) = event(
            "Trip Opened",
            "trip_id" to trip.id,
            "trip_status" to trip.status.wire(),
            "stop_count" to trip.stopCount,
            "in_city" to trip.inCity,
            "position" to position,
        )

        /** The trip screen could not read the trip, so the driver sees an error and a retry. */
        fun tripLoadFailed(tripId: String, error: AppError) =
            event("Trip Load Failed", "trip_id" to tripId, *error.properties())

        /** A pull on the trip screen: the driver checking whether the trip, or their own work, moved. */
        fun tripRefreshed(tripId: String, unsentWrites: Int) =
            event("Trip Refreshed", "trip_id" to tripId, "unsent_writes" to unsentWrites)

        /**
         * Recorded on the phone; the queue sends it whenever there is signal.
         *
         * [minutesLate] is against the trip's scheduled time, negative when early, and absent on a
         * trip with no schedule.
         */
        fun tripStarted(tripId: String, stopCount: Int, minutesLate: Int? = null) = event(
            "Trip Started",
            "trip_id" to tripId,
            "stop_count" to stopCount,
            "minutes_late" to minutesLate,
        )

        /**
         * [action] is the trip event's wire name: `arrived_at_stop`, `delivered`, ...
         *
         * [minutesLate] is an arrival against the stop's planned time, negative when early; the
         * other actions have nothing planned to compare with. [stopsRemaining] is the stops not
         * finished once this one lands, so a `delivered` with 0 left is the trip's last.
         */
        fun stopActionRecorded(
            tripId: String,
            stopId: String,
            action: String,
            stopType: StopType,
            stopSequence: Int,
            stopsRemaining: Int? = null,
            minutesLate: Int? = null,
        ) = event(
            "Stop Action Recorded",
            "trip_id" to tripId,
            "stop_id" to stopId,
            "action" to action,
            "stop_type" to stopType.name.lowercase(),
            "stop_sequence" to stopSequence,
            "stops_remaining" to stopsRemaining,
            "minutes_late" to minutesLate,
        )

        /** Directions to a stop, handed to the phone's maps app. */
        fun stopNavigationOpened(tripId: String, stop: TripStop) =
            event("Stop Navigation Opened", "trip_id" to tripId, *stop.properties())

        /** The stop's contact rung from the trip screen. The number itself is not sent. */
        fun stopContactCalled(tripId: String, stop: TripStop) =
            event("Stop Contact Called", "trip_id" to tripId, *stop.properties())

        /**
         * The camera or the photo library opened for a stop's proof. One with no [photoProofAdded]
         * after it is a driver who backed out, or a photo the phone could not keep.
         *
         * [source] is `camera` or `library`. [proofType] is the contract's: `photo` for the photo
         * that unlocks the step, `document` for the delivery note photographed as the POD.
         */
        fun photoProofStarted(tripId: String, stopId: String, source: String, proofType: String = "photo") = event(
            "Photo Proof Started",
            "trip_id" to tripId,
            "stop_id" to stopId,
            "source" to source,
            "proof_type" to proofType,
        )

        fun photoProofAdded(tripId: String, stopId: String, proofType: String = "photo") = event(
            "Photo Proof Added",
            "trip_id" to tripId,
            "stop_id" to stopId,
            "proof_type" to proofType,
        )

        /** [stopId] is the stop being worked when the sheet opened, if there is one. */
        fun exceptionReportOpened(tripId: String, stopId: String?) =
            event("Exception Report Opened", "trip_id" to tripId, "stop_id" to stopId)

        fun exceptionReportDismissed(tripId: String) =
            event("Exception Report Dismissed", "trip_id" to tripId)

        fun exceptionReported(
            tripId: String,
            stopId: String?,
            kind: String,
            severity: String,
            hasNote: Boolean,
        ) = event(
            "Exception Reported",
            "trip_id" to tripId,
            "stop_id" to stopId,
            "kind" to kind,
            "severity" to severity,
            "has_note" to hasNote,
        )

        /**
         * Something the driver did that the phone could not even save, so nothing will be sent.
         * [action] as in [stopActionRecorded], or `photo_proof`.
         */
        fun tripActionFailed(tripId: String, action: String, error: AppError) = event(
            "Trip Action Failed",
            "trip_id" to tripId,
            "action" to action,
            *error.properties(),
        )

        /**
         * A queued write the server refused for good, which the queue then threw away. On the
         * driver's screen it looked like it worked, so this is the only place it shows up.
         */
        fun queuedWriteRejected(reason: String) =
            event("Queued Write Rejected", "reason" to reason.take(MAX_TEXT))

        /** The driver dismissed the banner telling them [count] of their actions were refused. */
        fun rejectedWritesDismissed(tripId: String, count: Int) =
            event("Rejected Writes Dismissed", "trip_id" to tripId, "count" to count)

        // Profile

        fun fleetSwitched(workspaceId: String) = event("Fleet Switched", "workspace_id" to workspaceId)

        fun fleetSwitchFailed(error: AppError) = event("Fleet Switch Failed", *error.properties())

        /**
         * Sent before the call, while the session is still the driver's: a deletion that works
         * ends the session, and is followed by [signedOut] rather than an event of its own.
         */
        fun accountDeletionRequested() = event("Account Deletion Requested")

        fun accountDeletionFailed(error: AppError) =
            event("Account Deletion Failed", *error.properties())

        /** Enough of a server message to tell failures apart without shipping whole bodies. */
        private const val MAX_TEXT = 200

        private const val MINUTE_MILLIS = 60_000L

        private val NOT_STARTED = setOf(TripLifecycle.CREATED, TripLifecycle.ASSIGNED)

        private fun event(name: String, vararg properties: Pair<String, Any?>) = AnalyticsEvent(
            name = name,
            properties = buildMap {
                properties.forEach { (key, value) -> if (value != null) put(key, value) }
            },
        )

        /**
         * What a posting is, for lining offers up against lanes and prices. Cities only: the
         * labels can name a customer's site.
         */
        private fun DriverPosting.properties(nowMillis: Long): Array<Pair<String, Any?>> = arrayOf(
            "pricing" to if (isFixedRate) "fixed" else "open",
            "target_rate_sar" to targetRate?.amountSar,
            "bid_min_sar" to bidRange?.minSar,
            "bid_max_sar" to bidRange?.maxSar,
            "minutes_to_close" to biddingClosesAtMillis?.let { minutesBetween(nowMillis, it) },
            "distance_km" to distanceKm,
            "origin_city" to origin.city,
            "destination_city" to destination.city,
            "body_type" to bodyType?.wire,
            "size_class" to sizeClass?.wire,
            "temperature" to temperature?.wire,
        )

        private fun TripStop.properties(): Array<Pair<String, Any?>> = arrayOf(
            "stop_id" to id,
            "stop_type" to stopType.name.lowercase(),
            "stop_sequence" to sequenceNumber,
            "stop_status" to status.wire(),
        )

        private fun TripLifecycle.wire() = name.lowercase()

        private fun StopStatus.wire() = name.lowercase()

        /** Whole minutes from [fromMillis] to [toMillis], negative when [toMillis] is earlier. */
        fun minutesBetween(fromMillis: Long, toMillis: Long): Int =
            ((toMillis - fromMillis) / MINUTE_MILLIS).toInt()

        /** `other` for a failure with no named reason, which is most network ones. */
        private fun AppError.properties(): Array<Pair<String, Any?>> = arrayOf(
            "error_reason" to (reason?.name?.lowercase() ?: "other"),
            "error_message" to message.take(MAX_TEXT),
        )
    }
}
