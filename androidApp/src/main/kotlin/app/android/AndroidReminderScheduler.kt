package app.android

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.platform.ReminderScheduler
import app.ui.text.L
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * One AlarmManager alarm per habit (request code = habit id hash, so re-scheduling replaces it).
 * Exact when the user allows exact alarms (Android 12+), otherwise allow-while-idle, which may be a few minutes late.
 */
class AndroidReminderScheduler(
    private val context: Context,
    private val permissions: NotificationPermissionBridge,
) : ReminderScheduler {

    private val alarms: AlarmManager = requireNotNull(context.getSystemService(AlarmManager::class.java))

    init {
        ensureChannel(context)
    }

    override fun schedule(id: String, title: String, body: String, at: LocalDateTime) {
        val millis = at.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val pi = pendingIntent(id, title, body)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    override fun cancel(id: String) {
        alarms.cancel(pendingIntent(id, "", ""))
    }

    override suspend fun requestPermission(): Boolean =
        hasPermissionNow() || permissions.request()

    override suspend fun hasPermission(): Boolean = hasPermissionNow()

    private fun hasPermissionNow(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(id: String, title: String, body: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_ID, id)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "reminders"

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, L.channelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = L.channelDescription
                    },
                )
            }
        }
    }
}
