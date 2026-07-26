package co.sirdab.driver.shared.feature.trip.impl.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.DriverTextField
import co.sirdab.driver.shared.core.ui.components.SignaturePad
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.pod_add_photo
import co.sirdab.driver.shared.core.ui.generated.resources.pod_clear
import co.sirdab.driver.shared.core.ui.generated.resources.pod_photos
import co.sirdab.driver.shared.core.ui.generated.resources.pod_recipient
import co.sirdab.driver.shared.core.ui.generated.resources.pod_seal
import co.sirdab.driver.shared.core.ui.generated.resources.pod_signature
import co.sirdab.driver.shared.core.ui.generated.resources.pod_submit
import co.sirdab.driver.shared.core.ui.generated.resources.pod_title
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodScreen(
    tripId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: PodViewModel = koinViewModel { parametersOf(tripId) },
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.pod_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                DriverButton(
                    text = stringResource(Res.string.pod_submit),
                    onClick = { viewModel.submit(onDone) },
                    enabled = state.canSubmit,
                    isLoading = state.isSubmitting,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md)) {
            Text(stringResource(Res.string.pod_photos), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                repeat(state.photoCount) {
                    Box(
                        Modifier.size(80.dp).clip(RoundedCornerShape(Radius.md)).background(AppColors.Primary.c100),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.Primary.c700) }
                }
                Surface(
                    onClick = viewModel::addPhoto,
                    shape = RoundedCornerShape(Radius.md),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(80.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = stringResource(Res.string.pod_add_photo))
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            DriverTextField(value = state.recipient, onValueChange = viewModel::onRecipient, label = stringResource(Res.string.pod_recipient))
            Spacer(Modifier.height(Spacing.sm))
            DriverTextField(value = state.seal, onValueChange = viewModel::onSeal, label = stringResource(Res.string.pod_seal))

            Spacer(Modifier.height(Spacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.pod_signature), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = viewModel::clearSignature) { Text(stringResource(Res.string.pod_clear)) }
            }
            Spacer(Modifier.height(Spacing.xs))
            SignaturePad(clearKey = state.clearKey, onChanged = viewModel::onSignatureChanged, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
