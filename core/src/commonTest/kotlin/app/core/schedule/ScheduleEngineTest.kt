package app.core.schedule

import app.core.model.Entry
import app.core.model.Habit
import app.core.model.Interval
import app.core.model.IntervalUnit
import app.core.model.ReminderSettings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleEngineTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun habit(
        interval: Interval = Interval.Daily,
        doses: Int = 1,
        anchor: String = "2026-10-01",
        reminder: ReminderSettings = ReminderSettings(),
    ) = Habit("h", "Test", interval, doses, d(anchor), "blue", reminder = reminder)

    private fun done(date: String, count: Int = 1, note: String? = null) = Entry("h", d(date), count, note)
    private fun range(from: String, to: String): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var x = d(from)
        while (x <= d(to)) { out += x; x = ScheduleEngine.add(Interval.Daily, x) }
        return out
    }

    // --- interval arithmetic ---

    @Test fun monthEndClampsInLeapAndNonLeapYears() {
        val monthly = Interval(1, IntervalUnit.MONTH)
        assertEquals(d("2026-02-28"), ScheduleEngine.add(monthly, d("2026-01-31")))
        assertEquals(d("2028-02-29"), ScheduleEngine.add(monthly, d("2028-01-31")))
        assertEquals(d("2026-04-30"), ScheduleEngine.add(monthly, d("2026-03-31")))
    }

    @Test fun yearlyFromLeapDayClamps() {
        assertEquals(d("2029-02-28"), ScheduleEngine.add(Interval(1, IntervalUnit.YEAR), d("2028-02-29")))
        assertEquals(d("2030-10-17"), ScheduleEngine.add(Interval(4, IntervalUnit.YEAR), d("2026-10-17")))
    }

    @Test fun weeksAndLargeMonthCounts() {
        assertEquals(d("2026-10-29"), ScheduleEngine.add(Interval(2, IntervalUnit.WEEK), d("2026-10-15")))
        assertEquals(d("2028-01-31"), ScheduleEngine.add(Interval(15, IntervalUnit.MONTH), d("2026-10-31")))
    }

    // --- rolling schedule ---

    @Test fun nextDueIsAnchorWhenNeverDone() {
        assertEquals(d("2026-10-01"), ScheduleEngine.nextDue(habit(), emptyList()))
    }

    @Test fun lateCompletionRollsTheScheduleFromCompletionDay() {
        val h = habit(Interval(3, IntervalUnit.MONTH), anchor = "2026-07-01")
        // due Jul 1, done late on Jul 20 -> next due Oct 20, not Oct 1
        assertEquals(d("2026-10-20"), ScheduleEngine.nextDue(h, listOf(done("2026-07-20"))))
    }

    @Test fun partialDosesDoNotCountAsDone() {
        val h = habit(doses = 2)
        assertEquals(d("2026-10-01"), ScheduleEngine.nextDue(h, listOf(done("2026-10-01", count = 1))))
        assertEquals(d("2026-10-02"), ScheduleEngine.nextDue(h, listOf(done("2026-10-01", count = 2))))
    }

    @Test fun doseCountCycles() {
        assertEquals(1, ScheduleEngine.nextDoseCount(0, 2))
        assertEquals(2, ScheduleEngine.nextDoseCount(1, 2))
        assertEquals(0, ScheduleEngine.nextDoseCount(2, 2))
        assertEquals(0, ScheduleEngine.nextDoseCount(1, 1))
    }

    // --- cell states ---

    @Test fun everyThreeDaysShowsCoveredThenDue() {
        val h = habit(Interval(3, IntervalUnit.DAY), anchor = "2026-10-10")
        val cells = ScheduleEngine.cells(h, listOf(done("2026-10-15")), range("2026-10-15", "2026-10-19"), today = d("2026-10-17"))
        assertEquals(
            listOf(CellState.DONE, CellState.COVERED, CellState.COVERED, CellState.DUE, CellState.IDLE),
            cells.map { it.state },
        )
        assertTrue(cells[2].isToday)
        assertTrue(cells[2].isTappable)
        assertFalse(cells[3].isTappable)
    }

    @Test fun overdueRunsFromDueDayToToday() {
        val h = habit(Interval(1, IntervalUnit.WEEK), anchor = "2026-10-01")
        val cells = ScheduleEngine.cells(h, listOf(done("2026-10-01")), range("2026-10-07", "2026-10-11"), today = d("2026-10-10"))
        // due Oct 8, not done by Oct 10
        assertEquals(
            listOf(CellState.COVERED, CellState.OVERDUE, CellState.OVERDUE, CellState.OVERDUE, CellState.IDLE),
            cells.map { it.state },
        )
    }

    @Test fun dailyHabitHighlightsTomorrowOnlyAfterTodayIsDone() {
        val h = habit(anchor = "2026-10-16")
        val before = ScheduleEngine.cells(h, listOf(done("2026-10-16")), range("2026-10-17", "2026-10-18"), today = d("2026-10-17"))
        assertEquals(listOf(CellState.DUE, CellState.IDLE), before.map { it.state })

        val after = ScheduleEngine.cells(h, listOf(done("2026-10-16"), done("2026-10-17")), range("2026-10-17", "2026-10-18"), today = d("2026-10-17"))
        assertEquals(listOf(CellState.DONE, CellState.DUE), after.map { it.state })
    }

    @Test fun partialAndNoteFlags() {
        val h = habit(doses = 2, anchor = "2026-10-17")
        val cells = ScheduleEngine.cells(h, listOf(done("2026-10-17", count = 1, note = "morning")), range("2026-10-17", "2026-10-17"), today = d("2026-10-17"))
        assertEquals(CellState.PARTIAL, cells.single().state)
        assertTrue(cells.single().hasNote)
        assertEquals(1, cells.single().doseCount)
    }

    @Test fun noteOnlyEntryIsNotDone() {
        val h = habit(anchor = "2026-10-17")
        val cells = ScheduleEngine.cells(h, listOf(done("2026-10-17", count = 0, note = "skipped, travelling")), range("2026-10-17", "2026-10-17"), today = d("2026-10-17"))
        assertEquals(CellState.DUE, cells.single().state)
        assertTrue(cells.single().hasNote)
    }

    @Test fun otherHabitsEntriesAreIgnored() {
        val h = habit(anchor = "2026-10-17")
        val cells = ScheduleEngine.cells(h, listOf(Entry("other", d("2026-10-17"), 1)), range("2026-10-17", "2026-10-17"), today = d("2026-10-17"))
        assertEquals(CellState.DUE, cells.single().state)
    }

    // --- reminders ---

    private fun at(s: String) = LocalDateTime.parse(s)

    @Test fun reminderOffReturnsNull() {
        assertNull(ScheduleEngine.nextReminder(habit(), emptyList(), at("2026-10-17T08:00")))
    }

    @Test fun reminderDaysBeforeThenOnTheDay() {
        val h = habit(Interval(1, IntervalUnit.YEAR), anchor = "2026-10-20", reminder = ReminderSettings(true, 9 * 60, 2))
        assertEquals(at("2026-10-18T09:00"), ScheduleEngine.nextReminder(h, emptyList(), at("2026-10-17T12:00")))
        assertEquals(at("2026-10-20T09:00"), ScheduleEngine.nextReminder(h, emptyList(), at("2026-10-18T10:00")))
    }

    @Test fun overdueReminderRepeatsDailyAtTheSameTime() {
        val h = habit(anchor = "2026-10-15", reminder = ReminderSettings(true, 20 * 60 + 30, 0))
        assertEquals(at("2026-10-17T20:30"), ScheduleEngine.nextReminder(h, emptyList(), at("2026-10-17T12:00")))
        assertEquals(at("2026-10-18T20:30"), ScheduleEngine.nextReminder(h, emptyList(), at("2026-10-17T21:00")))
    }

    @Test fun checkingOffMovesTheReminderToTheNextDueDay() {
        val h = habit(Interval(3, IntervalUnit.DAY), anchor = "2026-10-17", reminder = ReminderSettings(true, 9 * 60, 0))
        assertEquals(
            LocalDateTime(d("2026-10-20"), LocalTime(9, 0)),
            ScheduleEngine.nextReminder(h, listOf(done("2026-10-17")), at("2026-10-17T10:00")),
        )
    }
}
