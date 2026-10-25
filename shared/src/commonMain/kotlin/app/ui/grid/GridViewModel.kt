package app.ui.grid

import androidx.lifecycle.ViewModel
import app.ui.text.L
import androidx.lifecycle.viewModelScope
import app.core.model.Entry
import app.core.model.Folder
import app.core.model.Habit
import app.core.model.Interval
import app.core.model.IntervalUnit
import app.core.schedule.Cell
import app.core.schedule.ScheduleEngine
import app.data.HabitDraft
import app.data.HabitRepository
import app.platform.AppClock
import app.platform.Analytics
import app.platform.Events
import app.ui.entry.EntryEditor
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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

data class HabitRowUi(
    val habit: Habit,
    val subtitle: String,
    val cells: List<Cell>,
    /** Colour of the folder the habit sits in, for the left bar. Null = no folder. */
    val folderColorKey: String?,
)

sealed interface GridItem {
    val key: String
    data class FolderHeader(val folder: Folder, val count: Int) : GridItem { override val key = "f-${folder.id}" }
    data class Row(val row: HabitRowUi) : GridItem { override val key = "h-${row.habit.id}" }
}

sealed interface GridUiState {
    data object Loading : GridUiState
    data class Empty(val today: LocalDate) : GridUiState
    data class Content(
        val today: LocalDate,
        val days: List<LocalDate>,
        val todayIndex: Int,
        val items: List<GridItem>,
    ) : GridUiState
}

class GridViewModel(
    private val repo: HabitRepository,
    private val clock: AppClock,
    private val analytics: Analytics,
) : ViewModel() {

    val entryEditor = EntryEditor(repo, viewModelScope, analytics)

    val state: StateFlow<GridUiState> =
        combine(repo.habits, repo.entries, repo.folders, clock.todayFlow) { habits, entries, folders, today ->
            buildState(habits, entries, folders, today, clock.now())
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridUiState.Loading)

    fun toggle(habitId: String, date: LocalDate) {
        if (date > clock.today()) return // future days are not checkable
        viewModelScope.launch { repo.toggleDay(habitId, date) }
        analytics.track(Events.DAY_TOGGLED, mapOf("source" to "grid"))
    }

    fun openNote(habitId: String, date: LocalDate) {
        if (date > clock.today()) return
        entryEditor.open(habitId, date, clock.today())
    }

    fun toggleFolder(folder: Folder) {
        viewModelScope.launch { repo.setFolderCollapsed(folder.id, !folder.collapsed) }
    }

    /** Empty-state templates: one tap to a first habit. */
    fun addTemplate(template: Template) {
        viewModelScope.launch {
            repo.createHabit(
                HabitDraft(
                    name = L.templates[template.ordinal].second,
                    interval = Interval(template.count, template.unit),
                    colorKey = template.colorKey,
                    anchorDate = clock.today(),
                ),
            )
        }
    }

    /** Starter habits (labels and names live in [app.ui.text.Strings.templates], same order). */
    enum class Template(val count: Int, val unit: IntervalUnit, val colorKey: String) {
        VITAMIN(1, IntervalUnit.DAY, "yellow"),
        SHEETS(2, IntervalUnit.WEEK, "mint"),
        INSURANCE(1, IntervalUnit.YEAR, "indigo"),
    }

    companion object {
        const val DAYS_BACK = 120
        const val DAYS_AHEAD = 45

        fun window(today: LocalDate): List<LocalDate> {
            val start = today.minus(DAYS_BACK, DateTimeUnit.DAY)
            return List(DAYS_BACK + DAYS_AHEAD + 1) { start.plus(it, DateTimeUnit.DAY) }
        }

        /** Pure: the grid for one dataset and day. Shared by the view model and previews. */
        fun buildState(habits: List<Habit>, entries: List<Entry>, folders: List<Folder>, today: LocalDate, now: LocalDateTime): GridUiState {
            if (habits.isEmpty()) return GridUiState.Empty(today)
            val days = window(today)
            val byHabit = entries.groupBy { it.habitId }
            val folderIds = folders.map { it.id }.toSet()

            fun rowFor(h: Habit, folder: Folder?): HabitRowUi {
                val own = byHabit[h.id].orEmpty()
                val nextDue = ScheduleEngine.nextDue(h, own)
                val reminderDay = ScheduleEngine.nextReminder(h, own, now)?.date
                return HabitRowUi(h, Formats.habitSubtitle(h, nextDue, today), ScheduleEngine.cells(h, own, days, today, reminderDay), folder?.colorKey)
            }

            val items = buildList {
                // Habits without a folder (or whose folder is gone) come first, without a header.
                habits.filter { it.folderId == null || it.folderId !in folderIds }
                    .forEach { add(GridItem.Row(rowFor(it, null))) }
                folders.forEach { f ->
                    val inFolder = habits.filter { it.folderId == f.id }
                    add(GridItem.FolderHeader(f, inFolder.size))
                    if (!f.collapsed) inFolder.forEach { add(GridItem.Row(rowFor(it, f))) }
                }
            }
            return GridUiState.Content(today, days, days.indexOf(today), items)
        }
    }
}
