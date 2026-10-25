package app.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.ui.App
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val permissions: NotificationPermissionBridge by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        permissions.attach(this) // must register before STARTED
        setContent { App() }
    }

    override fun onDestroy() {
        permissions.detach()
        super.onDestroy()
    }
}
