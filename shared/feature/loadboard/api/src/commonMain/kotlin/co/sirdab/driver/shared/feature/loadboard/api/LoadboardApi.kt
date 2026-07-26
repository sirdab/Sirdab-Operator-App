package co.sirdab.driver.shared.feature.loadboard.api

import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Load
import co.sirdab.driver.shared.core.model.Shipper
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
sealed interface LoadboardRoute : NavKey {
    @Serializable data object Board : LoadboardRoute
    @Serializable data class LoadDetail(val loadId: String) : LoadboardRoute
}

interface LoadRepository {
    fun observeLoads(): Flow<List<Load>>
    suspend fun refresh(): AppResult<List<Load>>
    suspend fun loadDetail(id: String): AppResult<Load?>
    suspend fun shipper(id: String): Shipper?
}
