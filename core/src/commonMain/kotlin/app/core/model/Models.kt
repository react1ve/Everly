package app.core.model

import kotlinx.datetime.LocalDate

enum class IntervalUnit { DAY, WEEK, MONTH, YEAR }

data class Interval(val count: Int, val unit: IntervalUnit) {
    init {
        require(count in 1..MAX_COUNT) { "interval count must be 1..$MAX_COUNT, was $count" }
    }

    val isDaily: Boolean get() = unit == IntervalUnit.DAY && count == 1

    companion object {
        const val MAX_COUNT = 999
        val Daily = Interval(1, IntervalUnit.DAY)
    }
}

data class ReminderSettings(
    val enabled: Boolean = false,
    /** Local wall-clock time, minutes after midnight. 540 = 09:00. */
    val minuteOfDay: Int = 9 * 60,
    /** 0 = on the due day only. */
    val daysBefore: Int = 0,
) {
    init {
        require(minuteOfDay in 0..1439)
        require(daysBefore in 0..30)
    }
}

data class Habit(
    val id: String,
    val name: String,
    val interval: Interval,
    val dosesPerDay: Int = 1,
    /** First due date while the habit has never been completed. */
    val anchorDate: LocalDate,
    val colorKey: String,
    val folderId: String? = null,
    val sortOrder: Int = 0,
    val reminder: ReminderSettings = ReminderSettings(),
) {
    init {
        require(dosesPerDay in 1..MAX_DOSES)
    }

    companion object {
        const val MAX_DOSES = 12
        const val MAX_NAME = 80
    }
}

data class Folder(
    val id: String,
    val name: String,
    val colorKey: String,
    val sortOrder: Int = 0,
    val collapsed: Boolean = false,
)

/** One per habit per calendar day (mirrors the UNIQUE(habit_id, date) constraint in schema.sql). */
data class Entry(
    val habitId: String,
    val date: LocalDate,
    /** 0 means "note only, not checked". */
    val doseCount: Int,
    val note: String? = null,
)
