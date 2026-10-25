package app.data

import app.core.model.Entry
import app.core.model.Folder
import app.core.model.Habit
import app.core.model.Interval
import app.core.model.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

data class HabitDraft(
    val name: String,
    val interval: Interval,
    val dosesPerDay: Int = 1,
    val colorKey: String,
    val folderId: String? = null,
    val reminder: ReminderSettings = ReminderSettings(),
    /** Used for new habits only: first due day. */
    val anchorDate: LocalDate,
)

/**
 * The app's data API. Every screen talks to this interface only.
 * v1 implementation: [SnapshotHabitRepository] (JSON in key-value storage).
 * /replica-backend swaps in a SQLDelight implementation of the same interface (schema.sql); no screen changes.
 */
interface HabitRepository {
    val habits: Flow<List<Habit>>
    val folders: Flow<List<Folder>>
    val entries: Flow<List<Entry>>

    suspend fun habit(id: String): Habit?
    suspend fun createHabit(draft: HabitDraft): String
    suspend fun updateHabit(id: String, draft: HabitDraft)
    suspend fun deleteHabit(id: String)

    /** Cycles the dose count for that day (0 -> 1 -> ... -> doses -> 0). Returns the new count. */
    suspend fun toggleDay(habitId: String, date: LocalDate): Int
    /** Upsert one day: dose count + note. Removes the row when both are empty. */
    suspend fun setNote(habitId: String, date: LocalDate, note: String?, doseCount: Int)
    suspend fun deleteEntry(habitId: String, date: LocalDate)

    suspend fun folder(id: String): Folder?
    suspend fun createFolder(name: String, colorKey: String): String
    suspend fun updateFolder(id: String, name: String, colorKey: String)
    /** Habits in the folder fall back to "no folder"; nothing else is deleted. */
    suspend fun deleteFolder(id: String)
    suspend fun setFolderCollapsed(id: String, collapsed: Boolean)
}
