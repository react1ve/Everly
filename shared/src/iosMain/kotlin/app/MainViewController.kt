package app

import androidx.compose.ui.window.ComposeUIViewController
import app.di.initKoin
import app.platform.IosReminderScheduler
import app.platform.BackupFiles
import app.platform.IosBackupFiles
import app.platform.IosSystemSettings
import app.platform.SystemSettings
import app.sync.AccountService
import app.sync.NoCloudAccounts
import app.sync.NoCloudBackend
import app.sync.SyncBackend
import app.platform.ReminderScheduler
import app.ui.App
import org.koin.dsl.module
import platform.UIKit.UIViewController

private val iosModule = module {
    single<ReminderScheduler> { IosReminderScheduler() }
    single<BackupFiles> { IosBackupFiles() }
    single<SystemSettings> { IosSystemSettings() }
    // Cloud sync + Firebase on iOS land in the iOS milestone (GoogleService-Info.plist, Sign in with Apple).
    single<SyncBackend> { NoCloudBackend }
    single<AccountService> { NoCloudAccounts }
}

@Suppress("FunctionName", "unused") // called from Swift: MainViewControllerKt.MainViewController()
fun MainViewController(): UIViewController {
    initKoin { modules(iosModule) }
    return ComposeUIViewController { App() }
}
