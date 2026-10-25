package app.ui.text

import app.core.model.Habit
import app.core.model.Interval
import app.core.model.IntervalUnit
import app.core.schedule.Cell
import app.core.schedule.CellState
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import app.core.model.ReminderSettings
import kotlinx.datetime.daysUntil

/**
 * All user-facing wording in one place, written fresh for this app (not the original's strings).
 * English only for now; moves to Compose resources when localisation lands (M4).
 */
object Formats {
    private val monthsShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val monthsLong = listOf("January", "February", "March", "April", "May", "June", "July", "August",
        "September", "October", "November", "December")

    fun monthLong(date: LocalDate) = monthsLong[date.month.ordinal]
    fun shortDate(date: LocalDate) = "${monthsShort[date.month.ordinal]} ${date.day}"

    fun weekdayDate(date: LocalDate) = "${weekdayShort(date.dayOfWeek)}, ${shortDate(date)}"
    fun longDate(date: LocalDate) =
        "${weekdayLong(date.dayOfWeek)}, ${monthsLong[date.month.ordinal]} ${date.day}, ${date.year}"
    fun weekdayLong(day: DayOfWeek) = day.name.lowercase().replaceFirstChar { it.uppercase() }

    /** 24-hour "09:00". A 12/24h setting can come later. */
    fun time(minuteOfDay: Int): String {
        val h = minuteOfDay / 60; val m = minuteOfDay % 60
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }
    fun time(t: LocalDateTime) = time(t.hour * 60 + t.minute)
    fun reminderMoment(t: LocalDateTime) = "${weekdayDate(t.date)} at ${time(t)}"

    fun daysBefore(n: Int) = when (n) { 0 -> "On the day"; 1 -> "1 day before"; else -> "$n days before" }

    fun reminderSummary(r: ReminderSettings) =
        if (!r.enabled) "Off" else "${time(r.minuteOfDay)} · ${daysBefore(r.daysBefore).lowercase()}"

    fun weekdayShort(day: DayOfWeek) = when (day) {
        DayOfWeek.MONDAY -> "Mon"; DayOfWeek.TUESDAY -> "Tue"; DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"; DayOfWeek.FRIDAY -> "Fri"; DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }

    fun isWeekend(day: DayOfWeek) = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY

    fun unitLabel(unit: IntervalUnit, count: Int): String {
        val one = when (unit) {
            IntervalUnit.DAY -> "day"; IntervalUnit.WEEK -> "week"
            IntervalUnit.MONTH -> "month"; IntervalUnit.YEAR -> "year"
        }
        return if (count == 1) one else one + "s"
    }

    fun interval(i: Interval): String = when {
        i.isDaily -> "daily"
        i.count == 1 -> when (i.unit) {
            IntervalUnit.WEEK -> "weekly"; IntervalUnit.MONTH -> "monthly"; IntervalUnit.YEAR -> "yearly"
            IntervalUnit.DAY -> "daily"
        }
        else -> "every ${i.count} ${unitLabel(i.unit, i.count)}"
    }

    /** Row subtitle: "every 3 days · due Oct 18", "2× a day", "overdue 3 days". */
    fun habitSubtitle(habit: Habit, nextDue: LocalDate, today: LocalDate): String {
        val rhythm = if (habit.dosesPerDay > 1) "${habit.dosesPerDay}× a day" else interval(habit.interval)
        if (habit.interval.isDaily) return rhythm
        val lateBy = nextDue.daysUntil(today)
        val due = when {
            lateBy > 0 -> "overdue $lateBy ${unitLabel(IntervalUnit.DAY, lateBy)}"
            lateBy == 0 -> "due today"
            lateBy == -1 -> "due tomorrow"
            else -> "due ${shortDate(nextDue)}"
        }
        return "$rhythm · $due"
    }

    fun cellStateWords(cell: Cell): String = when (cell.state) {
        CellState.DONE -> "done"
        CellState.PARTIAL -> "${cell.doseCount} of ${cell.dosesPerDay} done"
        CellState.COVERED -> "not needed, covered by the last time"
        CellState.DUE -> "due"
        CellState.OVERDUE -> "overdue"
        CellState.IDLE -> "nothing due"
    }

    fun cellDescription(habitName: String, cell: Cell): String = buildString {
        append(habitName).append(", ")
        append(weekdayShort(cell.date.dayOfWeek)).append(' ').append(shortDate(cell.date))
        if (cell.isToday) append(", today")
        if (cell.hasNote) append(", has a note")
        if (cell.isReminderDay) append(", reminder set")
    }
}
