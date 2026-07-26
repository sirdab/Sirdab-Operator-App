package co.sirdab.driver.shared.feature.onboarding.impl.presentation.docs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.DocStatus
import co.sirdab.driver.shared.core.model.DocType
import co.sirdab.driver.shared.core.model.Document
import co.sirdab.driver.shared.core.ui.components.DocumentUploadCard
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.StepProgress
import co.sirdab.driver.shared.core.ui.components.UploadState
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.common_continue
import co.sirdab.driver.shared.core.ui.generated.resources.doc_license
import co.sirdab.driver.shared.core.ui.generated.resources.doc_national_id
import co.sirdab.driver.shared.core.ui.generated.resources.doc_vehicle_reg
import co.sirdab.driver.shared.core.ui.generated.resources.docs_subtitle
import co.sirdab.driver.shared.core.ui.generated.resources.docs_title
import co.sirdab.driver.shared.core.ui.generated.resources.upload
import co.sirdab.driver.shared.core.ui.generated.resources.uploading
import co.sirdab.driver.shared.core.ui.generated.resources.verified
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.profile.api.domain.DocumentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val ONBOARDING_DOCS = listOf(DocType.NATIONAL_ID, DocType.DRIVING_LICENSE, DocType.VEHICLE_REGISTRATION)

class DocUploadViewModel(private val documents: DocumentRepository) : ViewModel() {
    val docs: StateFlow<List<Document>> = documents.observeDocuments()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun upload(type: DocType) {
        viewModelScope.launch { documents.upload(type) }
    }
}

@Composable
fun DocUploadScreen(
    onDone: () -> Unit,
    viewModel: DocUploadViewModel = koinViewModel(),
) {
    val docs by viewModel.docs.collectAsState()

    fun statusFor(type: DocType): DocStatus =
        docs.firstOrNull { it.type == type }?.status ?: DocStatus.MISSING

    fun uploadStateFor(type: DocType): UploadState = when (statusFor(type)) {
        DocStatus.MISSING -> UploadState.EMPTY
        DocStatus.UPLOADED, DocStatus.VERIFYING -> UploadState.UPLOADING
        else -> UploadState.VERIFIED
    }

    val allVerified = ONBOARDING_DOCS.all { statusFor(it) == DocStatus.VERIFIED }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
        ) {
            StepProgress(current = 4, total = 6)
            Spacer(Modifier.height(Spacing.xl))
            Text(stringResource(Res.string.docs_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(Res.string.docs_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ONBOARDING_DOCS.forEach { type ->
                    val titleRes = when (type) {
                        DocType.NATIONAL_ID -> Res.string.doc_national_id
                        DocType.DRIVING_LICENSE -> Res.string.doc_license
                        else -> Res.string.doc_vehicle_reg
                    }
                    DocumentUploadCard(
                        title = stringResource(titleRes),
                        hint = stringResource(Res.string.upload),
                        state = uploadStateFor(type),
                        verifiedLabel = stringResource(Res.string.verified),
                        uploadingLabel = stringResource(Res.string.uploading),
                        onClick = { viewModel.upload(type) },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            DriverButton(
                text = stringResource(Res.string.common_continue),
                onClick = onDone,
                enabled = allVerified,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
