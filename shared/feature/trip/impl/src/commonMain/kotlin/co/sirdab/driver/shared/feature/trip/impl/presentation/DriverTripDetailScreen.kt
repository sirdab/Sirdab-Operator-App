package co.sirdab.driver.shared.feature.trip.impl.presentation

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.model.TripStop
import co.sirdab.driver.shared.core.media.CapturedImage
import co.sirdab.driver.shared.core.media.PhotoSource
import co.sirdab.driver.shared.core.media.rememberPhotoCapture
import co.sirdab.driver.shared.core.platform.dialer.PhoneDialer
import co.sirdab.driver.shared.core.platform.locale.AppLocale
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.TagChip
import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.common_back
import co.sirdab.driver.shared.core.ui.generated.resources.trip_detail_title
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_arrived
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_call
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_departed
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_planned
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stops
import co.sirdab.driver.shared.core.ui.generated.resources.trips_in_city
import co.sirdab.driver.shared.core.ui.generated.resources.trips_retry
import co.sirdab.driver.shared.core.ui.generated.resources.exc_report
import co.sirdab.driver.shared.core.ui.generated.resources.exc_sent
import co.sirdab.driver.shared.core.ui.generated.resources.trip_pending_sync
import co.sirdab.driver.shared.core.ui.generated.resources.exc_report
import co.sirdab.driver.shared.core.ui.generated.resources.exc_sent
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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DriverTripDetailScreen(
    tripId: String,
    onBack: () -> Unit,
    viewModel: DriverTripDetailViewModel = koinViewModel { parametersOf(tripId) },
    dialer: PhoneDialer = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    val pending by viewModel.pendingWrites.collectAsState()
    val lang = AppLocale.current()
    val trip = state.trip

    // The launcher is created once and reused, so which stop a photo belongs to
    // travels beside it rather than inside a launcher per row.
    var photoFor by remember { mutableStateOf<String?>(null) }
    val capture = rememberPhotoCapture { image: CapturedImage ->
        photoFor?.let { stopId -> viewModel.attachPhoto(stopId, image.bytes, image.contentType) }
        photoFor = null
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

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            trip == null -> Box(
                Modifier.fillMaxSize().padding(Spacing.lg),
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
                    OutlinedButton(onClick = viewModel::load) {
                        Text(stringResource(Res.string.trips_retry))
                    }
                }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
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

                tripActionFor(trip)?.let { action ->
                    item {
                        Button(
                            onClick = viewModel::startTrip,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.trip_start))
                        }
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
                    StopRow(
                        stop = stop,
                        lang = lang,
                        isCurrent = stop.id == trip.currentStop?.id,
                        isLast = stop.id == trip.stops.lastOrNull()?.id,
                        actions = actionsFor(trip, stop),
                        awaitingPhoto = awaitingPhotoProof(trip, stop),
                        sendingPhotos = viewModel.pendingProofs(stop.id).collectAsState().value,
                        onTakePhoto = { source ->
                            photoFor = stop.id
                            capture.launch(source)
                        },
                        onCall = dialer::dial,
                        onAction = { action -> viewModel.record(stop.id, action) },
                    )
                }
            }
        }
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

                stop.contact?.let { contact ->
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedButton(onClick = { onCall(contact.phone) }) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("${stringResource(Res.string.trip_stop_call)}  ${contact.name}")
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
