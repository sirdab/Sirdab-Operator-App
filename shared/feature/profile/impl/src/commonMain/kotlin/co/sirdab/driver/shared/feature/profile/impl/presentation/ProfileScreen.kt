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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import co.sirdab.driver.shared.core.platform.locale.AppLocale
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.model.VerificationDocument
import co.sirdab.driver.shared.core.model.VerificationDocumentStatus
import co.sirdab.driver.shared.core.preferences.locale.AppLanguage
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.ErrorText
import co.sirdab.driver.shared.core.ui.components.SectionTitle
import co.sirdab.driver.shared.core.ui.components.DocumentStatusRow
import co.sirdab.driver.shared.core.ui.components.OptionRow
import co.sirdab.driver.shared.core.ui.components.equipmentLabel
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.common_cancel
import co.sirdab.driver.shared.core.ui.generated.resources.docstat_expired
import co.sirdab.driver.shared.core.ui.generated.resources.docstat_verified
import co.sirdab.driver.shared.core.ui.generated.resources.docstat_verifying
import co.sirdab.driver.shared.core.ui.generated.resources.delete_account_body
import co.sirdab.driver.shared.core.ui.generated.resources.delete_account_confirm
import co.sirdab.driver.shared.core.ui.generated.resources.delete_account_title
import co.sirdab.driver.shared.core.ui.generated.resources.delete_account_unsent
import co.sirdab.driver.shared.core.ui.generated.resources.logout_body
import co.sirdab.driver.shared.core.ui.generated.resources.logout_title
import co.sirdab.driver.shared.core.ui.generated.resources.logout_unsent
import co.sirdab.driver.shared.core.ui.generated.resources.profile_documents
import co.sirdab.driver.shared.core.ui.generated.resources.profile_fleet
import co.sirdab.driver.shared.core.ui.generated.resources.profile_logout
import co.sirdab.driver.shared.core.ui.generated.resources.review_under_way
import co.sirdab.driver.shared.core.ui.generated.resources.signup_item_rejected
import co.sirdab.driver.shared.core.ui.generated.resources.profile_delete_account
import co.sirdab.driver.shared.core.ui.generated.resources.profile_language
import co.sirdab.driver.shared.core.ui.generated.resources.profile_settings
import co.sirdab.driver.shared.core.ui.generated.resources.profile_vehicle
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLoggedOut: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isSigningOut by viewModel.isSigningOut.collectAsState()
    val isSwitchingFleet by viewModel.isSwitchingFleet.collectAsState()
    val fleetSwitchError by viewModel.fleetSwitchError.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isDeletingAccount by viewModel.isDeletingAccount.collectAsState()
    val deleteAccountError by viewModel.deleteAccountError.collectAsState()
    var confirmingLogout by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val lang = AppLocale.current()
    val driver = state.driver
    val name = (if (lang == "ar") driver.fullNameAr else driver.fullNameEn).ifBlank { driver.fullNameEn }

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
                    Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.take(1).ifBlank { "?" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Column {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (driver.phone.isNotBlank()) {
                        Text(driver.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Vehicle
            driver.vehicle?.let { v ->
                Spacer(Modifier.height(Spacing.md))
                Surface(shape = RoundedCornerShape(Radius.lg), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text(stringResource(Res.string.profile_vehicle), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.xxs))
                        Text(listOf(equipmentLabel(v.bodyType, v.sizeClass, v.temperature), v.plate).filter { it.isNotEmpty() }.joinToString(" • "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Document vault, in order of who has the most current answer: the fleet's own review if
            // there is one, then the driver's own sign-up documents.
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
                    else -> state.ownDocuments.forEach { document ->
                        DocumentStatusRow(
                            title = stringResource(document.kind.labelRes()),
                            status = document.status,
                            rejectionReason = document.rejectionReason,
                        )
                    }
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
                        OptionRow(
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
                    ErrorText(error)
                }
            }

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

            // Settings — leaving
            Spacer(Modifier.height(Spacing.lg))
            LogoutRow(
                label = stringResource(Res.string.profile_logout),
                isBusy = isSigningOut,
                onClick = { confirmingLogout = true },
            )
            Spacer(Modifier.height(Spacing.sm))
            LogoutRow(
                label = stringResource(Res.string.profile_delete_account),
                isBusy = isDeletingAccount,
                onClick = { confirmingDelete = true },
            )
            deleteAccountError?.let { error ->
                Spacer(Modifier.height(Spacing.xs))
                ErrorText(error)
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    // Asked before either happens. Getting back in after logging out needs signal, an SMS and a
    // code; a deleted account has no way back at all. Unsent work is named here rather than after
    // the fact: it is the one thing both destroy, and a driver can wait for a bar of signal instead.
    if (confirmingDelete) {
        ConfirmDestructiveDialog(
            title = stringResource(Res.string.delete_account_title),
            body = stringResource(Res.string.delete_account_body),
            unsentWarning = state.unsentWrites.takeIf { it > 0 }
                ?.let { stringResource(Res.string.delete_account_unsent, it) },
            confirmLabel = stringResource(Res.string.delete_account_confirm),
            onConfirm = {
                confirmingDelete = false
                viewModel.deleteAccount(onLoggedOut)
            },
            onDismiss = { confirmingDelete = false },
        )
    }

    if (confirmingLogout) {
        ConfirmDestructiveDialog(
            title = stringResource(Res.string.logout_title),
            body = stringResource(Res.string.logout_body),
            unsentWarning = state.unsentWrites.takeIf { it > 0 }
                ?.let { stringResource(Res.string.logout_unsent, it) },
            confirmLabel = stringResource(Res.string.profile_logout),
            onConfirm = {
                confirmingLogout = false
                viewModel.signOut(onLoggedOut)
            },
            onDismiss = { confirmingLogout = false },
        )
    }
}

@Composable
private fun ConfirmDestructiveDialog(
    title: String,
    body: String,
    unsentWarning: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(body)
                if (unsentWarning != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(unsentWarning, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
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
private fun VerificationDocumentRow(document: VerificationDocument) {
    val (label, tone) = when {
        document.expired -> Res.string.docstat_expired to ChipTone.DANGER
        document.status == VerificationDocumentStatus.REJECTED -> Res.string.signup_item_rejected to ChipTone.DANGER
        document.status == VerificationDocumentStatus.APPROVED -> Res.string.docstat_verified to ChipTone.SUCCESS
        else -> Res.string.docstat_verifying to ChipTone.PRIMARY
    }
    DocumentStatusRow(
        title = stringResource(document.kind.labelRes()),
        status = stringResource(label),
        tone = tone,
        rejectionReason = document.rejectionReason,
    )
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
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}
