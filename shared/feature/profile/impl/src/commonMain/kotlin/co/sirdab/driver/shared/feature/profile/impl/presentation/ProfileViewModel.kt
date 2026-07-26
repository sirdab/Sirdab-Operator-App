package co.sirdab.driver.shared.feature.profile.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.model.Document
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.preferences.locale.AppLanguage
import co.sirdab.driver.shared.core.preferences.locale.LanguageStore
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
    documentRepository: DocumentRepository,
) : ViewModel() {

    val state = combine(
        demoWorld.state,
        documentRepository.observeDocuments(),
        languageStore.language,
    ) { world, docs, lang ->
        ProfileUiState(driver = world.driver, documents = docs, language = lang, personas = world.personas)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUiState())

    fun setLanguage(language: AppLanguage) = languageStore.setLanguage(language)
    fun switchPersona(personaKey: String) = demoWorld.switchPersona(personaKey)
}
