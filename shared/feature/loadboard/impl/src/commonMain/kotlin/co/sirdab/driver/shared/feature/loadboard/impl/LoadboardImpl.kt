package co.sirdab.driver.shared.feature.loadboard.impl

import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.demo.demoLatency
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Load
import co.sirdab.driver.shared.feature.loadboard.api.LoadRepository
import co.sirdab.driver.shared.feature.loadboard.impl.presentation.LoadBoardViewModel
import co.sirdab.driver.shared.feature.loadboard.impl.presentation.LoadDetailViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

@OptIn(ExperimentalTime::class)
class LoadRepositoryMock(private val world: DemoWorld) : LoadRepository {

    override fun observeLoads(): Flow<List<Load>> =
        world.state.map { it.loads.sortedBy { load -> load.distanceFromDriverKm } }

    override suspend fun refresh(): AppResult<List<Load>> {
        demoLatency()
        // Pull-to-refresh injects 1–2 fresh loads so the board feels alive (plan §5, screen 7).
        val injectCount = 1 + Random.nextInt(2)
        val existing = world.state.value.loads
        val now = Clock.System.now().toEpochMilliseconds()
        val newLoads = (0 until injectCount).mapNotNull { i ->
            val template = existing.randomOrNull() ?: return@mapNotNull null
            val bump = Random.nextInt(-120, 160)
            template.copy(
                id = "L-${now % 100000}-$i",
                suggestedRateSar = (template.suggestedRateSar + bump).coerceAtLeast(300),
                fixedRateSar = null,
                instantBook = false,
                postedAtMillis = now,
                distanceFromDriverKm = 1 + Random.nextInt(6),
            )
        }
        world.update { it.copy(loads = newLoads + it.loads) }
        return AppResult.Success(world.state.value.loads)
    }

    override suspend fun loadDetail(id: String): AppResult<Load?> {
        demoLatency()
        return AppResult.Success(world.state.value.loads.firstOrNull { it.id == id })
    }

    override suspend fun shipper(id: String) =
        world.state.value.shippers.firstOrNull { it.id == id }
}

val loadboardModule: Module = module {
    single { LoadRepositoryMock(get()) } bind LoadRepository::class
    viewModelOf(::LoadBoardViewModel)
    viewModel { (loadId: String) -> LoadDetailViewModel(loadId, get()) }
}
