package app.android

import android.app.Application
import app.di.initKoin
import app.platform.ReminderScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

class RecurApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val androidModule = module {
            single { NotificationPermissionBridge() }
            single<ReminderScheduler> { AndroidReminderScheduler(get(), get()) }
        }
        initKoin {
            androidContext(this@RecurApp)
            modules(androidModule)
        }
    }
}
