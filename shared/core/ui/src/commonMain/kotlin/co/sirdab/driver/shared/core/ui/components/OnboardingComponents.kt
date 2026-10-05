package co.sirdab.driver.shared.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing

/** One choice in a single-choice list: a language, a truck, a fleet. */
@Composable
fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.md),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth().border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(Radius.md)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Horizontal step indicator for the onboarding flow. */
@Composable
fun StepProgress(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        repeat(total) { i ->
            val done = i <= current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}
