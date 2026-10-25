package app.android.cloud

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import app.sync.Account
import app.sync.AccountService
import app.sync.AuthResult
import app.sync.Provider
import android.util.Log
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Sign-in with the user's own Firebase project:
 * - Google: Credential Manager "Sign in with Google" → Firebase GoogleAuthProvider.
 * - Apple: Firebase's web flow for apple.com (Custom Tab), per Firebase's Android guide.
 * Needs an Activity, supplied by [CurrentActivity].
 */
class FirebaseAccountService(
    private val webClientId: String,
    private val currentActivity: () -> Activity?,
) : AccountService {

    private val auth = FirebaseAuth.getInstance()
    private val _account = MutableStateFlow(auth.currentUser?.toAccount())
    override val account: StateFlow<Account?> = _account.asStateFlow()
    override val isAvailable = true
    override val providers = listOfNotNull(Provider.GOOGLE.takeIf { webClientId.isNotBlank() }, Provider.APPLE)

    init {
        auth.addAuthStateListener { _account.value = it.currentUser?.toAccount() }
    }

    override suspend fun signIn(provider: Provider): AuthResult {
        val activity = currentActivity() ?: return AuthResult.Failed(AuthResult.Reason.OTHER, "no activity")
        return try {
            when (provider) {
                Provider.GOOGLE -> signInWithGoogle(activity)
                Provider.APPLE -> signInWithApple(activity)
            }
            AuthResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            AuthResult.Cancelled
        } catch (e: NoCredentialException) {
            AuthResult.Failed(AuthResult.Reason.NO_ACCOUNT, e.message)
        } catch (e: FirebaseNetworkException) {
            AuthResult.Failed(AuthResult.Reason.NETWORK, e.message)
        } catch (e: Exception) {
            // The Apple Custom Tab being closed surfaces as a FirebaseAuthException with code ERROR_WEB_CONTEXT_CANCELED.
            if (e.message?.contains("canceled", ignoreCase = true) == true) AuthResult.Cancelled
            else AuthResult.Failed(AuthResult.Reason.OTHER, e.message).also { Log.e("Auth", "sign-in failed", e) }
        }
    }

    private suspend fun signInWithGoogle(activity: Activity) {
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type"
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    private suspend fun signInWithApple(activity: Activity) {
        // The Activity can be recreated during the web flow: pick up a result that is already on its way.
        val pending = auth.pendingAuthResult
        if (pending != null) { pending.await(); return }
        val provider = OAuthProvider.newBuilder("apple.com").setScopes(listOf("email", "name")).build()
        auth.startActivityForSignInWithProvider(activity, provider).await()
    }

    override suspend fun signOut() {
        auth.signOut()
        currentActivity()?.let { runCatching { CredentialManager.create(it).clearCredentialState(ClearCredentialStateRequest()) } }
    }

    override suspend fun deleteAccount(): AuthResult = try {
        auth.currentUser?.delete()?.await()
        AuthResult.Success
    } catch (e: FirebaseAuthRecentLoginRequiredException) {
        AuthResult.Failed(AuthResult.Reason.NEEDS_RECENT_LOGIN, e.message)
    } catch (e: Exception) {
        AuthResult.Failed(AuthResult.Reason.OTHER, e.message)
    }

    private fun FirebaseUser.toAccount() = Account(
        uid = uid,
        email = email,
        name = displayName,
        provider = providerData.firstNotNullOfOrNull {
            when (it.providerId) {
                "google.com" -> Provider.GOOGLE
                "apple.com" -> Provider.APPLE
                else -> null
            }
        },
    )
}
