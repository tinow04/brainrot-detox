package dev.tinow.brainrotstopper.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tinow.brainrotstopper.domain.model.Stats
import dev.tinow.brainrotstopper.domain.stats.StatsAggregator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * B3 state from `stats.json` (live: a lock in the background updates the tab). "Today" is the local epoch day, re-read on
 * every resume of the tab ([refresh]), so the window moves at midnight without a restart. Labels are cached per package.
 */
class StatsViewModel(
    stats: Flow<Stats>,
    private val today: () -> Long,
    private val label: (String) -> String,
    private val log: (String) -> Unit = {},
) : ViewModel() {
    private val day = MutableStateFlow(today())
    private val labels = HashMap<String, String>()

    val ui: StateFlow<StatsState?> = combine(stats, day) { s, d ->
        StatsModel.state(StatsAggregator.summarize(s, d)) { pkg -> labels.getOrPut(pkg) { label(pkg) } }.also { st ->
            // numbers only (no app names), for scripts: `shown today=14 week=87 streak=12 cards=412 correct=354 saved=200 ranking=4`
            log("shown today=${st.blockedToday} week=${st.blockedWeek} streak=${st.streakDays} cards=${st.cards} correct=${st.correct} saved=${st.savedMinutes} ranking=${st.ranking.size}")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() {
        day.value = today()
    }
}
