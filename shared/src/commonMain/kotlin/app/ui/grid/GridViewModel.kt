package app.ui.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.core.model.Folder
import app.core.model.Habit
import app.core.schedule.Cell
import app.core.schedule.ScheduleEngine
import app.data.HabitDraft
import app.data.HabitRepository
import app.core.model.Interval
import app.core.model.IntervalUnit
import app.platform.AppClock
import app.ui.text.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

data class HabitRowUi(
    val habit: Habit,
    val subtitle: String,
    val cells: List<Cell>,
)

/** M1: folders are carried but rendered flat; grouping + collapse arrive in M2. */
sealed interface GridUiState {
    data object Loading : GridUiState
    data class Empty(val today: LocalDate) : GridUiState
    data class Content(
        val today: LocalDate,
        val days: List<LocalDate>,
        val todayIndex: Int,
        val rows: List<HabitRowUi>,
        val folders: List<Folder>,
    ) : GridUiState
}

class GridViewModel(
    private val repo: HabitRepository,
    private val clock: AppClock,
) : ViewModel() {

    val state: StateFlow<GridUiState> =
        combine(repo.habits, repo.entries, repo.folders, clock.todayFlow) { habits, entries, folders, today ->
            if (habits.isEmpty()) return@combine GridUiState.Empty(today)
            val days = window(today)
            val now = clock.now()
            val byHabit = entries.groupBy { it.habitId }
            val rows = habits.map { h ->
                val own = byHabit[h.id].orEmpty()
                val nextDue = ScheduleEngine.nextDue(h, own)
                val reminderDay = ScheduleEngine.nextReminder(h, own, now)?.date
                HabitRowUi(
                    habit = h,
                    subtitle = Formats.habitSubtitle(h, nextDue, today),
                    cells = ScheduleEngine.cells(h, own, days, today, reminderDay),
                )
            }
            GridUiState.Content(today, days, days.indexOf(today), rows, folders)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridUiState.Loading)

    fun toggle(habitId: String, date: LocalDate) {
        if (date > clock.today()) return // future days are not checkable
        viewModelScope.launch { repo.toggleDay(habitId, date) }
    }

    /** Empty-state templates: one tap to a first habit. */
    fun addTemplate(template: Template) {
        viewModelScope.launch {
            repo.createHabit(
                HabitDraft(
                    name = template.name,
                    interval = Interval(template.count, template.unit),
                    colorKey = template.colorKey,
                    anchorDate = clock.today(),
                ),
            )
        }
    }

    enum class Template(val label: String, val name: String, val count: Int, val unit: IntervalUnit, val colorKey: String) {
        VITAMIN("Daily vitamin", "💊 Vitamin", 1, IntervalUnit.DAY, "yellow"),
        SHEETS("Fresh bed sheets · every 2 weeks", "🛏️ Change bed sheets", 2, IntervalUnit.WEEK, "mint"),
        INSURANCE("Car insurance · yearly", "🚗 Renew car insurance", 1, IntervalUnit.YEAR, "indigo"),
    }

    companion object {
        const val DAYS_BACK = 120
        const val DAYS_AHEAD = 45

        fun window(today: LocalDate): List<LocalDate> {
            val start = today.minus(DAYS_BACK, DateTimeUnit.DAY)
            return List(DAYS_BACK + DAYS_AHEAD + 1) { start.plus(it, DateTimeUnit.DAY) }
        }
    }
}
