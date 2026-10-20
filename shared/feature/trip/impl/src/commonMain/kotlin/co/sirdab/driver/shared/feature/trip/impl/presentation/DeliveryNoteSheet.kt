package co.sirdab.driver.shared.feature.trip.impl.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.media.PhotoSource
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.pod_body
import co.sirdab.driver.shared.core.ui.generated.resources.pod_title
import co.sirdab.driver.shared.core.ui.generated.resources.stop_choose_photo
import co.sirdab.driver.shared.core.ui.generated.resources.stop_take_photo
import co.sirdab.driver.shared.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The proof-of-delivery step: the signed delivery note, photographed, before Delivered is recorded.
 *
 * Camera or library, like the stop's own photo, since a note photographed a moment ago is as good
 * as one taken now. Closing the sheet records nothing, so the stop stays open for another go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryNoteSheet(
    onDismiss: () -> Unit,
    onCapture: (PhotoSource) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
        ) {
            Text(
                stringResource(Res.string.pod_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(Res.string.pod_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.lg))
            Button(onClick = { onCapture(PhotoSource.CAMERA) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(Res.string.stop_take_photo))
            }
            Spacer(Modifier.height(Spacing.xs))
            OutlinedButton(onClick = { onCapture(PhotoSource.LIBRARY) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(Res.string.stop_choose_photo))
            }
        }
    }
}
