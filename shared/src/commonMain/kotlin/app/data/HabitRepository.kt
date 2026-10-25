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
    /** Sets the day to fully done (all doses), keeping any note. Idempotent — safe for notification actions. */
    suspend fun markDone(habitId: String, date: LocalDate)
    suspend fun deleteEntry(habitId: String, date: LocalDate)

    suspend fun folder(id: String): Folder?
    suspend fun createFolder(name: String, colorKey: String): String
    suspend fun updateFolder(id: String, name: String, colorKey: String)
    /** Habits in the folder fall back to "no folder"; nothing else is deleted. */
    suspend fun deleteFolder(id: String)
    suspend fun setFolderCollapsed(id: String, collapsed: Boolean)

    /** Moves a habit to another folder (null = none), at the end of that group. */
    suspend fun moveHabit(habitId: String, folderId: String?)
    /** Swaps with the neighbour in the same group: -1 = up, +1 = down. */
    suspend fun shiftHabit(habitId: String, delta: Int)
    suspend fun shiftFolder(folderId: String, delta: Int)
    /** Drag-and-drop: puts a habit in [folderId] (null = none) at [index] within that group. */
    suspend fun placeHabit(habitId: String, folderId: String?, index: Int)
    /** Drag-and-drop: folders take the order of [ids]; any not listed keep their order after them. */
    suspend fun reorderFolders(ids: List<String>)

    /** The whole dataset as a versioned JSON backup. */
    suspend fun exportBackup(): String
    /** Replaces everything with the backup. Throws [BackupFormatException] if it can't be read. */
    suspend fun importBackup(json: String): BackupSummary
    suspend fun deleteAll()
}

data class BackupSummary(val habits: Int, val folders: Int, val entries: Int)
class BackupFormatException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NEWER_VERSION, NOT_A_BACKUP }
}
