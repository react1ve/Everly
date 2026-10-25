package app.data

import app.core.model.Entry
import app.core.model.Folder
import app.core.model.Habit
import app.core.model.Interval
import app.core.model.IntervalUnit
import app.core.model.ReminderSettings
import app.core.schedule.ScheduleEngine
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * Stand-in data layer for the vertical slice: the whole dataset as one JSON document
 * in key-value storage. Fine for hundreds of habits and a few years of entries;
 * replaced by SQLDelight in /replica-backend.
 */
class SnapshotHabitRepository(
    private val settings: Settings,
    seedIfEmpty: Boolean,
    today: LocalDate,
) : HabitRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val state = MutableStateFlow(load() ?: if (seedIfEmpty) SampleData.snapshot(today) else Snapshot())

    override val habits: Flow<List<Habit>> =
        state.map { s -> s.habits.sortedWith(compareBy({ it.sortOrder }, { it.name })) }.distinctUntilChanged()
    override val folders: Flow<List<Folder>> =
        state.map { s -> s.folders.sortedBy { it.sortOrder } }.distinctUntilChanged()
    override val entries: Flow<List<Entry>> =
        state.map { it.entries.values.toList() }.distinctUntilChanged()

    override suspend fun habit(id: String): Habit? = state.value.habits.firstOrNull { it.id == id }

    override suspend fun createHabit(draft: HabitDraft): String = mutate { s ->
        val id = Uuid.random().toString()
        val habit = Habit(
            id = id,
            name = draft.name.trim(),
            interval = draft.interval,
            dosesPerDay = draft.dosesPerDay,
            anchorDate = draft.anchorDate,
            colorKey = draft.colorKey,
            folderId = draft.folderId,
            sortOrder = (s.habits.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            reminder = draft.reminder,
        )
        s.copy(habits = s.habits + habit) to id
    }

    override suspend fun updateHabit(id: String, draft: HabitDraft) = mutate { s ->
        s.copy(habits = s.habits.map { h ->
            if (h.id != id) h else h.copy(
                name = draft.name.trim(),
                interval = draft.interval,
                dosesPerDay = draft.dosesPerDay,
                colorKey = draft.colorKey,
                folderId = draft.folderId,
                reminder = draft.reminder,
            )
        }) to Unit
    }

    override suspend fun deleteHabit(id: String) = mutate { s ->
        s.copy(
            habits = s.habits.filterNot { it.id == id },
            entries = s.entries.filterKeys { it.habitId != id },
        ) to Unit
    }

    override suspend fun toggleDay(habitId: String, date: LocalDate): Int = mutate { s ->
        val habit = s.habits.firstOrNull { it.id == habitId } ?: return@mutate s to 0
        val key = EntryKey(habitId, date)
        val current = s.entries[key]
        val next = ScheduleEngine.nextDoseCount(current?.doseCount ?: 0, habit.dosesPerDay)
        val updated = when {
            next == 0 && current?.note.isNullOrBlank() -> s.entries - key
            else -> s.entries + (key to Entry(habitId, date, next, current?.note))
        }
        s.copy(entries = updated) to next
    }

    override suspend fun setNote(habitId: String, date: LocalDate, note: String?, doseCount: Int) = mutate { s ->
        val key = EntryKey(habitId, date)
        val clean = note?.trim()?.takeIf { it.isNotEmpty() }
        val updated = if (clean == null && doseCount == 0) s.entries - key
        else s.entries + (key to Entry(habitId, date, doseCount, clean))
        s.copy(entries = updated) to Unit
    }

    override suspend fun markDone(habitId: String, date: LocalDate) = mutate { s ->
        val habit = s.habits.firstOrNull { it.id == habitId } ?: return@mutate s to Unit
        val key = EntryKey(habitId, date)
        s.copy(entries = s.entries + (key to Entry(habitId, date, habit.dosesPerDay, s.entries[key]?.note))) to Unit
    }

    override suspend fun deleteEntry(habitId: String, date: LocalDate) = mutate { s ->
        s.copy(entries = s.entries - EntryKey(habitId, date)) to Unit
    }

    override suspend fun folder(id: String): Folder? = state.value.folders.firstOrNull { it.id == id }

    override suspend fun createFolder(name: String, colorKey: String): String = mutate { s ->
        val id = Uuid.random().toString()
        val folder = Folder(id, name.trim(), colorKey, (s.folders.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        s.copy(folders = s.folders + folder) to id
    }

    override suspend fun updateFolder(id: String, name: String, colorKey: String) = mutate { s ->
        s.copy(folders = s.folders.map { if (it.id == id) it.copy(name = name.trim(), colorKey = colorKey) else it }) to Unit
    }

    override suspend fun deleteFolder(id: String) = mutate { s ->
        s.copy(
            folders = s.folders.filterNot { it.id == id },
            habits = s.habits.map { if (it.folderId == id) it.copy(folderId = null) else it },
        ) to Unit
    }

    override suspend fun setFolderCollapsed(id: String, collapsed: Boolean) = mutate { s ->
        s.copy(folders = s.folders.map { if (it.id == id) it.copy(collapsed = collapsed) else it }) to Unit
    }

    override suspend fun moveHabit(habitId: String, folderId: String?) = mutate { s ->
        val last = s.habits.filter { it.folderId == folderId }.maxOfOrNull { it.sortOrder } ?: -1
        s.copy(habits = s.habits.map { if (it.id == habitId) it.copy(folderId = folderId, sortOrder = last + 1) else it }) to Unit
    }

    override suspend fun shiftHabit(habitId: String, delta: Int) = mutate { s ->
        val h = s.habits.firstOrNull { it.id == habitId } ?: return@mutate s to Unit
        val group = s.habits.filter { it.folderId == h.folderId }.sortedBy { it.sortOrder }
        val i = group.indexOfFirst { it.id == habitId }
        val other = group.getOrNull(i + delta) ?: return@mutate s to Unit
        s.copy(habits = s.habits.map {
            when (it.id) {
                h.id -> it.copy(sortOrder = other.sortOrder)
                other.id -> it.copy(sortOrder = h.sortOrder)
                else -> it
            }
        }.normalised()) to Unit
    }

    override suspend fun shiftFolder(folderId: String, delta: Int) = mutate { s ->
        val sorted = s.folders.sortedBy { it.sortOrder }
        val i = sorted.indexOfFirst { it.id == folderId }
        val j = i + delta
        if (i < 0 || j !in sorted.indices) return@mutate s to Unit
        val reordered = sorted.toMutableList().apply { add(j, removeAt(i)) }
        s.copy(folders = reordered.mapIndexed { idx, f -> f.copy(sortOrder = idx) }) to Unit
    }

    override suspend fun exportBackup(): String = json.encodeToString(SnapshotDto.serializer(), state.value.toDto())

    override suspend fun importBackup(json: String): BackupSummary {
        val parsed = try {
            this.json.decodeFromString(SnapshotDto.serializer(), json).also {
                if (it.schemaVersion > SnapshotDto.CURRENT_VERSION) {
                    throw BackupFormatException(BackupFormatException.Reason.NEWER_VERSION)
                }
            }.toDomain()
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: Exception) {
            throw BackupFormatException(BackupFormatException.Reason.NOT_A_BACKUP, e)
        }
        mutate { parsed to Unit }
        return BackupSummary(parsed.habits.size, parsed.folders.size, parsed.entries.size)
    }

    override suspend fun deleteAll() = mutate { Snapshot() to Unit }

    // --- internals ---

    /** Sort orders stay 0..n-1 after swaps, so equal values can't creep in. */
    private fun List<Habit>.normalised(): List<Habit> =
        sortedBy { it.sortOrder }.mapIndexed { i, h -> h.copy(sortOrder = i) }

    private suspend fun <T> mutate(block: (Snapshot) -> Pair<Snapshot, T>): T = mutex.withLock {
        val (next, result) = block(state.value)
        state.value = next
        save(next)
        result
    }

    private fun load(): Snapshot? = try {
        settings.getStringOrNull(KEY)?.let { json.decodeFromString(SnapshotDto.serializer(), it).toDomain() }
    } catch (e: Exception) {
        Logger.e(e) { "Could not read saved data; starting empty" }
        null
    }

    private fun save(s: Snapshot) {
        settings.putString(KEY, json.encodeToString(SnapshotDto.serializer(), s.toDto()))
    }

    private companion object {
        const val KEY = "snapshot.v1"
    }
}

internal data class EntryKey(val habitId: String, val date: LocalDate)

internal data class Snapshot(
    val habits: List<Habit> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val entries: Map<EntryKey, Entry> = emptyMap(),
)

// --- persistence DTOs (also the shape of the future backup file) ---

@Serializable
internal data class SnapshotDto(
    val schemaVersion: Int = CURRENT_VERSION,
    val habits: List<HabitDto> = emptyList(),
    val folders: List<FolderDto> = emptyList(),
    val entries: List<EntryDto> = emptyList(),
) {
    companion object { const val CURRENT_VERSION = 1 }
}

@Serializable
internal data class HabitDto(
    val id: String, val name: String, val intervalCount: Int, val intervalUnit: String,
    val dosesPerDay: Int, val anchorDate: String, val colorKey: String, val folderId: String? = null,
    val sortOrder: Int, val reminderEnabled: Boolean = false, val reminderMinuteOfDay: Int = 540,
    val remindDaysBefore: Int = 0,
)

@Serializable
internal data class FolderDto(val id: String, val name: String, val colorKey: String, val sortOrder: Int, val collapsed: Boolean = false)

@Serializable
internal data class EntryDto(val habitId: String, val date: String, val doseCount: Int, val note: String? = null)

internal fun Snapshot.toDto() = SnapshotDto(
    habits = habits.map {
        HabitDto(it.id, it.name, it.interval.count, it.interval.unit.name, it.dosesPerDay, it.anchorDate.toString(),
            it.colorKey, it.folderId, it.sortOrder, it.reminder.enabled, it.reminder.minuteOfDay, it.reminder.daysBefore)
    },
    folders = folders.map { FolderDto(it.id, it.name, it.colorKey, it.sortOrder, it.collapsed) },
    entries = entries.values.map { EntryDto(it.habitId, it.date.toString(), it.doseCount, it.note) },
)

internal fun SnapshotDto.toDomain() = Snapshot(
    habits = habits.map {
        Habit(
            id = it.id, name = it.name,
            interval = Interval(it.intervalCount, IntervalUnit.valueOf(it.intervalUnit)),
            dosesPerDay = it.dosesPerDay, anchorDate = LocalDate.parse(it.anchorDate), colorKey = it.colorKey,
            folderId = it.folderId, sortOrder = it.sortOrder,
            reminder = ReminderSettings(it.reminderEnabled, it.reminderMinuteOfDay, it.remindDaysBefore),
        )
    },
    folders = folders.map { Folder(it.id, it.name, it.colorKey, it.sortOrder, it.collapsed) },
    entries = entries.associate {
        val date = LocalDate.parse(it.date)
        EntryKey(it.habitId, date) to Entry(it.habitId, date, it.doseCount, it.note)
    },
)
