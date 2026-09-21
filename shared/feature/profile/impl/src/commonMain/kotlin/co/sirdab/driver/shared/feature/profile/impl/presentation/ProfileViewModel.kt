package co.sirdab.driver.shared.feature.profile.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.model.Document
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.preferences.locale.AppLanguage
import co.sirdab.driver.shared.core.network.BackendMode
import co.sirdab.driver.shared.core.preferences.locale.LanguageStore
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import co.sirdab.driver.shared.feature.profile.api.domain.DocumentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProfileUiState(
    val driver: Driver = Driver("", "", "", ""),
    val documents: List<Document> = emptyList(),
    val language: AppLanguage = AppLanguage.ENGLISH,
    val personas: List<Driver> = emptyList(),
)

class ProfileViewModel(
    private val demoWorld: DemoWorld,
    private val languageStore: LanguageStore,
    private val backendMode: BackendMode,
    authRepository: AuthRepository,
    documentRepository: DocumentRepository,
) : ViewModel() {

    val state = combine(
        // The signed-in driver, not the demo world's: against a real TMS these
        // are two different people, and the world's one is nobody.
        authRepository.observeDriver(),
        demoWorld.state,
        documentRepository.observeDocuments(),
        languageStore.language,
    ) { driver, world, docs, lang ->
        ProfileUiState(
            driver = driver,
            documents = docs,
            language = lang,
            // Switching persona rewrites the demo world, which a real session
            // does not read, so the control would do nothing but confuse.
            personas = if (backendMode == BackendMode.DEMO) world.personas else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUiState())

    fun setLanguage(language: AppLanguage) = languageStore.setLanguage(language)
    fun switchPersona(personaKey: String) = demoWorld.switchPersona(personaKey)
}
