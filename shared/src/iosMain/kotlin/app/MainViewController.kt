package app

import androidx.compose.ui.window.ComposeUIViewController
import app.di.initKoin
import app.ui.App
import platform.UIKit.UIViewController

@Suppress("FunctionName", "unused") // called from Swift: MainViewControllerKt.MainViewController()
fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController { App() }
}
