package co.sirdab.driver.shared.feature.onboarding.impl.presentation.signup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverDocumentStatus
import co.sirdab.driver.shared.core.platform.browser.UrlOpener
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.DriverSecondaryButton
import co.sirdab.driver.shared.core.ui.components.TagChip
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.blocked_body
import co.sirdab.driver.shared.core.ui.generated.resources.blocked_title
import co.sirdab.driver.shared.core.ui.generated.resources.profile_logout
import co.sirdab.driver.shared.core.ui.generated.resources.review_body
import co.sirdab.driver.shared.core.ui.generated.resources.review_check_again
import co.sirdab.driver.shared.core.ui.generated.resources.review_fix_rejected
import co.sirdab.driver.shared.core.ui.generated.resources.review_title
import co.sirdab.driver.shared.core.ui.generated.resources.signup_checklist_title
import co.sirdab.driver.shared.core.ui.generated.resources.signup_item_done
import co.sirdab.driver.shared.core.ui.generated.resources.signup_item_in_review
import co.sirdab.driver.shared.core.ui.generated.resources.signup_item_rejected
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfileStatus
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverOnboardingRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

data class ReviewUiState(
    val profile: DriverProfile? = null,
    val isChecking: Boolean = false,
    /**
     * The same read, asked for by pulling rather than by the button.
     *
     * Kept apart from [isChecking] only so the gesture's own indicator does not appear on the
     * automatic read when the screen opens.
     */
    val isRefreshing: Boolean = false,
)

/**
 * The wait between handing everything over and ops approving it.
 *
 * Re-reads the profile when the screen opens and whenever the driver asks, because approval
 * happens somewhere else entirely and the app has no way to be told about it yet.
 */
class ReviewViewModel(
    private val repository: DriverOnboardingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState(profile = repository.profile.value))
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    init {
        check()
    }

    fun check(pulled: Boolean = false) {
        _state.value = _state.value.copy(isChecking = !pulled, isRefreshing = pulled)
        viewModelScope.launch {
            val result = repository.refresh()
            _state.value = ReviewUiState(
                profile = (result as? AppResult.Success)?.data ?: _state.value.profile,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnderReviewScreen(
    onApproved: () -> Unit,
    onFixRejected: () -> Unit,
    viewModel: ReviewViewModel = koinViewModel(),
    urlOpener: UrlOpener = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    val profile = state.profile

    // Approval, and only approval, closes this. `destination()` is MAIN for a pending profile with
    // nothing missing too — which is exactly the profile this screen is opened for — so keying on it
    // popped the screen on its first frame.
    LaunchedEffect(profile?.status) {
        if (profile?.status == DriverProfileStatus.ACTIVE) onApproved()
    }

    val rejected = profile?.documents.orEmpty().filter { it.status == DriverDocumentStatus.REJECTED }

    Scaffold { padding ->
        // The whole point of this screen is waiting for someone else to act, and nothing tells the
        // phone when they do. Pulling is the gesture a driver will reach for before the button.
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.check(pulled = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
            ) {
                Spacer(Modifier.height(Spacing.xl))
                Text(stringResource(Res.string.review_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(Res.string.review_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(Spacing.lg))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    profile?.documents.orEmpty().forEach { document ->
                        Surface(
                            shape = RoundedCornerShape(Radius.md),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            // Tappable only when there is something to open: the link is minted with
                            // the profile read and is null until the upload was confirmed.
                            modifier = Modifier.fillMaxWidth().then(
                                document.downloadUrl?.let { url ->
                                    Modifier.clickable { urlOpener.open(url) }
                                } ?: Modifier,
                            ),
                        ) {
                            Row(
                                Modifier.padding(Spacing.md).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(document.kind.labelRes()),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    // Ops' own words, shown verbatim: it is the only thing that tells a
                                    // driver what to photograph differently.
                                    document.rejectionReason?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                when (document.status) {
                                    DriverDocumentStatus.APPROVED ->
                                        TagChip(stringResource(Res.string.signup_item_done), tone = ChipTone.SUCCESS)
                                    DriverDocumentStatus.REJECTED ->
                                        TagChip(stringResource(Res.string.signup_item_rejected), tone = ChipTone.DANGER)
                                    DriverDocumentStatus.UPLOADED ->
                                        TagChip(stringResource(Res.string.signup_item_in_review), tone = ChipTone.PRIMARY)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.lg))
                if (rejected.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.review_fix_rejected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    DriverButton(
                        text = stringResource(Res.string.signup_checklist_title),
                        onClick = onFixRejected,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
                DriverSecondaryButton(
                    text = stringResource(Res.string.review_check_again),
                    onClick = { viewModel.check() },
                    enabled = !state.isChecking,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The end of the road inside the app: ops has suspended this driver.
 *
 * Nothing here retries, because nothing the driver does on the phone will change it. The only
 * action is to sign out, so a colleague can use the phone.
 */
@Composable
fun BlockedScreen(
    onSignedOut: () -> Unit,
    viewModel: BlockedViewModel = koinViewModel(),
) {
    Box(Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(Res.string.blocked_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(Res.string.blocked_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xl))
            DriverSecondaryButton(
                text = stringResource(Res.string.profile_logout),
                onClick = { viewModel.signOut(onSignedOut) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

class BlockedViewModel(private val authRepository: AuthRepository) : ViewModel() {
    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onSignedOut()
        }
    }
}
