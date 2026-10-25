package app.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.core.model.Entry
import app.core.model.Folder
import app.core.model.Habit
import app.core.schedule.Cell
import app.core.schedule.CellState
import app.core.schedule.ScheduleEngine
import app.data.SampleData
import app.design.AppTheme
import app.ui.detail.DetailUiState
import app.ui.detail.HabitDetailViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** Fixed data for @Preview functions, so previews render the same every day. */
internal object PreviewData {
    val today: LocalDate = LocalDate(2026, 10, 17)
    val now: LocalDateTime = LocalDateTime(today, LocalTime(9, 41))

    private val snapshot = SampleData.snapshot(today)
    val folders: List<Folder> = snapshot.folders.sortedBy { it.sortOrder }
    val habits: List<Habit> = snapshot.habits.sortedBy { it.sortOrder } +
        Habit(
            id = "h-long", name = "📚 Read two chapters of the book club novel before the Sunday meeting",
            interval = snapshot.habits.first { it.id == "h-swim" }.interval, anchorDate = today, colorKey = "pink", sortOrder = 99,
        )
    val entries: List<Entry> = snapshot.entries.values.toList()

    fun habit(id: String): Habit = habits.first { it.id == id }
    fun entriesOf(id: String): List<Entry> = entries.filter { it.habitId == id }

    /** One hand-made cell for component previews. */
    fun cell(
        state: CellState, count: Int = 0, doses: Int = 1, note: Boolean = false,
        today: Boolean = false, bell: Boolean = false, date: LocalDate = this.today,
    ) = Cell(date, state, count, doses, note, today, bell, true)

    /** The schedule engine's cells for a whole year of [id], as the detail screen shows them. */
    fun yearCells(id: String, year: Int = today.year): List<Cell> {
        val habit = habit(id)
        val own = entriesOf(id)
        val reminder = ScheduleEngine.nextReminder(habit, own, now)
        return ScheduleEngine.cells(habit, own, HabitDetailViewModel.yearDays(year), today, reminder?.date)
    }

    /** What HabitDetailViewModel would emit for [id]. */
    fun detail(id: String, year: Int = today.year): DetailUiState.Content {
        val habit = habit(id)
        val own = entriesOf(id)
        val cells = yearCells(id, year)
        val nextDue = ScheduleEngine.nextDue(habit, own)
        return DetailUiState.Content(
            habit = habit,
            folder = folders.firstOrNull { it.id == habit.folderId },
            year = year,
            minYear = minOf(habit.anchorDate.year, own.minOfOrNull { it.date.year } ?: today.year, today.year),
            maxYear = maxOf(today.year, nextDue.year),
            yearCells = cells,
            doneThisYear = cells.count { it.state == CellState.DONE },
            nextDue = nextDue,
            nextReminder = ScheduleEngine.nextReminder(habit, own, now),
            entries = own.filter { it.date.year == year }.sortedByDescending { it.date },
            today = today,
        )
    }
}

/** App theme plus the screen background: wrap every preview in this. */
@Composable
internal fun PreviewSurface(dark: Boolean = false, content: @Composable () -> Unit) {
    AppTheme(dark = dark) {
        Box(Modifier.background(AppTheme.colors.bg)) { content() }
    }
}
