package app.android

import android.app.Application
import app.di.initKoin
import app.platform.ReminderScheduler
import app.platform.BackupFiles
import app.platform.SystemSettings
import app.platform.AnalyticsSink
import app.sync.AccountService
import app.sync.NoCloudAccounts
import app.sync.NoCloudBackend
import app.sync.SyncBackend
import app.android.cloud.CurrentActivity
import app.android.cloud.FirebaseAccountService
import app.android.cloud.FirebaseAnalyticsSink
import app.android.cloud.FirestoreSyncBackend
import app.android.cloud.firebaseReady
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.koin.core.context.GlobalContext
import app.data.HabitRepository
import app.platform.AppClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

class RecurApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(CurrentActivity)
        val cloud = firebaseReady(this)
        val androidModule = module {
            single { NotificationPermissionBridge() }
            single<ReminderScheduler> { AndroidReminderScheduler(get(), get()) }
            single { AndroidBackupFiles(get()) }
            single<BackupFiles> { get<AndroidBackupFiles>() }
            single<SystemSettings> { AndroidSystemSettings(get()) }
            // Cloud: Firebase when google-services.json was present at build time, otherwise local-only.
            if (cloud) {
                single<SyncBackend> { FirestoreSyncBackend() }
                single<AccountService> { FirebaseAccountService(BuildConfig.GOOGLE_WEB_CLIENT_ID, CurrentActivity::get) }
                single<AnalyticsSink> { FirebaseAnalyticsSink(get()) }
            } else {
                single<SyncBackend> { NoCloudBackend }
                single<AccountService> { NoCloudAccounts }
            }
        }
        initKoin {
            androidContext(this@RecurApp)
            modules(androidModule)
        }
        keepWidgetFresh()
    }

    /** Re-render the widget whenever habits or entries change, or the day rolls over. */
    private fun keepWidgetFresh() {
        val koin = GlobalContext.get()
        val repo = koin.get<HabitRepository>()
        val clock = koin.get<AppClock>()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            combine(repo.habits, repo.entries, clock.todayFlow) { _, _, _ -> Unit }
                .conflate()
                .collect {
                    runCatching { RecurWidget.refresh(this@RecurApp) }
                    delay(500) // coalesce bursts of taps
                }
        }
    }
}
