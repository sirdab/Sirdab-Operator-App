package co.sirdab.driver.shared.feature.onboarding.impl.presentation.signup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.media.CapturedImage
import co.sirdab.driver.shared.core.media.PhotoSource
import co.sirdab.driver.shared.core.media.rememberPhotoCapture
import co.sirdab.driver.shared.core.platform.browser.UrlOpener
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverDocumentKind
import co.sirdab.driver.shared.core.model.DriverDocumentStatus
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.LanguageOptionRow
import co.sirdab.driver.shared.core.ui.components.StepProgress
import co.sirdab.driver.shared.core.ui.components.TagChip
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.doc_driving_licence
import co.sirdab.driver.shared.core.ui.generated.resources.doc_view
import co.sirdab.driver.shared.core.ui.generated.resources.doc_iqama
import co.sirdab.driver.shared.core.ui.generated.resources.doc_national_id_short
import co.sirdab.driver.shared.core.ui.generated.resources.doc_vehicle_registration
import co.sirdab.driver.shared.core.ui.generated.resources.signup_checklist_subtitle
import co.sirdab.driver.shared.core.ui.generated.resources.signup_checklist_title
import co.sirdab.driver.shared.core.ui.generated.resources.signup_item_done
import co.sirdab.driver.shared.core.ui.generated.resources.signup_item_in_review
import co.sirdab.driver.shared.core.ui.generated.resources.signup_item_rejected
import co.sirdab.driver.shared.core.ui.generated.resources.signup_identity
import co.sirdab.driver.shared.core.ui.generated.resources.signup_item_todo
import co.sirdab.driver.shared.core.ui.generated.resources.signup_progress
import co.sirdab.driver.shared.core.ui.generated.resources.signup_replace_document
import co.sirdab.driver.shared.core.ui.generated.resources.signup_section_docs
import co.sirdab.driver.shared.core.ui.generated.resources.signup_section_truck
import co.sirdab.driver.shared.core.ui.generated.resources.signup_section_you
import co.sirdab.driver.shared.core.ui.generated.resources.signup_unsupported
import co.sirdab.driver.shared.core.ui.generated.resources.signup_your_details
import co.sirdab.driver.shared.core.ui.generated.resources.stop_choose_photo
import co.sirdab.driver.shared.core.ui.generated.resources.stop_take_photo
import co.sirdab.driver.shared.core.ui.generated.resources.trips_retry
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverDestination
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverOnboardingRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfile
import co.sirdab.driver.shared.feature.onboarding.api.domain.OnboardingGap
import co.sirdab.driver.shared.feature.onboarding.api.domain.ProfileDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** One line of the checklist, and what tapping it does. */
sealed interface ChecklistItem {
    val done: Boolean

    /** Name and licence, filled in on their own screen. */
    data class Details(override val done: Boolean) : ChecklistItem

    /** At least one truck, added on its own screen. */
    data class Truck(override val done: Boolean, val plates: List<String>) : ChecklistItem

    /** A photograph, taken here. */
    data class Document(
        val kind: DriverDocumentKind,
        val truckId: String?,
        val plate: String?,
        val document: ProfileDocument?,
        override val done: Boolean,
        /** True while the driver has still to say which identity document they carry. */
        val identityChoice: Boolean = false,
    ) : ChecklistItem {
        val rejected: Boolean get() = document?.status == DriverDocumentStatus.REJECTED
        val approved: Boolean get() = document?.status == DriverDocumentStatus.APPROVED
    }

    /** Something the server wants that this build has never heard of. */
    data class Unsupported(val wire: String) : ChecklistItem {
        override val done: Boolean get() = false
    }
}

data class ChecklistUiState(
    val profile: DriverProfile? = null,
    val isLoading: Boolean = true,
    /** A re-read the driver asked for, which leaves the checklist on screen while it runs. */
    val isRefreshing: Boolean = false,
    val uploading: DriverDocumentKind? = null,
    val uploadingTruckId: String? = null,
    val errorMessage: String? = null,
    val errorReason: AppErrorReason? = null,
)

/**
 * The hub of sign-up: what is left, in whatever order the driver wants to do it.
 *
 * The list is the server's `missing`, not a local guess — every write answers with a fresh profile,
 * so an item disappears because the server says it is filled, never because the app assumed so.
 */
class SignUpChecklistViewModel(
    private val repository: DriverOnboardingRepository,
) : ViewModel() {

    /** Everything that is this screen's rather than the profile's. */
    private data class Local(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val uploading: DriverDocumentKind? = null,
        val uploadingTruckId: String? = null,
        val errorMessage: String? = null,
        val errorReason: AppErrorReason? = null,
    )

    private val local = MutableStateFlow(Local())

    /**
     * Read straight from the repository rather than copied into here.
     *
     * The details and truck screens write to the same profile and this one is underneath them on
     * the back stack; a private copy would show yesterday's checklist the moment they came back.
     */
    val state: StateFlow<ChecklistUiState> = combine(repository.profile, local) { profile, own ->
        ChecklistUiState(
            profile = profile,
            isLoading = own.isLoading,
            isRefreshing = own.isRefreshing,
            uploading = own.uploading,
            uploadingTruckId = own.uploadingTruckId,
            errorMessage = own.errorMessage,
            errorReason = own.errorReason,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ChecklistUiState(profile = repository.profile.value),
    )

    init {
        refresh()
    }

    /**
     * Read the profile again.
     *
     * [pulled] is the driver asking rather than the screen opening. Ops reviews a document
     * somewhere else entirely and nothing tells the phone, so this is how a driver finds out — and
     * the checklist has to stay on screen while they wait, not blank to a spinner.
     */
    fun refresh(pulled: Boolean = false) {
        local.value = local.value.copy(
            isLoading = !pulled && repository.profile.value == null,
            isRefreshing = pulled,
            errorMessage = null,
            errorReason = null,
        )
        viewModelScope.launch { finish(repository.refresh()) }
    }

    fun upload(item: ChecklistItem.Document, bytes: ByteArray, contentType: String) {
        local.value = local.value.copy(
            uploading = item.kind,
            uploadingTruckId = item.truckId,
            errorMessage = null,
            errorReason = null,
        )
        viewModelScope.launch {
            finish(repository.uploadDocument(item.kind, item.truckId, bytes, contentType))
        }
    }

    private fun finish(result: AppResult<DriverProfile>) {
        local.value = when (result) {
            is AppResult.Success -> Local(isLoading = false)
            is AppResult.Failure -> Local(
                isLoading = false,
                errorMessage = result.error.message,
                errorReason = result.error.reason,
            )
        }
    }
}

/**
 * Everything sign-up still wants, on one screen.
 *
 * Nothing here is a wizard step: a driver who has their licence to hand but not their truck papers
 * can do the licence now and the rest tonight, and the server is the one keeping score.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpChecklistScreen(
    onOpenDetails: () -> Unit,
    onOpenTruck: () -> Unit,
    onCompleted: (DriverDestination) -> Unit,
    viewModel: SignUpChecklistViewModel = koinViewModel(),
    urlOpener: UrlOpener = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    val profile = state.profile

    // Finishing is something the app is told, not something it decides: the server empties
    // `missing` and stamps onboardingCompletedAt, and the write that did it answers with both.
    //
    // Both conditions matter. A driver who finished weeks ago and came back to replace a rejected
    // document still has onboardingCompletedAt set, and bouncing them straight out again would
    // leave them no way to fix the thing they came here for.
    LaunchedEffect(profile?.missing, profile?.status) {
        if (profile != null && profile.missing.isEmpty() && profile.onboardingComplete) {
            onCompleted(profile.destination())
        }
    }

    val items = remember(profile) { profile?.checklist().orEmpty() }

    var pickingFor by remember { mutableStateOf<ChecklistItem.Document?>(null) }
    var photoFor by remember { mutableStateOf<ChecklistItem.Document?>(null) }
    val capture = rememberPhotoCapture { image: CapturedImage ->
        photoFor?.let { item -> viewModel.upload(item, image.bytes, image.contentType) }
        photoFor = null
    }

    Scaffold { padding ->
        // Pull to refresh: ops reviews a document in another console and nothing tells the
        // phone, so this is how a driver finds out a rejection landed while they were driving.
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(pulled = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (profile == null) {
                // Nothing to show a checklist from yet. Usually a moment; if the read failed — a first
                // run in a dead zone — the driver needs the reason and a way to ask again, not a
                // spinner that never stops.
                Box(
                    // Scrollable so the pull gesture has something to grab while there is nothing
                    // on screen, which is exactly when a driver wants to ask again.
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(padding)
                        .padding(Spacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                state.errorReason?.let { stringResource(it.labelRes()) }
                                    ?: state.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(Spacing.md))
                            DriverButton(
                                text = stringResource(Res.string.trips_retry),
                                onClick = { viewModel.refresh() },
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.lg),
                ) {
                    val done = items.count { it.done }
                    StepProgress(current = 2, total = 3)
                    Spacer(Modifier.height(Spacing.xl))
                    Text(
                        stringResource(Res.string.signup_checklist_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(Res.string.signup_checklist_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(Res.string.signup_progress, done, items.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    val errorReason = state.errorReason
                    val errorMessage = errorReason?.let { stringResource(it.labelRes()) } ?: state.errorMessage
                    if (errorMessage != null) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(Modifier.height(Spacing.lg))
                    SectionTitle(stringResource(Res.string.signup_section_you))
                    Spacer(Modifier.height(Spacing.xs))
                    items.filterIsInstance<ChecklistItem.Details>().forEach { item ->
                        ChecklistRow(
                            title = stringResource(Res.string.signup_your_details),
                            subtitle = profile?.name?.takeIf { it.isNotBlank() },
                            status = if (item.done) RowStatus.DONE else RowStatus.TODO,
                            onClick = onOpenDetails,
                        )
                    }

                    Spacer(Modifier.height(Spacing.lg))
                    SectionTitle(stringResource(Res.string.signup_section_truck))
                    Spacer(Modifier.height(Spacing.xs))
                    items.filterIsInstance<ChecklistItem.Truck>().forEach { item ->
                        ChecklistRow(
                            title = stringResource(Res.string.signup_section_truck),
                            subtitle = item.plates.joinToString().takeIf { it.isNotBlank() },
                            status = if (item.done) RowStatus.DONE else RowStatus.TODO,
                            onClick = onOpenTruck,
                        )
                    }

                    Spacer(Modifier.height(Spacing.lg))
                    SectionTitle(stringResource(Res.string.signup_section_docs))
                    Spacer(Modifier.height(Spacing.xs))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        items.filterIsInstance<ChecklistItem.Document>().forEach { item ->
                            val busy = state.uploading == item.kind && state.uploadingTruckId == item.truckId
                            ChecklistRow(
                                title = stringResource(item.titleRes()) + (item.plate?.let { " · $it" } ?: ""),
                                subtitle = item.document?.rejectionReason
                                    ?: stringResource(Res.string.signup_replace_document).takeIf { item.done },
                                status = when {
                                    busy -> RowStatus.BUSY
                                    item.rejected -> RowStatus.REJECTED
                                    item.approved -> RowStatus.DONE
                                    item.done -> RowStatus.IN_REVIEW
                                    else -> RowStatus.TODO
                                },
                                onClick = { if (state.uploading == null) pickingFor = item },
                            )
                        }
                        items.filterIsInstance<ChecklistItem.Unsupported>().forEach { item ->
                            Text(
                                stringResource(Res.string.signup_unsupported, item.wire),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.xl))
                }
            }
        }
    }

    val picking = pickingFor
    if (picking != null) {
        ModalBottomSheet(
            onDismissRequest = { pickingFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(Spacing.lg)) {
                Text(
                    stringResource(picking.titleRes()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                // A citizen carries a national id, a resident an iqama, and the server takes
                // either for the same gap — so the driver says which they have rather than the
                // app assuming.
                if (picking.identityChoice) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        stringResource(Res.string.signup_identity),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        listOf(DriverDocumentKind.NATIONAL_ID, DriverDocumentKind.IQAMA).forEach { kind ->
                            LanguageOptionRow(
                                label = stringResource(kind.labelRes()),
                                selected = kind == picking.kind,
                                onClick = { pickingFor = picking.copy(kind = kind) },
                            )
                        }
                    }
                }
                // What is already filed, before replacing it. The link is signed and short-lived,
                // so it is fetched with the profile and opened rather than cached.
                picking.document?.downloadUrl?.let { url ->
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedButton(
                        onClick = {
                            pickingFor = null
                            urlOpener.open(url)
                        },
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(Res.string.doc_view))
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(
                        onClick = {
                            photoFor = picking
                            pickingFor = null
                            capture.launch(PhotoSource.CAMERA)
                        },
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(Res.string.stop_take_photo))
                    }
                    OutlinedButton(
                        onClick = {
                            photoFor = picking
                            pickingFor = null
                            capture.launch(PhotoSource.LIBRARY)
                        },
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(Res.string.stop_choose_photo))
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}

/**
 * The server's `missing` turned into rows, with the items already done shown alongside.
 *
 * A checklist that only listed what is left would shrink as the driver worked and never tell them
 * how far along they are, so every row is always present and only its state changes.
 */
fun DriverProfile.checklist(): List<ChecklistItem> = buildList {
    add(
        ChecklistItem.Details(
            done = !isMissing(OnboardingGap.Name) && !isMissing(OnboardingGap.Licence),
        ),
    )
    add(
        ChecklistItem.Truck(
            done = !isMissing(OnboardingGap.Truck),
            plates = trucks.map { it.licencePlate },
        ),
    )
    // One row for the identity gap, showing whichever of the two the driver filed. Until they
    // file one, the row offers the choice rather than assuming a citizen.
    val identity = documentFor(DriverDocumentKind.NATIONAL_ID)
        ?: documentFor(DriverDocumentKind.IQAMA)
    add(
        ChecklistItem.Document(
            kind = identity?.kind ?: DriverDocumentKind.NATIONAL_ID,
            truckId = null,
            plate = null,
            document = identity,
            done = !isMissing(OnboardingGap.Identity),
            identityChoice = identity == null,
        ),
    )
    add(
        ChecklistItem.Document(
            kind = DriverDocumentKind.DRIVING_LICENCE,
            truckId = null,
            plate = null,
            document = documentFor(DriverDocumentKind.DRIVING_LICENCE),
            done = !isMissing(OnboardingGap.DrivingLicence),
        ),
    )
    // One registration per truck, named by its plate: a driver with two trucks needs to know which
    // one the app is still waiting for.
    trucks.forEach { truck ->
        add(
            ChecklistItem.Document(
                kind = DriverDocumentKind.VEHICLE_REGISTRATION,
                truckId = truck.id,
                plate = truck.licencePlate,
                document = documentFor(DriverDocumentKind.VEHICLE_REGISTRATION, truck.id),
                done = !isMissing(OnboardingGap.VehicleRegistration(truck.id)),
            ),
        )
    }
    missing.filterIsInstance<OnboardingGap.Unsupported>().forEach { add(ChecklistItem.Unsupported(it.wire)) }
}

private fun ChecklistItem.Document.titleRes(): StringResource = kind.labelRes()

private enum class RowStatus { TODO, DONE, IN_REVIEW, REJECTED, BUSY }

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun ChecklistRow(
    title: String,
    subtitle: String?,
    status: RowStatus,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(Spacing.md).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status == RowStatus.REJECTED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            when (status) {
                RowStatus.BUSY -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                RowStatus.DONE -> TagChip(stringResource(Res.string.signup_item_done), tone = ChipTone.SUCCESS)
                RowStatus.IN_REVIEW -> TagChip(stringResource(Res.string.signup_item_in_review), tone = ChipTone.PRIMARY)
                RowStatus.REJECTED -> TagChip(stringResource(Res.string.signup_item_rejected), tone = ChipTone.DANGER)
                RowStatus.TODO -> {
                    TagChip(stringResource(Res.string.signup_item_todo), tone = ChipTone.WARNING)
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
