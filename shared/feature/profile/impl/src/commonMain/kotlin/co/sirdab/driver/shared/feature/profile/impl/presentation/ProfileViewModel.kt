package co.sirdab.driver.shared.feature.profile.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Document
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.model.DriverVerification
import co.sirdab.driver.shared.core.preferences.locale.AppLanguage
import co.sirdab.driver.shared.core.network.BackendMode
import co.sirdab.driver.shared.core.preferences.locale.LanguageStore
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverOnboardingRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.ProfileDocument
import co.sirdab.driver.shared.feature.onboarding.api.domain.ProfileWorkspace
import co.sirdab.driver.shared.feature.profile.api.domain.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val driver: Driver = Driver("", "", "", ""),
    val documents: List<Document> = emptyList(),
    val language: AppLanguage = AppLanguage.ENGLISH,
    val personas: List<Driver> = emptyList(),
    /** Recorded work still in the outbox, which signing out would throw away. */
    val unsentWrites: Int = 0,
    /**
     * The fleet's file on this driver. Null for a driver no fleet has taken on, whose paperwork
     * lives on their own profile rather than in anyone's verification queue.
     */
    val verification: DriverVerification? = null,
    /**
     * The documents the driver handed over at sign-up, and where each one stands with ops.
     *
     * Theirs, not a fleet's: a driver no fleet has taken on still has paperwork in review, and
     * this is the only place they can see it.
     */
    val ownDocuments: List<ProfileDocument> = emptyList(),
    /** True while ops has still to look at a profile that has everything it asked for. */
    val isUnderReview: Boolean = false,
    /** Every fleet that has this driver on its roster. More than one is unusual, not impossible. */
    val workspaces: List<ProfileWorkspace> = emptyList(),
    /** The one the session is stamped into, which is the one the API answers for. */
    val activeWorkspaceId: String? = null,
) {
    /** Offering a switch between one fleet and itself would be noise. */
    val canSwitchFleet: Boolean get() = workspaces.size > 1
}

class ProfileViewModel(
    private val demoWorld: DemoWorld,
    private val languageStore: LanguageStore,
    private val backendMode: BackendMode,
    private val authRepository: AuthRepository,
    private val onboardingRepository: DriverOnboardingRepository,
    documentRepository: DocumentRepository,
) : ViewModel() {

    private val _isSigningOut = MutableStateFlow(false)
    private val _isSwitchingFleet = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)

    /** Separate from [state]: it belongs to the gesture, not to the driver. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Separate from [state] for the same reason as signing out: it belongs to the row, not the driver. */
    val isSwitchingFleet: StateFlow<Boolean> = _isSwitchingFleet.asStateFlow()

    private val _fleetSwitchError = MutableStateFlow<AppError?>(null)

    /**
     * Why the last switch did not happen. Without it the spinner stopped and nothing changed, and
     * the most likely reason — unsent work that belongs to the fleet being left — was never said.
     */
    val fleetSwitchError: StateFlow<AppError?> = _fleetSwitchError.asStateFlow()

    /** Separate from [state]: it is the button's business, not a fact about the driver. */
    val isSigningOut: StateFlow<Boolean> = _isSigningOut.asStateFlow()

    val state = combine(
        // The signed-in driver, not the demo world's: against a real TMS these
        // are two different people, and the world's one is nobody.
        authRepository.observeDriver(),
        demoWorld.state,
        documentRepository.observeDocuments(),
        languageStore.language,
        // Four flows folded into one because the typed `combine` stops at five, and these four
        // are all answers about the session rather than about the driver.
        combine(
            authRepository.observeUnsentWrites(),
            authRepository.observeVerification(),
            authRepository.observeActiveWorkspace(),
            onboardingRepository.profile,
        ) { unsent, verification, activeWorkspace, profile ->
            SessionFacts(
                unsentWrites = unsent,
                verification = verification,
                activeWorkspaceId = activeWorkspace,
                workspaces = profile?.workspaces.orEmpty(),
                ownDocuments = profile?.documents.orEmpty(),
                isUnderReview = profile?.isUnderReview == true,
            )
        },
    ) { driver, world, docs, lang, session ->
        ProfileUiState(
            driver = driver,
            // The demo world's paperwork, which in TMS mode belongs to nobody: shown there, it
            // stood in for a real driver's documents whenever theirs had not loaded yet.
            documents = if (backendMode == BackendMode.DEMO) docs else emptyList(),
            language = lang,
            // Switching persona rewrites the demo world, which a real session
            // does not read, so the control would do nothing but confuse.
            personas = if (backendMode == BackendMode.DEMO) world.personas else emptyList(),
            unsentWrites = session.unsentWrites,
            verification = session.verification,
            workspaces = session.workspaces,
            activeWorkspaceId = session.activeWorkspaceId,
            ownDocuments = session.ownDocuments,
            isUnderReview = session.isUnderReview,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUiState())

    private data class SessionFacts(
        val unsentWrites: Int,
        val verification: DriverVerification?,
        val activeWorkspaceId: String?,
        val workspaces: List<ProfileWorkspace>,
        val ownDocuments: List<ProfileDocument>,
        val isUnderReview: Boolean,
    )

    /**
     * Move the session to another fleet.
     *
     * Everything on this screen is scoped to the fleet the token names, so the repository re-reads
     * the profile and the fleet's verdict as part of the switch and this flows back through
     * [state] on its own.
     */
    fun switchFleet(workspaceId: String) {
        if (_isSwitchingFleet.value || workspaceId == state.value.activeWorkspaceId) return
        _isSwitchingFleet.value = true
        _fleetSwitchError.value = null
        viewModelScope.launch {
            try {
                val result = authRepository.switchWorkspace(workspaceId)
                if (result is AppResult.Failure) _fleetSwitchError.value = result.error
            } finally {
                _isSwitchingFleet.value = false
            }
        }
    }

    /**
     * Pull to refresh: ask the server about this driver again.
     *
     * Everything on this screen arrives through [state]'s flows, so nothing is assigned here — the
     * read lands in the repositories and the screen follows on its own.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            // finally: a read that throws must not leave the pull spinner turning forever.
            try {
                authRepository.refreshDriver()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun setLanguage(language: AppLanguage) = languageStore.setLanguage(language)
    fun switchPersona(personaKey: String) = demoWorld.switchPersona(personaKey)

    /**
     * [onSignedOut] runs once the session is gone, so the caller can leave a shell that no longer
     * has anything to show. Signing out can take a moment: it tries the outbox one last time.
     */
    fun signOut(onSignedOut: () -> Unit) {
        if (_isSigningOut.value) return
        _isSigningOut.value = true
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } finally {
                _isSigningOut.value = false
            }
            onSignedOut()
        }
    }
}
