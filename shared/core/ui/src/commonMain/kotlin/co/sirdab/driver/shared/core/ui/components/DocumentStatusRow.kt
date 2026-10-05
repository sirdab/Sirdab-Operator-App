package co.sirdab.driver.shared.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.model.DriverDocumentStatus
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.docstat_verified
import co.sirdab.driver.shared.core.ui.generated.resources.signup_item_in_review
import co.sirdab.driver.shared.core.ui.generated.resources.signup_item_rejected
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = modifier)
}

/**
 * One document and where it stands.
 *
 * [rejectionReason] is ops' own words, shown verbatim: it is the only thing that tells a driver what
 * to photograph differently. [onClick] is given only when there is something to open.
 */
@Composable
fun DocumentStatusRow(
    title: String,
    status: String,
    tone: ChipTone,
    rejectionReason: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            Modifier.padding(Spacing.md).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                rejectionReason?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            TagChip(status, tone = tone)
        }
    }
}

/**
 * A document the driver handed over, and where it stands with ops. `uploaded` means ops has not
 * looked yet, which to a driver is "in review" rather than a status of their own doing.
 */
@Composable
fun DocumentStatusRow(
    title: String,
    status: DriverDocumentStatus,
    rejectionReason: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val (label, tone) = when (status) {
        DriverDocumentStatus.APPROVED -> Res.string.docstat_verified to ChipTone.SUCCESS
        DriverDocumentStatus.REJECTED -> Res.string.signup_item_rejected to ChipTone.DANGER
        DriverDocumentStatus.UPLOADED -> Res.string.signup_item_in_review to ChipTone.PRIMARY
    }
    DocumentStatusRow(title, stringResource(label), tone, rejectionReason, onClick)
}
