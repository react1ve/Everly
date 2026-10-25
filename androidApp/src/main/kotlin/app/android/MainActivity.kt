package app.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.platform.DeepLinks
import app.ui.App
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val permissions: NotificationPermissionBridge by inject()
    private val backupFiles: AndroidBackupFiles by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        permissions.attach(this) // launchers must be registered before STARTED
        backupFiles.attach(this)
        if (savedInstanceState == null) handleLink(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN_HABIT)?.let(DeepLinks::openHabit)
    }

    override fun onDestroy() {
        permissions.detach()
        backupFiles.detach()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN_HABIT = "open_habit"
    }
}
