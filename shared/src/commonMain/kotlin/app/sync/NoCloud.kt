package app.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Used when the build has no cloud config (or on a platform not wired yet): the account UI hides itself. */
object NoCloudAccounts : AccountService {
    override val isAvailable = false
    override val providers = emptyList<Provider>()
    override val account: StateFlow<Account?> = MutableStateFlow(null)
    override suspend fun signIn(provider: Provider): AuthResult = AuthResult.Failed(AuthResult.Reason.NOT_CONFIGURED)
    override suspend fun signOut() = Unit
    override suspend fun deleteAccount(): AuthResult = AuthResult.Failed(AuthResult.Reason.NOT_CONFIGURED)
}

object NoCloudBackend : SyncBackend {
    override suspend fun push(uid: String, records: List<SyncRecord>) = Unit
    override fun observe(uid: String): Flow<RemoteBatch> = emptyFlow()
    override suspend fun deleteAll(uid: String) = Unit
}
