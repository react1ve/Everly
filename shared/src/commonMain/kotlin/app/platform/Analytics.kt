package app.platform

import co.touchlab.kermit.Logger

/**
 * Anonymous, opt-in usage counts. Events carry feature names and coarse values only —
 * never habit names, notes or dates (matches the privacy promise in Settings).
 */
interface Analytics {
    fun track(event: String, props: Map<String, String> = emptyMap())
    /** The user's choice in Settings. Turns collection on/off at the provider too. */
    fun setConsent(enabled: Boolean)
}

/** Where events go once consent is given: Firebase Analytics on Android, the log elsewhere. */
interface AnalyticsSink {
    fun send(event: String, props: Map<String, String>)
    /** Provider-level switch (e.g. FirebaseAnalytics.setAnalyticsCollectionEnabled). */
    fun setEnabled(enabled: Boolean) {}
}

object LogSink : AnalyticsSink {
    override fun send(event: String, props: Map<String, String>) {
        Logger.withTag("Analytics").i { "$event $props" }
    }
}

class ConsentingAnalytics(
    private val settings: AppSettings,
    private val sink: AnalyticsSink = LogSink,
) : Analytics {
    init {
        sink.setEnabled(settings.analyticsEnabled.value)
    }

    override fun track(event: String, props: Map<String, String>) {
        if (settings.analyticsEnabled.value) runCatching { sink.send(event, props) }
    }

    override fun setConsent(enabled: Boolean) {
        settings.setAnalytics(enabled)
        sink.setEnabled(enabled)
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
    const val SIGNED_IN = "login"            // Firebase's recommended event name
    const val SYNC_ENABLED = "sync_enabled"
}
