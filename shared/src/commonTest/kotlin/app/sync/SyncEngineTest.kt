package app.sync

import app.core.model.Interval
import app.core.model.IntervalUnit
import app.data.HabitDraft
import app.data.SnapshotHabitRepository
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeBackend : SyncBackend {
    val cloud = mutableMapOf<String, SyncRecord>()
    private val changes = MutableSharedFlow<RemoteBatch>(extraBufferCapacity = 64)
    var pushes = 0

    override suspend fun push(uid: String, records: List<SyncRecord>) {
        pushes++
        records.forEach { cloud[it.key] = it }
    }

    override fun observe(uid: String): Flow<RemoteBatch> =
        changes.onSubscription { emit(RemoteBatch(cloud.values.toList(), fromCache = false)) }

    /** Another device writes. */
    suspend fun remoteWrite(r: SyncRecord) {
        cloud[r.key] = r
        changes.emit(RemoteBatch(listOf(r), fromCache = false))
    }

    override suspend fun deleteAll(uid: String) = cloud.clear()
}

private class FakeAccounts : AccountService {
    override val isAvailable = true
    override val providers = listOf(Provider.GOOGLE)
    override val account = MutableStateFlow<Account?>(null)
    override suspend fun signIn(provider: Provider): AuthResult {
        account.value = Account("u1", "a@b.c", "A", provider); return AuthResult.Success
    }
    override suspend fun signOut() { account.value = null }
    override suspend fun deleteAccount(): AuthResult { account.value = null; return AuthResult.Success }
}

/** The engine lives in backgroundScope, which advanceUntilIdle() doesn't drive; move virtual time instead. */
private fun TestScope.settle() {
    advanceTimeBy(1_000)
    runCurrent()
}

class SyncEngineTest {
    private val today = LocalDate(2026, 10, 25)
    private var clock = 1_000L
    private fun repo() = SnapshotHabitRepository(MapSettings(), seedIfEmpty = false, today = today, nowMillis = { clock })
    private fun draft(name: String) = HabitDraft(name, Interval(1, IntervalUnit.DAY), 1, "blue", anchorDate = today)

    private fun TestScope.engine(repo: SnapshotHabitRepository, backend: FakeBackend, accounts: FakeAccounts) =
        SyncEngine(repo, backend, accounts, nowMillis = { clock }, scope = backgroundScope, pushDebounceMillis = 10).also { it.start() }

    @Test fun nothingLeavesTheDeviceWhenSignedOut() = runTest {
        val repo = repo(); val backend = FakeBackend(); val accounts = FakeAccounts()
        val engine = engine(repo, backend, accounts)
        repo.createHabit(draft("local"))
        settle()
        assertEquals(0, backend.pushes)
        assertEquals(SyncStatus.SignedOut, engine.status.value)
    }

    @Test fun signingInPushesLocalData() = runTest {
        val repo = repo(); val backend = FakeBackend(); val accounts = FakeAccounts()
        engine(repo, backend, accounts)
        val id = repo.createHabit(draft("water plants"))
        repo.toggleDay(id, today)
        accounts.signIn(Provider.GOOGLE)
        settle()
        assertTrue(backend.cloud.containsKey("habits/$id"))
        assertTrue(backend.cloud.keys.any { it.startsWith("entries/") })
    }

    @Test fun newerCloudDataWinsAndOlderLocalDataIsNotPushedOverIt() = runTest {
        val repo = repo(); val backend = FakeBackend(); val accounts = FakeAccounts()
        clock = 1_000
        val id = repo.createHabit(draft("old local name"))
        // the cloud already has the same habit, renamed later on another phone
        val local = repo.records.first().single { it.kind == SyncRecord.HABITS }
        backend.cloud[local.key] = local.copy(updatedAt = 5_000, data = local.data + ("name" to "renamed on other phone"))
        engine(repo, backend, accounts)
        accounts.signIn(Provider.GOOGLE)
        settle()
        assertEquals("renamed on other phone", repo.habit(id)!!.name)
        assertEquals("renamed on other phone", backend.cloud[local.key]!!.data["name"])
    }

    @Test fun remoteChangesAndDeletesArriveLive() = runTest {
        val repo = repo(); val backend = FakeBackend(); val accounts = FakeAccounts()
        engine(repo, backend, accounts)
        accounts.signIn(Provider.GOOGLE)
        clock = 2_000
        val id = repo.createHabit(draft("shared"))
        settle()
        val pushed = backend.cloud["habits/$id"]!!
        backend.remoteWrite(pushed.copy(updatedAt = 9_000, deleted = true, data = emptyMap()))
        settle()
        assertEquals(null, repo.habit(id))
        // and a stale echo of the old version doesn't bring it back
        backend.remoteWrite(pushed)
        settle()
        assertEquals(null, repo.habit(id))
    }

    @Test fun localDeleteBecomesATombstoneInTheCloud() = runTest {
        val repo = repo(); val backend = FakeBackend(); val accounts = FakeAccounts()
        engine(repo, backend, accounts)
        accounts.signIn(Provider.GOOGLE)
        clock = 3_000
        val id = repo.createHabit(draft("temp"))
        settle()
        clock = 4_000
        repo.deleteHabit(id)
        settle()
        assertTrue(backend.cloud["habits/$id"]!!.deleted)
    }

    @Test fun deleteAccountClearsCloudAndSignsOut() = runTest {
        val repo = repo(); val backend = FakeBackend(); val accounts = FakeAccounts()
        val engine = engine(repo, backend, accounts)
        accounts.signIn(Provider.GOOGLE)
        repo.createHabit(draft("x"))
        settle()
        assertEquals(AuthResult.Success, engine.deleteAccount())
        assertTrue(backend.cloud.isEmpty())
        assertEquals(null, accounts.account.value)
        assertEquals(1, repo.habits.first().size) // local copy stays
    }
}
