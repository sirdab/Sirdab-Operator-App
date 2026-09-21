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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.model.DocStatus
import co.sirdab.driver.shared.core.model.DocType
import co.sirdab.driver.shared.core.model.Document
import co.sirdab.driver.shared.core.preferences.locale.AppLanguage
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.TagChip
import co.sirdab.driver.shared.core.ui.components.VerifiedBadge
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.generated.resources.Res
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
import co.sirdab.driver.shared.core.ui.generated.resources.profile_documents
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
import co.sirdab.driver.shared.core.util.localizeDigits
import co.sirdab.driver.shared.core.util.toLocalizedString
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalTime::class)
@Composable
fun ProfileScreen(
    onOpenHistory: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val lang = Locale.current.language
    val driver = state.driver
    val now = Clock.System.now().toEpochMilliseconds()

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

        // Document vault
        Spacer(Modifier.height(Spacing.lg))
        SectionTitle(stringResource(Res.string.profile_documents))
        Spacer(Modifier.height(Spacing.xs))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            state.documents.forEach { doc -> DocumentRow(doc, now, lang) }
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
        Spacer(Modifier.height(Spacing.xl))
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
