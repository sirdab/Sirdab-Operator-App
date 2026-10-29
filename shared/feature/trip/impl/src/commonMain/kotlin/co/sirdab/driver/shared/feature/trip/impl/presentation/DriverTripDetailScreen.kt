package co.sirdab.driver.shared.feature.trip.impl.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.core.model.TripLeg
import co.sirdab.driver.shared.core.model.TripStop
import co.sirdab.driver.shared.core.media.CapturedImage
import co.sirdab.driver.shared.core.media.PhotoSource
import co.sirdab.driver.shared.core.media.rememberPhotoCapture
import co.sirdab.driver.shared.core.platform.dialer.PhoneDialer
import co.sirdab.driver.shared.core.platform.maps.MapLauncher
import co.sirdab.driver.shared.core.platform.locale.AppLocale
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.TagChip
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.common_back
import co.sirdab.driver.shared.core.ui.generated.resources.common_ok
import co.sirdab.driver.shared.core.ui.generated.resources.trip_dropped_sync
import co.sirdab.driver.shared.core.ui.generated.resources.trip_dropped_sync_note
import co.sirdab.driver.shared.core.ui.generated.resources.trip_detail_title
import co.sirdab.driver.shared.core.ui.generated.resources.trip_document_failed
import co.sirdab.driver.shared.core.ui.generated.resources.trip_document_download
import co.sirdab.driver.shared.core.ui.generated.resources.trip_documents
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_arrived
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_call
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_navigate
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_departed
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_planned
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stops
import co.sirdab.driver.shared.core.ui.generated.resources.trips_in_city
import co.sirdab.driver.shared.core.ui.generated.resources.trips_retry
import co.sirdab.driver.shared.core.ui.generated.resources.exc_report
import co.sirdab.driver.shared.core.ui.generated.resources.exc_sent
import co.sirdab.driver.shared.core.ui.generated.resources.trip_pending_sync
import co.sirdab.driver.shared.core.ui.generated.resources.trip_pending_sync_note
import co.sirdab.driver.shared.core.ui.generated.resources.trips_stops_count
import co.sirdab.driver.shared.core.ui.generated.resources.trip_start
import co.sirdab.driver.shared.core.ui.generated.resources.stop_needs_photo
import co.sirdab.driver.shared.core.ui.generated.resources.stop_take_photo
import co.sirdab.driver.shared.core.ui.generated.resources.stop_choose_photo
import co.sirdab.driver.shared.core.ui.generated.resources.stop_photo_sending
import co.sirdab.driver.shared.core.ui.generated.resources.stop_photo_count
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.core.util.localizeDigits
import co.sirdab.driver.shared.feature.trip.api.ProofType
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverTripDetailScreen(
    tripId: String,
    onBack: () -> Unit,
    viewModel: DriverTripDetailViewModel = koinViewModel { parametersOf(tripId) },
    documents: TripDocumentsViewModel = koinViewModel { parametersOf(tripId) },
    dialer: PhoneDialer = koinInject(),
    maps: MapLauncher = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    val pending by viewModel.pendingWrites.collectAsState()
    val documentState by documents.state.collectAsState()
    val lang = AppLocale.current()
    val trip = state.trip

    // The launcher is created once and reused, so which stop a photo belongs to
    // travels beside it rather than inside a launcher per row.
    // Saveable: the camera is another app, and a low-memory phone often kills this process while
    // it is open. Held in plain remember, the photo came back to a screen that no longer knew which
    // stop it was for, and was dropped without a word.
    var photoFor by rememberSaveable { mutableStateOf<String?>(null) }
    val capture = rememberPhotoCapture { image: CapturedImage ->
        photoFor?.let { stopId -> viewModel.attachPhoto(stopId, image.bytes, image.contentType) }
        photoFor = null
    }

    // The delivery note has a launcher of its own, so a note can never be filed as the stop's
    // photo or the other way round. Saveable for the same reason as the photo: what comes back
    // from the camera is a delivery, and it must still know which stop it closes.
    var askingNoteFor by rememberSaveable { mutableStateOf<String?>(null) }
    var noteFor by rememberSaveable { mutableStateOf<String?>(null) }
    val noteCapture = rememberPhotoCapture { image: CapturedImage ->
        noteFor?.let { stopId -> viewModel.deliverWithNote(stopId, image.bytes, image.contentType) }
        noteFor = null
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.common_back),
                )
            }
            Text(
                trip?.reference ?: stringResource(Res.string.trip_detail_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        // A trip moves under the driver — a stop resequenced, an exception answered by
        // dispatch — and pulling is how they ask whether it has. The push happens first, so a
        // proof still in the queue is sent before the server is asked what it knows.
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                trip == null -> Box(
                    // Scrollable so the pull gesture has something to grab when the read failed
                    // and there is nothing on screen, which is when a driver most wants to pull.
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            state.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        OutlinedButton(onClick = { viewModel.load() }) {
                            Text(stringResource(Res.string.trips_retry))
                        }
                    }
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (state.droppedWrites > 0) {
                        item { DroppedBanner(state.droppedWrites, lang, viewModel::acknowledgeDropped) }
                    }

                    if (pending > 0) {
                        item { PendingBanner(pending, lang) }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            TagChip(stringResource(trip.status.label()), tone = trip.status.tone())
                            TagChip(stringResource(Res.string.trips_stops_count, trip.stopCount))
                            if (trip.inCity) {
                                TagChip(stringResource(Res.string.trips_in_city), tone = ChipTone.PRIMARY)
                            }
                        }
                    }

                    if (tripActionFor(trip) != null) {
                        item {
                            Button(
                                onClick = viewModel::startTrip,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(Res.string.trip_start))
                            }
                        }
                    }

                    // Before the problem button, because the slip is what the gate asks for
                    // first. Only legs whose order came with paperwork are listed.
                    val withDocuments = trip.legs.filter { it.documents.isNotEmpty() }.sortedBy { it.sequence }
                    if (withDocuments.isNotEmpty()) {
                        item {
                            DocumentsCard(
                                trip = trip,
                                legs = withDocuments,
                                lang = lang,
                                state = documentState,
                                onOpen = documents::open,
                            )
                        }
                    }

                    item {
                        if (state.exceptionReported) {
                            Text(
                                stringResource(Res.string.exc_sent),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.Green.c600,
                            )
                            Spacer(Modifier.height(Spacing.xs))
                        }
                        // Always reachable: a problem can happen at any point, and a
                        // driver should never have to find the right stop first.
                        OutlinedButton(
                            onClick = viewModel::openExceptionReport,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.exc_report))
                        }
                    }

                    item {
                        Text(
                            stringResource(Res.string.trip_stops),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    items(trip.stops, key = { it.id }) { stop ->
                        val sending = viewModel.pendingProofs(stop.id).collectAsState().value
                        StopRow(
                            stop = stop,
                            lang = lang,
                            isCurrent = stop.id == trip.currentStop?.id,
                            isLast = stop.id == trip.stops.lastOrNull()?.id,
                            actions = actionsFor(trip, stop, queuedPhotos = sending),
                            awaitingPhoto = awaitingPhotoProof(trip, stop, queuedPhotos = sending),
                            sendingPhotos = sending,
                            onTakePhoto = { source ->
                                viewModel.photoCaptureStarted(stop.id, source)
                                photoFor = stop.id
                                capture.launch(source)
                            },
                            onCall = { phone ->
                                viewModel.contactCalled(stop)
                                dialer.dial(phone)
                            },
                            onNavigate = { lat, lng ->
                                viewModel.navigationOpened(stop)
                                maps.navigateTo(lat, lng, stop.address.label)
                            },
                            onAction = { action ->
                                if (action.requiresDeliveryNote) {
                                    askingNoteFor = stop.id
                                } else {
                                    viewModel.record(stop.id, action)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    askingNoteFor?.let { stopId ->
        DeliveryNoteSheet(
            onDismiss = { askingNoteFor = null },
            onCapture = { source ->
                viewModel.photoCaptureStarted(stopId, source, ProofType.DOCUMENT)
                noteFor = stopId
                askingNoteFor = null
                noteCapture.launch(source)
            },
        )
    }

    if (state.isReportingException) {
        ReportExceptionSheet(
            onDismiss = viewModel::dismissExceptionReport,
            onSubmit = { kind, severity, note ->
                viewModel.reportException(kind, severity, note)
            },
        )
    }
}

/**
 * One stop, drawn as a rail entry.
 *
 * The rail carries the running order, which is the one thing a driver has to read at a glance: a
 * numbered dot per stop and a connector down to the next. The stop being worked now is the only
 * one outlined, so the eye lands on it without reading a single word.
 */
@Composable
private fun StopRow(
    stop: TripStop,
    lang: String,
    isCurrent: Boolean,
    isLast: Boolean,
    actions: List<StopAction>,
    awaitingPhoto: Boolean,
    sendingPhotos: Int,
    onTakePhoto: (PhotoSource) -> Unit,
    onCall: (String) -> Unit,
    onNavigate: (Double, Double) -> Unit,
    onAction: (StopAction) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(32.dp)) {
            Surface(
                shape = CircleShape,
                color = if (isCurrent) AppColors.Primary.c700 else AppColors.Gray.c100,
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        stop.sequenceNumber.toString().localizeDigits(lang),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) AppColors.Gray.c50 else AppColors.Gray.c700,
                    )
                }
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(64.dp)
                        .background(AppColors.Gray.c100),
                )
            }
        }

        Spacer(Modifier.width(Spacing.sm))

        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = if (isCurrent) 3.dp else 1.dp,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        ) {
            Column(Modifier.padding(Spacing.md)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TagChip(
                        stringResource(stop.stopType.label()),
                        tone = ChipTone.PRIMARY,
                    )
                    TagChip(stringResource(stop.status.label()), tone = stop.status.tone())
                }

                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stop.address.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stop.address.addressLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                stop.address.districtLine?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                stop.address.city?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Planned is what the driver is held to; arrived and departed are what actually
                // happened. Showing all three is how a late stop explains itself.
                StopTime(Res.string.trip_stop_planned, stop.plannedAtMillis, lang)
                StopTime(Res.string.trip_stop_arrived, stop.arrivedAtMillis, lang)
                StopTime(Res.string.trip_stop_departed, stop.departedAtMillis, lang)

                // Getting there and asking where to go once you are: the two things a driver
                // does with a stop before working it. A pin only exists when someone put one on
                // the address, so the first button comes and goes with it.
                val pin = stop.address.lat to stop.address.lng
                if (pin.first != null || stop.contact != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        val (lat, lng) = pin
                        if (lat != null && lng != null) {
                            OutlinedButton(onClick = { onNavigate(lat, lng) }) {
                                Icon(
                                    Icons.Default.Directions,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(Spacing.xs))
                                Text(stringResource(Res.string.trip_stop_navigate))
                            }
                        }
                        stop.contact?.let { contact ->
                            OutlinedButton(onClick = { onCall(contact.phone) }) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(Spacing.xs))
                                Text("${stringResource(Res.string.trip_stop_call)}  ${contact.name}")
                            }
                        }
                    }
                }

                if (stop.photoProofCount > 0) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(
                            Res.string.stop_photo_count,
                            stop.photoProofCount.toString().localizeDigits(lang),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Green.c600,
                    )
                }

                if (!awaitingPhoto && sendingPhotos > 0) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(Res.string.stop_photo_sending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (awaitingPhoto) {
                    Spacer(Modifier.height(Spacing.sm))
                    // A photo on its way is not a photo the server has, and the
                    // action stays locked until it is. Saying so is the
                    // difference between a screen that is waiting and one that
                    // looks broken.
                    Text(
                        stringResource(
                            if (sendingPhotos > 0) {
                                Res.string.stop_photo_sending
                            } else {
                                Res.string.stop_needs_photo
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sendingPhotos > 0) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Button(onClick = { onTakePhoto(PhotoSource.CAMERA) }) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(stringResource(Res.string.stop_take_photo))
                        }
                        OutlinedButton(onClick = { onTakePhoto(PhotoSource.LIBRARY) }) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(stringResource(Res.string.stop_choose_photo))
                        }
                    }
                }

                if (actions.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        // The optional detail sits before the action that moves the
                        // trip on, so the filled button is the last thing read.
                        actions.sortedBy { it.primary }.forEach { action ->
                            if (action.primary) {
                                Button(onClick = { onAction(action) }) {
                                    Text(stringResource(action.label()))
                                }
                            } else {
                                OutlinedButton(onClick = { onAction(action) }) {
                                    Text(stringResource(action.label()))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopTime(
    label: org.jetbrains.compose.resources.StringResource,
    millis: Long?,
    lang: String,
) {
    if (millis == null) return
    Spacer(Modifier.height(Spacing.xxs))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(formatStopTime(millis, lang), style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Work the driver has recorded that has not reached the server.
 *
 * Shown because the alternative is worse: the stop advances the moment they tap,
 * so with no indicator a driver in a dead zone cannot tell a saved delivery from
 * a sent one, and has no reason to trust either.
 */
@Composable
private fun PendingBanner(count: Int, lang: String) {
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = AppColors.Amber.c50,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                stringResource(Res.string.trip_pending_sync, count).localizeDigits(lang),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.Amber.c800,
            )
            Text(
                stringResource(Res.string.trip_pending_sync_note),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Amber.c800,
            )
        }
    }
}

/**
 * Writes the server refused for good.
 *
 * They are gone from the queue, so the pending count fell as if they had been
 * sent; this is the only thing telling the driver that one of their taps did
 * not count. It stays until they dismiss it.
 */
@Composable
private fun DroppedBanner(count: Int, lang: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = AppColors.Red.c50,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                stringResource(Res.string.trip_dropped_sync, count).localizeDigits(lang),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.Red.c800,
            )
            Text(
                stringResource(Res.string.trip_dropped_sync_note),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Red.c800,
            )
            OutlinedButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(Res.string.common_ok))
            }
        }
    }
}

/**
 * The paperwork of the orders on this trip, as the system that sent them holds it: the slip the
 * gate asks for at a pickup, the one the receiver signs at a drop-off.
 *
 * Grouped under each leg's two stops only when the trip carries more than one order, so a driver
 * with two loads on board can tell whose slip is whose. A trip with one is just its documents.
 */
@Composable
private fun DocumentsCard(
    trip: DriverTrip,
    legs: List<TripLeg>,
    lang: String,
    state: TripDocumentsUiState,
    onOpen: (legId: String, key: String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(Res.string.trip_documents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            legs.forEach { leg ->
                if (legs.size > 1) LegRoute(trip, leg)
                leg.documents.forEach { document ->
                    val ref = DocumentRef(leg.id, document.key)
                    DocumentRow(
                        name = document.name(lang),
                        downloading = state.downloading == ref,
                        enabled = state.downloading == null,
                        onClick = { onOpen(leg.id, document.key) },
                    )
                    state.failure?.takeIf { it.document == ref }?.let { failure ->
                        Text(
                            stringResource(failure.reason?.labelRes() ?: Res.string.trip_document_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** Where a leg starts and ends, by its stops' names. An arrow that turns round for Arabic. */
@Composable
private fun LegRoute(trip: DriverTrip, leg: TripLeg) {
    val stops = trip.stops.filter { it.legId == leg.id }
    val from = stops.firstOrNull()?.address?.label ?: return
    val to = stops.lastOrNull()?.address?.label ?: return
    Row(
        Modifier.padding(top = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(from, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(to, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One document: what the sender calls it, and a download mark that turns into a spinner while it
 * comes. Every other row waits meanwhile, since only one comes at a time.
 */
@Composable
private fun DocumentRow(name: String, downloading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = AppColors.Red.c600,
                modifier = Modifier.size(24.dp),
            )
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (downloading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Default.Download,
                    contentDescription = stringResource(Res.string.trip_document_download),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
