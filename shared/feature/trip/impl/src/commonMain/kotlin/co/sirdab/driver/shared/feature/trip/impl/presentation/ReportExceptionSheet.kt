package co.sirdab.driver.shared.feature.trip.impl.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.DriverTextField
import co.sirdab.driver.shared.core.ui.components.LanguageOptionRow
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.exc_note
import co.sirdab.driver.shared.core.ui.generated.resources.exc_send
import co.sirdab.driver.shared.core.ui.generated.resources.exc_severity
import co.sirdab.driver.shared.core.ui.generated.resources.exc_title
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.trip.api.ExceptionKind
import co.sirdab.driver.shared.feature.trip.api.ExceptionSeverity
import org.jetbrains.compose.resources.stringResource

/**
 * Reporting a problem, in two taps and an optional note.
 *
 * Kind and severity are pickers rather than free text because the report is for
 * a dispatcher deciding what to do next, and "urgent, truck broke down" is
 * actionable in a way a paragraph is not. The note is where the detail goes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportExceptionSheet(
    onDismiss: () -> Unit,
    onSubmit: (ExceptionKind, ExceptionSeverity, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var kind by remember { mutableStateOf(ExceptionKind.DELAY) }
    var severity by remember { mutableStateOf(ExceptionSeverity.MEDIUM) }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
        ) {
            Text(
                stringResource(Res.string.exc_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                ExceptionKind.entries.forEach { value ->
                    LanguageOptionRow(
                        label = stringResource(value.label()),
                        selected = value == kind,
                        onClick = { kind = value },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            Text(
                stringResource(Res.string.exc_severity),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                ExceptionSeverity.entries.forEach { value ->
                    LanguageOptionRow(
                        label = stringResource(value.label()),
                        selected = value == severity,
                        onClick = { severity = value },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            DriverTextField(
                value = note,
                onValueChange = { note = it },
                label = stringResource(Res.string.exc_note),
            )

            Spacer(Modifier.height(Spacing.lg))
            DriverButton(
                text = stringResource(Res.string.exc_send),
                onClick = { onSubmit(kind, severity, note) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
