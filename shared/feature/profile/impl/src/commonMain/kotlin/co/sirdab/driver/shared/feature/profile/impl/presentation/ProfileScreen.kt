package co.sirdab.driver.shared.feature.profile.impl.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.model.DocStatus
import co.sirdab.driver.shared.core.model.DocType
import co.sirdab.driver.shared.core.model.Document
import co.sirdab.driver.shared.core.model.DriverDocumentStatus
import co.sirdab.driver.shared.core.model.VerificationDocument
import co.sirdab.driver.shared.core.model.VerificationDocumentStatus
import co.sirdab.driver.shared.core.preferences.locale.AppLanguage
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.TagChip
import co.sirdab.driver.shared.core.ui.components.LanguageOptionRow
import co.sirdab.driver.shared.core.ui.components.VerifiedBadge
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.common_cancel
import co.sirdab.driver.shared.core.ui.generated.resources.doc_days_left
import co.sirdab.driver.shared.core.ui.generated.resources.doc_insurance
import co.sirdab.driver.shared.core.ui.generated.resources.doc_license
import co.sirdab.driver.shared.core.ui.generated.resources.doc_national_id
import co.sirdab.driver.shared.core.ui.generated.resources.doc_operating_card
import co.sirdab.driver.shared.core.ui.generated.resources.doc_vehicle_reg
import co.sirdab.driver.shared.core.ui.generated.resources.docstat_expired
import co.sirdab.driver.shared.core.ui.generated.resources.docstat_expiring
import co.sirdab.driver.shared.core.ui.generated.resources.docstat_missing
import co.sirdab.driver.shared.core.ui.generated.resources.docstat_verified
import co.sirdab.driver.shared.core.ui.generated.resources.docstat_verifying
import co.sirdab.driver.shared.core.ui.generated.resources.logout_body
import co.sirdab.driver.shared.core.ui.generated.resources.logout_title
import co.sirdab.driver.shared.core.ui.generated.resources.logout_unsent
import co.sirdab.driver.shared.core.ui.generated.resources.profile_documents
import co.sirdab.driver.shared.core.ui.generated.resources.profile_fleet
import co.sirdab.driver.shared.core.ui.generated.resources.profile_logout
import co.sirdab.driver.shared.core.ui.generated.resources.review_under_way
import co.sirdab.driver.shared.core.ui.generated.resources.signup_item_in_review
import co.sirdab.driver.shared.core.ui.generated.resources.signup_item_rejected
import co.sirdab.driver.shared.core.ui.generated.resources.profile_history
import co.sirdab.driver.shared.core.ui.generated.resources.profile_language
import co.sirdab.driver.shared.core.ui.generated.resources.profile_ontime
import co.sirdab.driver.shared.core.ui.generated.resources.profile_persona
import co.sirdab.driver.shared.core.ui.generated.resources.profile_score
import co.sirdab.driver.shared.core.ui.generated.resources.profile_settings
import co.sirdab.driver.shared.core.ui.generated.resources.profile_trips
import co.sirdab.driver.shared.core.ui.generated.resources.profile_vehicle
import co.sirdab.driver.shared.core.ui.generated.resources.persona_flatbed
import co.sirdab.driver.shared.core.ui.generated.resources.persona_new
import co.sirdab.driver.shared.core.ui.generated.resources.persona_van
import co.sirdab.driver.shared.core.ui.generated.resources.verified
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.onboarding.api.domain.ProfileDocument
import co.sirdab.driver.shared.core.util.localizeDigits
import co.sirdab.driver.shared.core.util.toLocalizedString
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenHistory: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isSigningOut by viewModel.isSigningOut.collectAsState()
    val isSwitchingFleet by viewModel.isSwitchingFleet.collectAsState()
    val fleetSwitchError by viewModel.fleetSwitchError.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var confirmingLogout by remember { mutableStateOf(false) }
    val lang = Locale.current.language
    val driver = state.driver
    val now = Clock.System.now().toEpochMilliseconds()

    // Pull to refresh: ops approves a document and a fleet clears a driver to work in two
    // different consoles, and neither one tells the phone. Pulling is how a driver asks.
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(
                    Modifier.size(56.dp).background(AppColors.Primary.c600, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val name = if (lang == "ar") driver.fullNameAr else driver.fullNameEn
                    Text(name.take(1).ifBlank { "?" }, style = MaterialTheme.typography.headlineSmall, color = AppColors.White)
                }
                Column {
                    Text(
                        (if (lang == "ar") driver.fullNameAr else driver.fullNameEn).ifBlank { driver.fullNameEn },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (driver.phone.isNotBlank()) {
                        Text(driver.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile(stringResource(Res.string.profile_score), "★ ${driver.carrierScore.toString().localizeDigits(lang)}", Modifier.weight(1f))
                StatTile(stringResource(Res.string.profile_trips), driver.tripsCompleted.toLocalizedString(lang), Modifier.weight(1f))
                StatTile(stringResource(Res.string.profile_ontime), "${driver.onTimePercent.toLocalizedString(lang)}%", Modifier.weight(1f))
            }

            // Vehicle
            driver.vehicle?.let { v ->
                Spacer(Modifier.height(Spacing.md))
                Surface(shape = RoundedCornerShape(Radius.lg), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text(stringResource(Res.string.profile_vehicle), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text("${stringResource(v.truckSize.labelRes())} · ${stringResource(v.truckType.labelRes())} • ${v.plate}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Document vault, in order of who has the most current answer: the fleet's own review if
            // there is one, then the driver's own sign-up documents, then the demo world's list.
            Spacer(Modifier.height(Spacing.lg))
            SectionTitle(stringResource(Res.string.profile_documents))
            if (state.isUnderReview) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    stringResource(Res.string.review_under_way),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            val fleetDocuments = state.verification?.documents.orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                when {
                    fleetDocuments.isNotEmpty() ->
                        fleetDocuments.forEach { document -> VerificationDocumentRow(document) }
                    state.ownDocuments.isNotEmpty() ->
                        state.ownDocuments.forEach { document -> OwnDocumentRow(document) }
                    else -> state.documents.forEach { doc -> DocumentRow(doc, now, lang) }
                }
            }

            // Fleet. Shown whenever there is one to name — a driver should be able to see whose work
            // they are looking at — and tappable only when there is another to move to.
            if (state.workspaces.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                SectionTitle(stringResource(Res.string.profile_fleet))
                Spacer(Modifier.height(Spacing.xs))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    state.workspaces.forEach { workspace ->
                        LanguageOptionRow(
                            label = workspace.workspaceName.ifBlank { workspace.workspaceId },
                            selected = workspace.workspaceId == state.activeWorkspaceId,
                            onClick = {
                                if (state.canSwitchFleet && !isSwitchingFleet) {
                                    viewModel.switchFleet(workspace.workspaceId)
                                }
                            },
                        )
                    }
                }
                if (isSwitchingFleet) {
                    Spacer(Modifier.height(Spacing.xs))
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                }
                fleetSwitchError?.let { error ->
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        error.reason?.let { stringResource(it.labelRes()) } ?: error.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Actions
            Spacer(Modifier.height(Spacing.lg))
            ActionRow(stringResource(Res.string.profile_history), onOpenHistory)
            Spacer(Modifier.height(Spacing.xs))

            // Settings — language
            Spacer(Modifier.height(Spacing.lg))
            SectionTitle(stringResource(Res.string.profile_settings))
            Spacer(Modifier.height(Spacing.xs))
            Text(stringResource(Res.string.profile_language), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xxs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                AppLanguage.entries.forEach { l ->
                    SelectableChip(l.displayName, l == state.language) { viewModel.setLanguage(l) }
                }
            }

            // Settings — persona
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(Res.string.profile_persona), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xxs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                state.personas.forEach { p ->
                    SelectableChip(personaLabel(p.personaKey), p.personaKey == driver.personaKey) { viewModel.switchPersona(p.personaKey) }
                }
            }
            // Settings — leaving
            Spacer(Modifier.height(Spacing.lg))
            LogoutRow(
                label = stringResource(Res.string.profile_logout),
                isBusy = isSigningOut,
                onClick = { confirmingLogout = true },
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (confirmingLogout) {
        LogoutDialog(
            unsentWrites = state.unsentWrites,
            onConfirm = {
                confirmingLogout = false
                viewModel.signOut(onLoggedOut)
            },
            onDismiss = { confirmingLogout = false },
        )
    }
}

/**
 * Asked before it happens, because getting back in needs signal, an SMS and a code.
 *
 * Unsent work is named here rather than after the fact: it is the one thing signing out destroys,
 * and a driver at the back of a warehouse can wait for a bar of signal instead.
 */
@Composable
private fun LogoutDialog(
    unsentWrites: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.logout_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.logout_body))
                if (unsentWrites > 0) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        stringResource(Res.string.logout_unsent, unsentWrites),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(Res.string.profile_logout),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

@Composable
private fun LogoutRow(label: String, isBusy: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isBusy, onClick = onClick),
    ) {
        Row(
            Modifier.padding(Spacing.md).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            if (isBusy) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.padding(Spacing.md)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.Primary.c700)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@OptIn(ExperimentalTime::class)
@Composable
private fun DocumentRow(doc: Document, now: Long, lang: String) {
    Surface(shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.md).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(doc.type.docLabel()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            when (doc.status) {
                DocStatus.EXPIRING_SOON -> {
                    val days = doc.expiresAtMillis?.let { ((it - now) / DAY_MS).coerceAtLeast(0) } ?: 0
                    TagChip("${days.toString().localizeDigits(lang)} ${stringResource(Res.string.doc_days_left)}", tone = ChipTone.WARNING)
                }
                DocStatus.VERIFIED -> TagChip(stringResource(Res.string.docstat_verified), tone = ChipTone.SUCCESS)
                DocStatus.VERIFYING -> TagChip(stringResource(Res.string.docstat_verifying), tone = ChipTone.PRIMARY)
                DocStatus.EXPIRED -> TagChip(stringResource(Res.string.docstat_expired), tone = ChipTone.DANGER)
                DocStatus.MISSING, DocStatus.UPLOADED -> TagChip(stringResource(Res.string.docstat_missing), tone = ChipTone.NEUTRAL)
            }
        }
    }
}

/**
 * One document as the fleet's verification queue holds it.
 *
 * [VerificationDocument.expired] rather than the date decides the warning: an approved document
 * can be expired, and the server has already worked out which. A rejection reason is ops' own
 * words and is shown verbatim — it is the only thing that tells a driver what to do differently.
 */
@Composable
private fun VerificationDocumentRow(document: VerificationDocument) {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Spacing.md).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(document.kind.labelRes()), style = MaterialTheme.typography.bodyLarge)
                document.rejectionReason?.let { reason ->
                    Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            when {
                document.expired ->
                    TagChip(stringResource(Res.string.docstat_expired), tone = ChipTone.DANGER)
                document.status == VerificationDocumentStatus.REJECTED ->
                    TagChip(stringResource(Res.string.signup_item_rejected), tone = ChipTone.DANGER)
                document.status == VerificationDocumentStatus.APPROVED ->
                    TagChip(stringResource(Res.string.docstat_verified), tone = ChipTone.SUCCESS)
                else ->
                    TagChip(stringResource(Res.string.docstat_verifying), tone = ChipTone.PRIMARY)
            }
        }
    }
}

/**
 * One document the driver handed over at sign-up, and where it stands.
 *
 * `uploaded` means ops has not looked yet, which to a driver is "in review" rather than a status
 * of their own doing. A rejection carries ops' words verbatim: it is the only thing that tells
 * them what to photograph differently.
 */
@Composable
private fun OwnDocumentRow(document: ProfileDocument) {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Spacing.md).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(document.kind.labelRes()), style = MaterialTheme.typography.bodyLarge)
                document.rejectionReason?.let { reason ->
                    Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            when (document.status) {
                DriverDocumentStatus.APPROVED ->
                    TagChip(stringResource(Res.string.docstat_verified), tone = ChipTone.SUCCESS)
                DriverDocumentStatus.REJECTED ->
                    TagChip(stringResource(Res.string.signup_item_rejected), tone = ChipTone.DANGER)
                DriverDocumentStatus.UPLOADED ->
                    TagChip(stringResource(Res.string.signup_item_in_review), tone = ChipTone.PRIMARY)
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(Spacing.md).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.pill),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) AppColors.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

private fun DocType.docLabel(): StringResource = when (this) {
    DocType.NATIONAL_ID -> Res.string.doc_national_id
    DocType.DRIVING_LICENSE -> Res.string.doc_license
    DocType.VEHICLE_REGISTRATION -> Res.string.doc_vehicle_reg
    DocType.INSURANCE -> Res.string.doc_insurance
    DocType.OPERATING_CARD -> Res.string.doc_operating_card
}

@Composable
private fun personaLabel(personaKey: String): String = when (personaKey) {
    "flatbed_verified" -> stringResource(Res.string.persona_flatbed)
    "van_3t" -> stringResource(Res.string.persona_van)
    else -> stringResource(Res.string.persona_new)
}
