package app.android

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred

/**
 * Lets shared code ask for POST_NOTIFICATIONS (Android 13+) without knowing about Activities.
 * MainActivity attaches itself in onCreate; requests made while no Activity is attached return false.
 */
class NotificationPermissionBridge {
    private var launcher: ActivityResultLauncher<String>? = null
    private var pending: CompletableDeferred<Boolean>? = null

    fun attach(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pending?.complete(granted)
            pending = null
        }
    }

    fun detach() {
        launcher = null
        pending?.complete(false)
        pending = null
    }

    suspend fun request(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val l = launcher ?: return false
        pending?.let { return it.await() }
        val d = CompletableDeferred<Boolean>().also { pending = it }
        l.launch(Manifest.permission.POST_NOTIFICATIONS)
        return d.await()
    }
}
