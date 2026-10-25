package app.android

import android.app.Application
import app.di.initKoin
import org.koin.android.ext.koin.androidContext

class RecurApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@RecurApp) }
    }
}
