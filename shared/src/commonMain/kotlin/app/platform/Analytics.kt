package app.platform

import co.touchlab.kermit.Logger

/**
 * Anonymous, opt-in usage counts. Events carry feature names and coarse values only —
 * never habit names, notes or dates (matches the privacy promise in Settings).
 */
interface Analytics {
    fun track(event: String, props: Map<String, String> = emptyMap())
}

/** Where events go once consent is given. Swap for a real provider (needs its API key) in /replica-deploy. */
fun interface AnalyticsSink {
    fun send(event: String, props: Map<String, String>)
}

val LogSink = AnalyticsSink { event, props -> Logger.withTag("Analytics").i { "$event $props" } }

class ConsentingAnalytics(
    private val settings: AppSettings,
    private val sink: AnalyticsSink = LogSink,
) : Analytics {
    override fun track(event: String, props: Map<String, String>) {
        if (settings.analyticsEnabled.value) runCatching { sink.send(event, props) }
    }
}

object Events {
    const val HABIT_CREATED = "habit_created"
    const val DAY_TOGGLED = "day_toggled"
    const val NOTE_SAVED = "note_saved"
    const val REMINDER_ENABLED = "reminder_enabled"
    const val FOLDER_CREATED = "folder_created"
    const val BACKUP_EXPORTED = "backup_exported"
    const val BACKUP_RESTORED = "backup_restored"
    const val ONBOARDING_DONE = "onboarding_done"
    const val WIDGET_TOGGLE = "widget_toggle"
    const val NOTIFICATION_DONE = "notification_done"
}
