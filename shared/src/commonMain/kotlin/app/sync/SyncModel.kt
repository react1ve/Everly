package app.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * One synced document. Kinds map 1:1 to cloud collections under users/{uid}/.
 * [data] holds plain values only (String, Long/Int/Double, Boolean, null) so any backend can store it.
 */
data class SyncRecord(
    val kind: String,
    val id: String,
    val updatedAt: Long,
    val deleted: Boolean,
    val data: Map<String, Any?>,
) {
    val key: String get() = "$kind/$id"

    companion object {
        const val HABITS = "habits"
        const val FOLDERS = "folders"
        const val ENTRIES = "entries"
        val KINDS = listOf(HABITS, FOLDERS, ENTRIES)
    }
}

/** The local side of sync. Implemented by the repository. */
interface SyncableStore {
    /** Every local record, including tombstones of deleted ones, with their last-change time. */
    val records: Flow<List<SyncRecord>>
    /** Merge remote records: last write wins per record. Must not re-stamp what it applies. */
    suspend fun applyRemote(records: List<SyncRecord>)
}

/** The cloud side of sync (Firestore on Android; iOS later). */
interface SyncBackend {
    /** Writes records for [uid]. Each record is written independently. */
    suspend fun push(uid: String, records: List<SyncRecord>)
    /** First emission = everything the user has in the cloud; then only what changed. */
    fun observe(uid: String): Flow<RemoteBatch>
    /** Removes all of [uid]'s cloud data (account deletion). */
    suspend fun deleteAll(uid: String)
}

data class RemoteBatch(val records: List<SyncRecord>, val fromCache: Boolean)

enum class Provider { GOOGLE, APPLE }

data class Account(val uid: String, val email: String?, val name: String?, val provider: Provider?)

sealed interface AuthResult {
    data object Success : AuthResult
    data object Cancelled : AuthResult
    data class Failed(val reason: Reason, val detail: String? = null) : AuthResult
    enum class Reason { NOT_CONFIGURED, NO_ACCOUNT, NETWORK, NEEDS_RECENT_LOGIN, OTHER }
}

interface AccountService {
    /** False when the build has no cloud config (e.g. no google-services.json) or the platform isn't done yet. */
    val isAvailable: Boolean
    val providers: List<Provider>
    val account: StateFlow<Account?>
    suspend fun signIn(provider: Provider): AuthResult
    suspend fun signOut()
    /** Deletes the sign-in account itself (cloud data is removed first by [SyncEngine.deleteAccount]). */
    suspend fun deleteAccount(): AuthResult
}

sealed interface SyncStatus {
    data object SignedOut : SyncStatus
    data object Connecting : SyncStatus
    /** [pending] = local changes not yet confirmed by the server (offline or in flight). */
    data class Synced(val atMillis: Long, val pending: Boolean) : SyncStatus
    data class Error(val detail: String) : SyncStatus
}
