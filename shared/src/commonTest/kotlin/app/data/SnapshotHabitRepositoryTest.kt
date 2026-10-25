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

    @Test fun shiftAndMoveKeepGroupsInOrder() = runTest {
        val repo = SnapshotHabitRepository(MapSettings(), seedIfEmpty = false, today = today)
        val a = repo.createHabit(draft().copy(name = "a"))
        val b = repo.createHabit(draft().copy(name = "b"))
        val c = repo.createHabit(draft().copy(name = "c"))
        repo.shiftHabit(c, -1)
        assertEquals(listOf("a", "c", "b"), repo.habits.first().map { it.name })
        repo.shiftHabit(a, -1) // already first: no-op
        assertEquals(listOf("a", "c", "b"), repo.habits.first().map { it.name })
        val f = repo.createFolder("F", "teal")
        repo.moveHabit(a, f)
        assertEquals(f, repo.habit(a)!!.folderId)
    }

    @Test fun placeHabitMovesAcrossGroupsAtIndex() = runTest {
        val repo = SnapshotHabitRepository(MapSettings(), seedIfEmpty = false, today = today)
        val a = repo.createHabit(draft().copy(name = "a"))
        val b = repo.createHabit(draft().copy(name = "b"))
        val c = repo.createHabit(draft().copy(name = "c"))
        val f = repo.createFolder("F", "teal")
        repo.placeHabit(a, f, 0)
        repo.placeHabit(c, f, 0)
        suspend fun group(id: String?) = repo.habits.first().filter { it.folderId == id }.map { it.name }
        assertEquals(listOf("c", "a"), group(f))
        assertEquals(listOf("b"), group(null))
        repo.placeHabit(c, f, 5) // past the end: goes last
        assertEquals(listOf("a", "c"), group(f))
        repo.placeHabit(a, null, 0)
        assertEquals(listOf("a", "b"), group(null))
    }

    @Test fun reorderFoldersFollowsTheGivenIds() = runTest {
        val repo = SnapshotHabitRepository(MapSettings(), seedIfEmpty = false, today = today)
        val x = repo.createFolder("X", "teal")
        val y = repo.createFolder("Y", "teal")
        val z = repo.createFolder("Z", "teal")
        repo.reorderFolders(listOf(z, x))
        assertEquals(listOf(z, x, y), repo.folders.first().map { it.id })
    }

    @Test fun backupRoundTripReplacesEverything() = runTest {
        val source = SnapshotHabitRepository(MapSettings(), seedIfEmpty = true, today = today)
        val json = source.exportBackup()
        val target = SnapshotHabitRepository(MapSettings(), seedIfEmpty = false, today = today)
        target.createHabit(draft().copy(name = "will be replaced"))
        val summary = target.importBackup(json)
        assertEquals(source.habits.first(), target.habits.first())
        assertEquals(source.entries.first().toSet(), target.entries.first().toSet())
        assertEquals(source.habits.first().size, summary.habits)
    }

    @Test fun badBackupIsRejectedAndChangesNothing() = runTest {
        val repo = SnapshotHabitRepository(MapSettings(), seedIfEmpty = false, today = today)
        repo.createHabit(draft())
        kotlin.test.assertFailsWith<BackupFormatException> { repo.importBackup("{ not json") }
        kotlin.test.assertFailsWith<BackupFormatException> { repo.importBackup("""{"schemaVersion": 99}""") }
        assertEquals(1, repo.habits.first().size)
    }

    @Test fun deleteAllEmptiesTheApp() = runTest {
        val repo = SnapshotHabitRepository(MapSettings(), seedIfEmpty = true, today = today)
        repo.deleteAll()
        assertTrue(repo.habits.first().isEmpty())
        assertTrue(repo.entries.first().isEmpty())
    }

    @Test fun markDoneIsIdempotentAndKeepsTheNote() = runTest {
        val repo = SnapshotHabitRepository(MapSettings(), seedIfEmpty = false, today = today)
        val id = repo.createHabit(draft(doses = 3))
        repo.setNote(id, today, "half way", 1)
        repo.markDone(id, today)
        repo.markDone(id, today)
        val e = repo.entries.first().single()
        assertEquals(3, e.doseCount)
        assertEquals("half way", e.note)
    }
}
