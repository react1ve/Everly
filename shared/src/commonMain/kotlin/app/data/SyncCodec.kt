package app.data

import app.core.model.Entry
import app.core.model.Folder
import app.core.model.Habit
import app.core.model.Interval
import app.core.model.IntervalUnit
import app.core.model.ReminderSettings
import app.sync.SyncRecord
import kotlinx.datetime.LocalDate

/** Domain <-> plain field maps for sync. Field names are the cloud schema; keep them stable. */
internal object SyncCodec {

    fun entryDocId(habitId: String, date: LocalDate) = "${habitId}_$date"

    fun habitFields(h: Habit): Map<String, Any?> = mapOf(
        "name" to h.name,
        "intervalCount" to h.interval.count,
        "intervalUnit" to h.interval.unit.name,
        "dosesPerDay" to h.dosesPerDay,
        "anchorDate" to h.anchorDate.toString(),
        "colorKey" to h.colorKey,
        "folderId" to h.folderId,
        "sortOrder" to h.sortOrder,
        "reminderEnabled" to h.reminder.enabled,
        "reminderMinuteOfDay" to h.reminder.minuteOfDay,
        "remindDaysBefore" to h.reminder.daysBefore,
    )

    fun folderFields(f: Folder): Map<String, Any?> = mapOf(
        "name" to f.name,
        "colorKey" to f.colorKey,
        "sortOrder" to f.sortOrder,
        "collapsed" to f.collapsed,
    )

    fun entryFields(e: Entry): Map<String, Any?> = mapOf(
        "habitId" to e.habitId,
        "date" to e.date.toString(),
        "doseCount" to e.doseCount,
        "note" to e.note,
    )

    /** All live records of a snapshot, keyed by "kind/id", as field maps (used for change detection too). */
    fun fieldsByKey(s: Snapshot): Map<String, Map<String, Any?>> = buildMap {
        s.habits.forEach { put("${SyncRecord.HABITS}/${it.id}", habitFields(it)) }
        s.folders.forEach { put("${SyncRecord.FOLDERS}/${it.id}", folderFields(it)) }
        s.entries.values.forEach { put("${SyncRecord.ENTRIES}/${entryDocId(it.habitId, it.date)}", entryFields(it)) }
    }

    fun toHabit(id: String, d: Map<String, Any?>): Habit? = runCatching {
        Habit(
            id = id,
            name = d.str("name")!!,
            interval = Interval(d.int("intervalCount") ?: 1, IntervalUnit.valueOf(d.str("intervalUnit") ?: "DAY")),
            dosesPerDay = d.int("dosesPerDay") ?: 1,
            anchorDate = LocalDate.parse(d.str("anchorDate")!!),
            colorKey = d.str("colorKey") ?: "blue",
            folderId = d.str("folderId"),
            sortOrder = d.int("sortOrder") ?: 0,
            reminder = ReminderSettings(
                enabled = d["reminderEnabled"] as? Boolean ?: false,
                minuteOfDay = d.int("reminderMinuteOfDay") ?: 540,
                daysBefore = d.int("remindDaysBefore") ?: 0,
            ),
        )
    }.getOrNull()

    fun toFolder(id: String, d: Map<String, Any?>): Folder? = runCatching {
        Folder(id, d.str("name")!!, d.str("colorKey") ?: "teal", d.int("sortOrder") ?: 0, d["collapsed"] as? Boolean ?: false)
    }.getOrNull()

    fun toEntry(d: Map<String, Any?>): Entry? = runCatching {
        Entry(d.str("habitId")!!, LocalDate.parse(d.str("date")!!), d.int("doseCount") ?: 0, d.str("note"))
    }.getOrNull()

    private fun Map<String, Any?>.str(k: String) = this[k] as? String
    private fun Map<String, Any?>.int(k: String) = (this[k] as? Number)?.toInt()
}
