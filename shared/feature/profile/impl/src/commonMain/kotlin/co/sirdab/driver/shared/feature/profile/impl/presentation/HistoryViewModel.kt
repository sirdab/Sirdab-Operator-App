package co.sirdab.driver.shared.feature.profile.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.model.TripEarning
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val WEEK_MS = 7 * 86_400_000L

data class WeekBar(val weeksAgo: Int, val amountSar: Int)

data class HistoryUiState(
    val bars: List<WeekBar> = emptyList(),
    val earnings: List<TripEarning> = emptyList(),
)

@OptIn(ExperimentalTime::class)
class HistoryViewModel(demoWorld: DemoWorld) : ViewModel() {

    val state = demoWorld.state.map { world ->
        val now = Clock.System.now().toEpochMilliseconds()
        val earnings = world.earnings
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
}
