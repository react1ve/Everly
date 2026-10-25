package app.android

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import app.platform.SystemSettings

class AndroidSystemSettings(private val context: Context) : SystemSettings {

    override fun exactAlarmsAllowed(): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms()
    }

    override fun ignoringBatteryOptimisations(): Boolean? =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName)

    override fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            start(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        } else {
            openAppSettings()
        }
    }

    // This app's own battery page (Unrestricted / Optimised / Restricted), not the all-apps list.
    // Not ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: Play restricts that direct prompt.
    // Where the page doesn't exist (before Android 12, some OEMs) start() falls back to the app's info page.
    override fun openBatterySettings() =
        start(Intent(ACTION_APP_BATTERY_USAGE, Uri.parse("package:${context.packageName}")))

    override fun openAppSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        start(intent)
    }

    private companion object {
        /** Exported by AOSP Settings since Android 12 (AppBatteryUsageActivity); not in the public SDK constants. */
        const val ACTION_APP_BATTERY_USAGE = "android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL"
    }

    private fun start(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
    }
}
