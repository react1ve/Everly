package app.android

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.data.HabitRepository
import app.platform.AppClock
import app.platform.ReminderCoordinator
import app.platform.Analytics
import app.platform.Events
import app.ui.text.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext

/**
 * ACTION_FIRE: posts the reminder (tap opens the habit; "Done" checks today off).
 * ACTION_DONE: the notification's Done button.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        when (intent.action) {
            ACTION_FIRE -> {
                post(context, id, intent.getStringExtra(EXTRA_TITLE).orEmpty(), intent.getStringExtra(EXTRA_BODY).orEmpty())
                inBackground { reschedule() }
            }
            ACTION_DONE -> {
                NotificationManagerCompat.from(context).cancel(id.hashCode())
                inBackground {
                    val koin = GlobalContext.getOrNull() ?: return@inBackground
                    koin.get<HabitRepository>().markDone(id, koin.get<AppClock>().today())
                    koin.get<Analytics>().track(Events.NOTIFICATION_DONE)
                    reschedule()
                }
            }
        }
    }

    private fun post(context: Context, id: String, title: String, body: String) {
        AndroidReminderScheduler.ensureChannel(context)
        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!canPost) return

        val open = PendingIntent.getActivity(
            context, id.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_HABIT, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = PendingIntent.getBroadcast(
            context, id.hashCode() + 1,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_DONE).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, AndroidReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .addAction(0, L.notifDone, done)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
    }

    companion object {
        const val ACTION_FIRE = "app.android.REMINDER_FIRE"
        const val ACTION_DONE = "app.android.REMINDER_DONE"
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
    }
}

/** Alarms don't survive reboot, and wall-clock alarms drift on time / zone changes. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> inBackground { reschedule() }
        }
    }
}

private suspend fun reschedule() {
    GlobalContext.getOrNull()?.get<ReminderCoordinator>()?.rescheduleAll()
}

/** Runs [work] off the main thread while keeping the receiver alive (max ~8 s). */
private fun BroadcastReceiver.inBackground(work: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            withTimeoutOrNull(8_000) { work() }
        } finally {
            pending.finish()
        }
    }
}
