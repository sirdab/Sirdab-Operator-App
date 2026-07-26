package co.sirdab.driver.shared.feature.profile.impl

import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DocStatus
import co.sirdab.driver.shared.core.model.DocType
import co.sirdab.driver.shared.core.model.Document
import co.sirdab.driver.shared.feature.profile.api.domain.DocumentRepository
import co.sirdab.driver.shared.feature.profile.impl.presentation.AutoBidViewModel
import co.sirdab.driver.shared.feature.profile.impl.presentation.HistoryViewModel
import co.sirdab.driver.shared.feature.profile.impl.presentation.ProfileViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

class DocumentRepositoryMock(private val world: DemoWorld) : DocumentRepository {

    override fun observeDocuments(): Flow<List<Document>> = world.state.map { it.documents }

    override suspend fun upload(type: DocType): AppResult<Document> {
        setStatus(type, DocStatus.VERIFYING)
        delay(1500) // instant "Verified ✓" after 1.5s (plan §5, screen 5)
        setStatus(type, DocStatus.VERIFIED, fileRef = "file://${type.name.lowercase()}")
        val doc = world.state.value.documents.first { it.type == type }
        return AppResult.Success(doc)
    }

    private fun setStatus(type: DocType, status: DocStatus, fileRef: String? = null) {
        world.update { w ->
            val existing = w.documents.firstOrNull { it.type == type }
            val updated = if (existing == null) {
                w.documents + Document(id = "doc-${type.name.lowercase()}", type = type, status = status, fileRef = fileRef)
            } else {
                w.documents.map {
                    if (it.type == type) it.copy(status = status, fileRef = fileRef ?: it.fileRef) else it
                }
            }
            w.copy(documents = updated)
        }
    }
}

val profileModule: Module = module {
    single { DocumentRepositoryMock(get()) } bind DocumentRepository::class
    viewModelOf(::ProfileViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::AutoBidViewModel)
}
