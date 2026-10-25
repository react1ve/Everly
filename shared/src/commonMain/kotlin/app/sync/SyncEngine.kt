package app.sync

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Keeps the local store and the signed-in user's cloud copy in step.
 *
 * - Signed out: nothing leaves the device.
 * - Signed in: listen to the cloud; apply anything newer than ours (last write wins per record);
 *   push local records that are newer than what the cloud has. Pushing only starts after the first
 *   cloud snapshot arrives, so a fresh install can never overwrite newer cloud data with old local data.
 */
@OptIn(FlowPreview::class)
class SyncEngine(
    private val store: SyncableStore,
    private val backend: SyncBackend,
    private val accounts: AccountService,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val pushDebounceMillis: Long = 800,
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.SignedOut)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var session: Job? = null
    private val sessionLock = Mutex()
    private var started = false

    fun start() {
        if (started || !accounts.isAvailable) return
        started = true
        scope.launch {
            accounts.account.map { it?.uid }.distinctUntilChanged().collect { uid ->
                sessionLock.withLock {
                    session?.cancelAndJoin()
                    session = null
                    if (uid == null) {
                        _status.value = SyncStatus.SignedOut
                    } else {
                        _status.value = SyncStatus.Connecting
                        session = scope.launch { runSession(uid) }
                    }
                }
            }
        }
    }

    /** Deletes the cloud copy, then the sign-in account. Local data on this device stays. */
    suspend fun deleteAccount(): AuthResult {
        val uid = accounts.account.value?.uid ?: return AuthResult.Failed(AuthResult.Reason.NO_ACCOUNT)
        sessionLock.withLock { session?.cancelAndJoin(); session = null }
        return try {
            backend.deleteAll(uid)
            accounts.deleteAccount()
        } catch (e: Exception) {
            Logger.e(e) { "Account deletion failed" }
            AuthResult.Failed(AuthResult.Reason.OTHER, e.message)
        }
    }

    private suspend fun runSession(uid: String) = coroutineScope {
        // What the cloud holds, per record key -> updatedAt. Filled by the listener, updated after pushes.
        val cloud = mutableMapOf<String, Long>()
        val cloudLock = Mutex()
        val firstSnapshot = kotlinx.coroutines.CompletableDeferred<Unit>()

        launch {
            backend.observe(uid)
                .catch { e ->
                    Logger.e(e) { "Sync listener failed" }
                    _status.value = SyncStatus.Error(e.message ?: e::class.simpleName.orEmpty())
                }
                .collect { batch ->
                    cloudLock.withLock { batch.records.forEach { r -> cloud[r.key] = maxOf(cloud[r.key] ?: -1L, r.updatedAt) } }
                    store.applyRemote(batch.records)
                    firstSnapshot.complete(Unit)
                    _status.value = SyncStatus.Synced(nowMillis(), pending = batch.fromCache)
                }
        }

        launch {
            firstSnapshot.await()
            store.records.debounce(pushDebounceMillis).collect { local ->
                val dirty = cloudLock.withLock { local.filter { it.updatedAt > (cloud[it.key] ?: -1L) } }
                if (dirty.isEmpty()) return@collect
                try {
                    backend.push(uid, dirty)
                    cloudLock.withLock { dirty.forEach { cloud[it.key] = it.updatedAt } }
                    _status.value = SyncStatus.Synced(nowMillis(), pending = false)
                } catch (e: Exception) {
                    Logger.e(e) { "Sync push failed" }
                    _status.value = SyncStatus.Error(e.message ?: e::class.simpleName.orEmpty())
                }
            }
        }
    }
}
