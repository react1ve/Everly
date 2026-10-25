package app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.core.model.Entry
import app.core.model.Folder
import app.core.model.Habit
import app.core.schedule.Cell
import app.core.schedule.CellState
import app.core.schedule.ScheduleEngine
import app.data.HabitRepository
import app.platform.AppClock
import app.ui.entry.EntryEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object Gone : DetailUiState
    data class Content(
        val habit: Habit,
        val folder: Folder?,
        val year: Int,
        val minYear: Int,
        val maxYear: Int,
        val yearCells: List<Cell>,
        val doneThisYear: Int,
        val nextDue: LocalDate,
        val nextReminder: LocalDateTime?,
        val entries: List<Entry>, // selected year, newest first
        val today: LocalDate,
    ) : DetailUiState
}

class HabitDetailViewModel(
    private val habitId: String,
    private val repo: HabitRepository,
    private val clock: AppClock,
) : ViewModel() {

    val entryEditor = EntryEditor(repo, viewModelScope)
    private val year = MutableStateFlow(clock.today().year)

    val state: StateFlow<DetailUiState> =
        combine(repo.habits, repo.entries, repo.folders, year, clock.todayFlow) { habits, all, folders, y, today ->
            val habit = habits.firstOrNull { it.id == habitId } ?: return@combine DetailUiState.Gone
            val own = all.filter { it.habitId == habitId }
            val minYear = minOf(habit.anchorDate.year, own.minOfOrNull { it.date.year } ?: today.year, today.year)
            val nextDue = ScheduleEngine.nextDue(habit, own)
            val maxYear = maxOf(today.year, nextDue.year)
            val selected = y.coerceIn(minYear, maxYear)
            val nextReminder = ScheduleEngine.nextReminder(habit, own, clock.now())
            val days = yearDays(selected)
            val cells = ScheduleEngine.cells(habit, own, days, today, nextReminder?.date)
            DetailUiState.Content(
                habit = habit,
                folder = folders.firstOrNull { it.id == habit.folderId },
                year = selected, minYear = minYear, maxYear = maxYear,
                yearCells = cells,
                doneThisYear = cells.count { it.state == CellState.DONE },
                nextDue = nextDue,
                nextReminder = nextReminder,
                entries = own.filter { it.date.year == selected }.sortedByDescending { it.date },
                today = today,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    fun previousYear() = year.update { it - 1 }
    fun nextYear() = year.update { it + 1 }

    fun addEntry() = entryEditor.open(habitId, clock.today(), clock.today(), dateEditable = true)
    fun editEntry(entry: Entry) = entryEditor.open(habitId, entry.date, clock.today())

    companion object {
        fun yearDays(year: Int): List<LocalDate> {
            val start = LocalDate(year, 1, 1)
            val out = ArrayList<LocalDate>(366)
            var d = start
            while (d.year == year) { out += d; d = d.plus(1, DateTimeUnit.DAY) }
            return out
        }
    }
}
