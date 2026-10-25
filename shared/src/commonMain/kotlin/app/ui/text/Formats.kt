package app.ui.text

import app.core.model.Habit
import app.core.model.Interval
import app.core.model.IntervalUnit
import app.core.model.ReminderSettings
import app.core.schedule.Cell
import app.core.schedule.CellState
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil

/** Formatting helpers on top of the active [Strings] (see [Lang]). */
object Formats {
    fun monthLong(date: LocalDate) = L.monthsLong[date.month.ordinal]
    fun shortDate(date: LocalDate) = L.shortDate(date.day, date.month.ordinal)
    fun weekdayDate(date: LocalDate) = L.weekdayDate(date.dayOfWeek.ordinal, date.day, date.month.ordinal)
    fun longDate(date: LocalDate) = L.longDate(date.dayOfWeek.ordinal, date.day, date.month.ordinal, date.year)
    fun weekdayShort(day: DayOfWeek) = L.weekdaysShort[day.ordinal]
    fun isWeekend(day: DayOfWeek) = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY

    /** 24-hour "09:00". */
    fun time(minuteOfDay: Int): String {
        val h = minuteOfDay / 60; val m = minuteOfDay % 60
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }
    fun time(t: LocalDateTime) = time(t.hour * 60 + t.minute)
    fun reminderMoment(t: LocalDateTime) = L.at(weekdayDate(t.date), time(t))

    fun daysBefore(n: Int) = L.daysBefore(n)
    fun reminderSummary(r: ReminderSettings) =
        if (!r.enabled) L.off else "${time(r.minuteOfDay)} · ${daysBefore(r.daysBefore).lowercase()}"

    fun unitLabel(unit: IntervalUnit, count: Int) = L.unitCount(unit, count)

    fun interval(i: Interval): String = when {
        i.isDaily -> L.daily
        i.count == 1 -> when (i.unit) {
            IntervalUnit.DAY -> L.daily; IntervalUnit.WEEK -> L.weekly
            IntervalUnit.MONTH -> L.monthly; IntervalUnit.YEAR -> L.yearly
        }
        else -> L.every(i.count, i.unit)
    }

    /** Row subtitle, e.g. "every 3 days · due Oct 18", "2× a day", "overdue 3 days". */
    fun habitSubtitle(habit: Habit, nextDue: LocalDate, today: LocalDate): String {
        val rhythm = if (habit.dosesPerDay > 1) L.perDay(habit.dosesPerDay) else interval(habit.interval)
        if (habit.interval.isDaily) return rhythm
        val lateBy = nextDue.daysUntil(today)
        val due = when {
            lateBy > 0 -> L.overdueDays(lateBy)
            lateBy == 0 -> L.dueToday
            lateBy == -1 -> L.dueTomorrow
            else -> L.dueOn(shortDate(nextDue))
        }
        return "$rhythm · $due"
    }

    fun cellStateWords(cell: Cell): String = when (cell.state) {
        CellState.DONE -> L.stDone
        CellState.PARTIAL -> L.stPartial(cell.doseCount, cell.dosesPerDay)
        CellState.COVERED -> L.stCovered
        CellState.DUE -> L.stDue
        CellState.OVERDUE -> L.stOverdue
        CellState.IDLE -> L.stIdle
    }

    fun cellDescription(habitName: String, cell: Cell): String = buildString {
        append(habitName).append(", ").append(weekdayDate(cell.date))
        if (cell.isToday) append(", ").append(L.cToday)
        if (cell.hasNote) append(", ").append(L.cHasNote)
        if (cell.isReminderDay) append(", ").append(L.cReminder)
    }
}
