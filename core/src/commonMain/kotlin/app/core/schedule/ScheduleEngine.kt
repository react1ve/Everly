package app.core.schedule

import app.core.model.Entry
import app.core.model.Habit
import app.core.model.Interval
import app.core.model.IntervalUnit
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

enum class CellState { DONE, PARTIAL, COVERED, DUE, OVERDUE, IDLE }

data class Cell(
    val date: LocalDate,
    val state: CellState,
    val doseCount: Int,
    val dosesPerDay: Int,
    val hasNote: Boolean,
    val isToday: Boolean,
    /** The day the next reminder fires on (bell marker). */
    val isReminderDay: Boolean,
    /** Checking is allowed for today and the past only. */
    val isTappable: Boolean,
)

/**
 * The rules of the app, in one place, with no platform or UI code.
 *
 * Rolling schedule: the next due date is counted from the last day the habit was
 * fully done, not from the original plan. Late on Tuesday -> next one counts from Tuesday.
 */
object ScheduleEngine {

    /** Month and year steps clamp to the end of the month (Jan 31 + 1 month = Feb 28/29). */
    fun add(interval: Interval, date: LocalDate): LocalDate = when (interval.unit) {
        IntervalUnit.DAY -> date.plus(interval.count, DateTimeUnit.DAY)
        IntervalUnit.WEEK -> date.plus(interval.count * 7, DateTimeUnit.DAY)
        IntervalUnit.MONTH -> date.plus(interval.count, DateTimeUnit.MONTH)
        IntervalUnit.YEAR -> date.plus(interval.count * 12, DateTimeUnit.MONTH)
    }

    fun isDone(habit: Habit, entry: Entry?): Boolean =
        entry != null && entry.doseCount >= habit.dosesPerDay

    fun lastDone(habit: Habit, entries: Collection<Entry>): LocalDate? =
        entries.asSequence()
            .filter { it.habitId == habit.id && isDone(habit, it) }
            .maxOfOrNull { it.date }

    fun nextDue(habit: Habit, entries: Collection<Entry>): LocalDate =
        lastDone(habit, entries)?.let { add(habit.interval, it) } ?: habit.anchorDate

    /** Tap cycles 0 -> 1 -> ... -> dosesPerDay -> 0. */
    fun nextDoseCount(current: Int, dosesPerDay: Int): Int =
        if (current >= dosesPerDay) 0 else current + 1

    /**
     * When the next reminder should fire, or null if reminders are off.
     * 1. N days before the due day, at the reminder time.
     * 2. Otherwise on the due day itself.
     * 3. Once both have passed and it is still not done, once a day at the reminder time
     *    until it is checked off.
     */
    fun nextReminder(habit: Habit, entries: Collection<Entry>, now: LocalDateTime): LocalDateTime? {
        val r = habit.reminder
        if (!r.enabled) return null
        val time = LocalTime(r.minuteOfDay / 60, r.minuteOfDay % 60)
        val due = nextDue(habit, entries)
        val early = if (r.daysBefore > 0) LocalDateTime(due.minus(r.daysBefore, DateTimeUnit.DAY), time) else null
        val onDay = LocalDateTime(due, time)
        listOfNotNull(early, onDay).firstOrNull { it > now }?.let { return it }
        val todayAt = LocalDateTime(now.date, time)
        return if (todayAt > now) todayAt else LocalDateTime(now.date.plus(1, DateTimeUnit.DAY), time)
    }

    /**
     * Cell states for one habit across [days] (ascending).
     * [entries] may contain other habits' entries; they are ignored.
     */
    fun cells(
        habit: Habit,
        entries: Collection<Entry>,
        days: List<LocalDate>,
        today: LocalDate,
        reminderDate: LocalDate? = null,
    ): List<Cell> {
        val own = entries.filter { it.habitId == habit.id }
        val byDate = own.associateBy { it.date }
        val doneDates = own.filter { isDone(habit, it) }.map { it.date }.sorted()
        val nextDue = doneDates.lastOrNull()?.let { add(habit.interval, it) } ?: habit.anchorDate

        return days.map { day ->
            val entry = byDate[day]
            val count = entry?.doseCount ?: 0
            val state = when {
                count >= habit.dosesPerDay -> CellState.DONE
                count > 0 -> CellState.PARTIAL
                else -> {
                    val prev = previousDone(doneDates, day)
                    when {
                        prev != null && day < add(habit.interval, prev) -> CellState.COVERED
                        day == nextDue && day >= today -> CellState.DUE
                        nextDue < today && day >= nextDue && day <= today -> CellState.OVERDUE
                        else -> CellState.IDLE
                    }
                }
            }
            Cell(
                date = day,
                state = state,
                doseCount = count,
                dosesPerDay = habit.dosesPerDay,
                hasNote = !entry?.note.isNullOrBlank(),
                isToday = day == today,
                isReminderDay = reminderDate != null && day == reminderDate && state != CellState.DONE,
                isTappable = day <= today,
            )
        }
    }

    /** Greatest done date strictly before [day]. [sorted] must be ascending. */
    private fun previousDone(sorted: List<LocalDate>, day: LocalDate): LocalDate? {
        val idx = sorted.binarySearch(day)
        val insertion = if (idx >= 0) idx else -idx - 1
        return sorted.getOrNull(insertion - 1)
    }
}
