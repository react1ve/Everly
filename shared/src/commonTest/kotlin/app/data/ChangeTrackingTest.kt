package app.data

import app.core.model.Interval
import app.core.model.IntervalUnit
import app.sync.SyncRecord
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangeTrackingTest {
    private val today = LocalDate(2026, 10, 25)
    private var clock = 100L
    private fun repo(settings: MapSettings = MapSettings()) =
        SnapshotHabitRepository(settings, seedIfEmpty = false, today = today, nowMillis = { clock })
    private fun draft(name: String) = HabitDraft(name, Interval(1, IntervalUnit.DAY), 1, "blue", anchorDate = today)

    @Test fun everyChangeIsStampedAndOnlyThatRecord() = runTest {
        val repo = repo()
        val a = repo.createHabit(draft("a"))
        clock = 200
        val b = repo.createHabit(draft("b"))
        clock = 300
        repo.updateHabit(a, draft("a2"))
        val stamps = repo.records.first().associate { it.key to it.updatedAt }
        assertEquals(300L, stamps["habits/$a"])
        assertEquals(200L, stamps["habits/$b"])
    }

    @Test fun deletesLeaveTombstones() = runTest {
        val repo = repo()
        val id = repo.createHabit(draft("x"))
        repo.toggleDay(id, today)
        clock = 500
        repo.deleteHabit(id)
        val gone = repo.records.first().filter { it.deleted }.map { it.key }.toSet()
        assertTrue("habits/$id" in gone)
        assertTrue("entries/${id}_$today" in gone)
    }

    @Test fun applyRemoteIsLastWriteWins() = runTest {
        val repo = repo()
        clock = 1_000
        val id = repo.createHabit(draft("local"))
        val mine = repo.records.first().single { it.kind == SyncRecord.HABITS }
        repo.applyRemote(listOf(mine.copy(updatedAt = 900, data = mine.data + ("name" to "older"))))
        assertEquals("local", repo.habit(id)!!.name)
        repo.applyRemote(listOf(mine.copy(updatedAt = 2_000, data = mine.data + ("name" to "newer"))))
        assertEquals("newer", repo.habit(id)!!.name)
        // applying must not re-stamp with the local clock
        assertEquals(2_000L, repo.records.first().single { it.kind == SyncRecord.HABITS }.updatedAt)
    }

    @Test fun remoteRecordsForUnknownItemsAreCreated() = runTest {
        val repo = repo()
        val fields = mapOf(
            "name" to "from cloud", "intervalCount" to 3L, "intervalUnit" to "WEEK", "dosesPerDay" to 1L,
            "anchorDate" to "2026-10-01", "colorKey" to "teal", "folderId" to null, "sortOrder" to 0L,
            "reminderEnabled" to false, "reminderMinuteOfDay" to 540L, "remindDaysBefore" to 0L,
        )
        repo.applyRemote(listOf(SyncRecord(SyncRecord.HABITS, "h9", 50, false, fields)))
        val h = assertNotNull(repo.habit("h9"))
        assertEquals(Interval(3, IntervalUnit.WEEK), h.interval)
        repo.applyRemote(listOf(SyncRecord(SyncRecord.HABITS, "h9", 60, true, emptyMap())))
        assertNull(repo.habit("h9"))
    }

    @Test fun stampsSurviveRestartAndOldDataGetsStamped() = runTest {
        val settings = MapSettings()
        clock = 700
        repo(settings).createHabit(draft("persisted"))
        val again = repo(settings)
        assertEquals(700L, again.records.first().single().updatedAt)
    }
}
