package app.platform

import app.core.model.Entry
import app.core.model.Habit
import app.core.schedule.CellState
import app.core.schedule.ScheduleEngine
import kotlinx.datetime.LocalDate

/** What a home-screen widget shows: today's open items first, then what got done today. Platform-neutral. */
data class WidgetItem(
    val habitId: String,
    val name: String,
    val colorKey: String,
    val state: CellState,
    val doseCount: Int,
    val dosesPerDay: Int,
)

object WidgetSnapshot {
    fun build(habits: List<Habit>, entries: List<Entry>, today: LocalDate, limit: Int = 6): List<WidgetItem> {
        val byHabit = entries.groupBy { it.habitId }
        val items = habits.mapNotNull { h ->
            val cell = ScheduleEngine.cells(h, byHabit[h.id].orEmpty(), listOf(today), today).single()
            val relevant = cell.state in setOf(CellState.DUE, CellState.OVERDUE, CellState.PARTIAL, CellState.DONE)
            if (!relevant) null else WidgetItem(h.id, h.name, h.colorKey, cell.state, cell.doseCount, cell.dosesPerDay)
        }
        // open items first (overdue, due, partial), done last
        return items.sortedBy {
            when (it.state) {
                CellState.OVERDUE -> 0; CellState.DUE -> 1; CellState.PARTIAL -> 2; else -> 3
            }
        }.take(limit)
    }
}
