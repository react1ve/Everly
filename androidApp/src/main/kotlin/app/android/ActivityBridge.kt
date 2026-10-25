package app.android

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import app.platform.BackupFiles
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Storage Access Framework pickers for backups. Launchers are registered by MainActivity
 * (they must exist before the Activity is STARTED); shared code just suspends on the result.
 */
class AndroidBackupFiles(private val context: Context) : BackupFiles {
    private var createLauncher: ActivityResultLauncher<String>? = null
    private var openLauncher: ActivityResultLauncher<Array<String>>? = null
    private var pendingCreate: CompletableDeferred<Uri?>? = null
    private var pendingOpen: CompletableDeferred<Uri?>? = null

    fun attach(activity: ComponentActivity) {
        createLauncher = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
            pendingCreate?.complete(it); pendingCreate = null
        }
        openLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) {
            pendingOpen?.complete(it); pendingOpen = null
        }
    }

    fun detach() {
        createLauncher = null; openLauncher = null
        pendingCreate?.complete(null); pendingOpen?.complete(null)
        pendingCreate = null; pendingOpen = null
    }

    override suspend fun save(suggestedName: String, content: String): Boolean {
        val launcher = createLauncher ?: return false
        val result = CompletableDeferred<Uri?>().also { pendingCreate = it }
        launcher.launch(suggestedName)
        val uri = result.await() ?: return false
        return withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.encodeToByteArray()) } != null
        }
    }

    override suspend fun open(): String? {
        val launcher = openLauncher ?: return null
        val result = CompletableDeferred<Uri?>().also { pendingOpen = it }
        launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        val uri = result.await() ?: return null
        return withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }
    }
}
