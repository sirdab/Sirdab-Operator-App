package co.sirdab.driver.shared.feature.profile.api.domain

import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DocType
import co.sirdab.driver.shared.core.model.Document
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>
    /** Simulates capture + verification: sets VERIFYING, then VERIFIED after a short delay. */
    suspend fun upload(type: DocType): AppResult<Document>
}
