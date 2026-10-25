package app.data

import app.core.model.Entry
import app.core.model.Folder
import app.core.model.Habit
import app.core.model.Interval
import app.core.model.IntervalUnit
import app.core.model.IntervalUnit.DAY
import app.core.model.IntervalUnit.MONTH
import app.core.model.IntervalUnit.WEEK
import app.core.model.IntervalUnit.YEAR
import app.core.model.ReminderSettings
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Development seed so every screen has something real to show. Our own examples, not the original's. */
internal object SampleData {

    fun snapshot(today: LocalDate): Snapshot {
        fun ago(n: Int) = today.minus(n, DateTimeUnit.DAY)
        fun ahead(n: Int) = today.plus(n, DateTimeUnit.DAY)

        val folders = listOf(
            Folder("f-home", "Around the flat", "teal", 0),
            Folder("f-care", "Body", "purple", 1),
        )
        var order = 0
        fun h(id: String, name: String, n: Int, unit: IntervalUnit, color: String, anchor: LocalDate,
              doses: Int = 1, folder: String? = null, remind: Boolean = false) =
            Habit(id, name, Interval(n, unit), doses, anchor, color, folder, order++,
                ReminderSettings(enabled = remind, daysBefore = if (unit == YEAR) 7 else 0))

        val habits = listOf(
            h("h-stretch", "🧘 Morning stretch", 1, DAY, "indigo", ago(40)),
            h("h-omega", "🐟 Omega-3", 1, DAY, "yellow", ago(40), doses = 2),
            h("h-swim", "🏊 Swim", 3, DAY, "cyan", ago(30)),
            h("h-ferns", "🌿 Mist the ferns", 4, DAY, "green", ago(30), folder = "f-home"),
            h("h-towels", "🧺 Wash towels", 1, WEEK, "mint", ago(30), folder = "f-home"),
            h("h-hood", "🍳 Degrease the hood filter", 2, MONTH, "orange", ago(70), folder = "f-home"),
            h("h-eyes", "👓 Eye exam", 1, YEAR, "blue", ahead(12), folder = "f-care", remind = true),
            h("h-blood", "🩸 Donate blood", 3, MONTH, "red", ago(5), folder = "f-care", remind = true),
        )

        val e = mutableListOf<Entry>()
        for (i in 1..35) if (i % 6 != 0) e += Entry("h-stretch", ago(i), 1)
        for (i in 1..30) e += Entry("h-omega", ago(i), if (i % 5 == 0) 1 else 2)
        e += Entry("h-omega", today, 1, "with breakfast")
        listOf(2, 5, 9, 12, 15, 19).forEach { e += Entry("h-swim", ago(it), 1) }
        e += Entry("h-swim", ago(9), 1, "1.5 km, new record")
        listOf(1, 5, 9, 14).forEach { e += Entry("h-ferns", ago(it), 1) }
        listOf(9, 16, 23).forEach { e += Entry("h-towels", ago(it), 1) }
        e += Entry("h-hood", ago(70), 1)

        return Snapshot(
            habits = habits,
            folders = folders,
            entries = e.associateBy { EntryKey(it.habitId, it.date) },
        )
    }
}
