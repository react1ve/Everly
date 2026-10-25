package app.data

import app.core.model.Interval
import app.core.model.IntervalUnit
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnapshotHabitRepositoryTest {
    private val today = LocalDate(2026, 10, 25)
    private fun draft(doses: Int = 1) = HabitDraft("💊 Test", Interval(1, IntervalUnit.DAY), doses, "blue", anchorDate = today)

    @Test fun toggleCyclesDosesAndRemovesEmptyEntry() = runTest {
        val repo = SnapshotHabitRepository(MapSettings(), seedIfEmpty = false, today = today)
        val id = repo.createHabit(draft(doses = 2))
        assertEquals(1, repo.toggleDay(id, today))
        assertEquals(2, repo.toggleDay(id, today))
        assertEquals(0, repo.toggleDay(id, today))
        assertTrue(repo.entries.first().isEmpty())
    }

    @Test fun oneEntryPerDayEvenAfterManyTaps() = runTest {
        val repo = SnapshotHabitRepository(MapSettings(), seedIfEmpty = false, today = today)
        val id = repo.createHabit(draft())
        repeat(5) { repo.toggleDay(id, today) }
        assertEquals(1, repo.entries.first().size)
    }

    @Test fun noteSurvivesUncheck() = runTest {
        val repo = SnapshotHabitRepository(MapSettings(), seedIfEmpty = false, today = today)
        val id = repo.createHabit(draft())
        repo.setNote(id, today, "felt great", doseCount = 1)
        repo.toggleDay(id, today) // 1 -> 0, note stays
        val e = repo.entries.first().single()
        assertEquals(0, e.doseCount)
        assertEquals("felt great", e.note)
    }

    @Test fun dataPersistsAcrossInstances() = runTest {
        val settings = MapSettings()
        val first = SnapshotHabitRepository(settings, seedIfEmpty = false, today = today)
        val id = first.createHabit(draft())
        first.toggleDay(id, today)
        val second = SnapshotHabitRepository(settings, seedIfEmpty = true, today = today)
        assertEquals(listOf("💊 Test"), second.habits.first().map { it.name })
        assertEquals(1, second.entries.first().size)
    }

    @Test fun deleteRemovesHistory() = runTest {
        val repo = SnapshotHabitRepository(MapSettings(), seedIfEmpty = false, today = today)
        val id = repo.createHabit(draft())
        repo.toggleDay(id, today)
        repo.deleteHabit(id)
        assertNull(repo.habit(id))
        assertTrue(repo.entries.first().isEmpty())
    }

    @Test fun seedOnlyWhenEmpty() = runTest {
        val repo = SnapshotHabitRepository(MapSettings(), seedIfEmpty = true, today = today)
        assertTrue(repo.habits.first().isNotEmpty())
    }

    @Test fun deletingAFolderKeepsItsHabitsUngrouped() = runTest {
        val repo = SnapshotHabitRepository(MapSettings(), seedIfEmpty = false, today = today)
        val folder = repo.createFolder("Home", "teal")
        val id = repo.createHabit(draft().copy(folderId = folder))
        repo.setFolderCollapsed(folder, true)
        assertTrue(repo.folders.first().single().collapsed)
        repo.deleteFolder(folder)
        assertTrue(repo.folders.first().isEmpty())
        assertNull(repo.habit(id)!!.folderId)
    }

    @Test fun deleteEntryRemovesOnlyThatDay() = runTest {
        val repo = SnapshotHabitRepository(MapSettings(), seedIfEmpty = false, today = today)
        val id = repo.createHabit(draft())
        repo.setNote(id, today, "a", 1)
        repo.setNote(id, LocalDate(2026, 10, 24), "b", 1)
        repo.deleteEntry(id, today)
        assertEquals(listOf("b"), repo.entries.first().map { it.note })
    }
}
