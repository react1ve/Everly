package app

import androidx.compose.ui.window.ComposeUIViewController
import app.di.initKoin
import app.platform.IosReminderScheduler
import app.platform.ReminderScheduler
import app.ui.App
import org.koin.dsl.module
import platform.UIKit.UIViewController

private val iosModule = module {
    single<ReminderScheduler> { IosReminderScheduler() }
}

@Suppress("FunctionName", "unused") // called from Swift: MainViewControllerKt.MainViewController()
fun MainViewController(): UIViewController {
    initKoin { modules(iosModule) }
    return ComposeUIViewController { App() }
}
