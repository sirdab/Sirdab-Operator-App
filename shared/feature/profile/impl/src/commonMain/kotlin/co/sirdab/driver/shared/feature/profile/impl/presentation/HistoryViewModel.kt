package co.sirdab.driver.shared.feature.profile.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.model.TripEarning
import co.sirdab.driver.shared.core.network.BackendMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val WEEK_MS = 7 * 86_400_000L

data class WeekBar(val weeksAgo: Int, val amountSar: Int)

data class HistoryUiState(
    val bars: List<WeekBar> = emptyList(),
    val earnings: List<TripEarning> = emptyList(),
)

@OptIn(ExperimentalTime::class)
class HistoryViewModel(demoWorld: DemoWorld, backendMode: BackendMode) : ViewModel() {

    val state = demoWorld.state.map { world ->
        val now = Clock.System.now().toEpochMilliseconds()
        // Earnings have no TMS route yet, so a real driver has none to show. The demo world's are
        // simulated, and a phone that once ran demo mode still has them persisted: shown here they
        // would read as this driver's pay.
        val earnings = if (backendMode == BackendMode.DEMO) world.earnings else emptyList()
        val buckets = IntArray(6)
        earnings.forEach { txn ->
            val week = ((now - txn.earnedAtMillis) / WEEK_MS).toInt()
            if (week in 0..5) buckets[5 - week] += txn.amountSar
        }
        HistoryUiState(
            bars = buckets.mapIndexed { i, v -> WeekBar(weeksAgo = 5 - i, amountSar = v) },
            earnings = earnings.sortedByDescending { it.earnedAtMillis },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUiState())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Pull to refresh.
     *
     * Earnings have no route in the driver contract yet, so this reads the same in-memory world the
     * screen is already showing and there is nothing new to find. The gesture is here because the
     * rest of the app has it and a screen that ignores a pull reads as broken; it becomes a real
     * read the day the numbers come from the server.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch { _isRefreshing.value = false }
    }
}
