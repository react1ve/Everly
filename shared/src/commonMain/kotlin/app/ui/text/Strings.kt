package app.ui.text

import app.core.model.IntervalUnit

/**
 * Every user-facing word in the app, per language. Plain Kotlin (not Compose resources) so
 * view models, notifications and the Android widget can use it outside composition.
 * All copy is written fresh for this app.
 */
abstract class Strings {
    abstract val code: String

    // --- dates ---
    abstract val monthsLong: List<String>          // nominative, for the grid header
    abstract val weekdaysShort: List<String>       // Mon..Sun
    abstract val weekdaysLong: List<String>
    abstract fun shortDate(day: Int, month: Int): String                       // month 0..11
    abstract fun longDate(weekday: Int, day: Int, month: Int, year: Int): String
    abstract fun weekdayDate(weekday: Int, day: Int, month: Int): String
    abstract fun at(date: String, time: String): String

    // --- intervals ---
    abstract fun unitName(u: IntervalUnit): String                // segmented control
    abstract fun unitCount(u: IntervalUnit, n: Int): String       // "3 days" next to the stepper (unit only)
    abstract val daily: String
    abstract val weekly: String
    abstract val monthly: String
    abstract val yearly: String
    abstract fun every(n: Int, u: IntervalUnit): String
    abstract fun perDay(n: Int): String
    abstract fun timesThatDay(n: Int): String
    abstract fun overdueDays(n: Int): String
    abstract val dueToday: String
    abstract val dueTomorrow: String
    abstract fun dueOn(date: String): String
    abstract fun daysBefore(n: Int): String
    abstract val off: String

    // --- cell semantics ---
    abstract val stDone: String
    abstract fun stPartial(n: Int, m: Int): String
    abstract val stCovered: String
    abstract val stDue: String
    abstract val stOverdue: String
    abstract val stIdle: String
    abstract val cToday: String
    abstract val cHasNote: String
    abstract val cReminder: String

    // --- common ---
    abstract val close: String
    abstract val save: String
    abstract val ok: String
    abstract val cancel: String
    abstract val delete: String
    abstract val edit: String
    abstract val decrease: String
    abstract val increase: String
    abstract val none: String
    abstract val colour: String
    abstract val folder: String
    abstract val reminder: String
    abstract val nextReminder: String

    // --- grid ---
    abstract val opensDetails: String
    abstract val editFolder: String
    abstract fun habitsCount(n: Int): String
    abstract val collapsed: String
    abstract val expanded: String
    abstract val scrollToToday: String
    abstract val today: String
    abstract val newHabit: String
    abstract val organiseHabits: String
    abstract val settings: String
    abstract val emptyTitle: String
    abstract val emptyBody: String
    abstract val addFirst: String
    abstract val orStartFrom: String
    abstract val templates: List<Pair<String, String>>  // label to habit name, same order as GridViewModel.Template

    // --- reminders / notifications ---
    abstract fun notifComingUp(days: Int, date: String): String
    abstract val notifDueTomorrow: String
    abstract val notifDueToday: String
    abstract fun notifStillWaiting(date: String): String
    abstract val notifDone: String
    abstract val channelName: String
    abstract val channelDescription: String

    // --- habit detail ---
    abstract val yearView: String
    abstract val previousYear: String
    abstract val nextYear: String
    abstract fun daysDoneIn(n: Int, year: Int): String
    abstract val repeats: String
    abstract val nextDue: String
    abstract val entries: String
    abstract val addEntry: String
    abstract fun nothingLogged(year: Int): String
    abstract val noteOnly: String

    // --- entry sheet ---
    abstract val whatHappened: String
    abstract val deleteEntry: String
    abstract val discardChanges: String
    abstract val discard: String
    abstract val keepEditing: String
    abstract val checked: String
    abstract val notChecked: String
    abstract val doneLabel: String

    // --- folder ---
    abstract val newFolder: String
    abstract val editFolderTitle: String
    abstract val folderPlaceholder: String
    abstract val deleteFolder: String
    abstract fun deleteFolderTitle(name: String): String
    abstract val folderEmpty: String
    abstract fun folderHabitsStay(n: Int): String
    abstract val noFolder: String
    abstract val newFolderEllipsis: String
    abstract val create: String
    abstract val folderExample: String

    // --- habit form ---
    abstract val newHabitTitle: String
    abstract val editHabitTitle: String
    abstract val namePlaceholder: String
    abstract val nameError: String
    abstract val repeatsEvery: String
    abstract val deleteHabit: String
    abstract val deleteHabitTitle: String
    abstract val deleteHabitBody: String
    abstract val keepIt: String
    abstract val remindMe: String
    abstract val time: String
    abstract val whenLabel: String
    abstract fun reminderTimeA11y(time: String): String
    abstract val notificationsOffWarning: String
    abstract val reminderTimeTitle: String

    // --- organise ---
    abstract val organise: String
    abstract val moveFolderUp: String
    abstract val moveFolderDown: String
    abstract val empty: String
    abstract val moveUp: String
    abstract val moveDown: String
    abstract val moveToFolder: String
    abstract val dragToReorder: String

    // --- settings ---
    abstract val appearance: String
    abstract val themeAuto: String
    abstract val themeLight: String
    abstract val themeDark: String
    abstract val language: String
    abstract val languageAuto: String
    abstract val reminders: String
    abstract val notifications: String
    abstract val allowed: String
    abstract val offTapToAllow: String
    abstract val openNotificationSettings: String
    abstract val onTimeReminders: String
    abstract val on: String
    abstract val batteryOptimisation: String
    abstract val notRestricted: String
    abstract val restricted: String
    abstract val batteryHint: String
    abstract val yourData: String
    abstract val exportBackup: String
    abstract val restoreBackup: String
    abstract val deleteEverything: String
    abstract val dataHint: String
    abstract val shareUsage: String
    abstract val shareUsageHint: String
    abstract val about: String
    abstract val version: String
    abstract val designCatalog: String
    abstract val backupSaved: String
    abstract fun restored(habits: Int, entries: Int): String
    abstract val everythingDeleted: String
    abstract fun somethingWrong(detail: String): String
    abstract val replaceEverything: String
    abstract val replaceBody: String
    abstract val restore: String
    abstract val deleteEverythingTitle: String
    abstract val deleteEverythingBody: String
    abstract val backupNewer: String
    abstract val backupInvalid: String

    // --- account & sync ---
    abstract val accountSync: String
    abstract val continueGoogle: String
    abstract val continueApple: String
    abstract val syncHint: String
    abstract fun signedInAs(who: String): String
    abstract val syncConnecting: String
    abstract fun syncedAt(time: String): String
    abstract val syncPending: String
    abstract fun syncProblem(detail: String): String
    abstract val signOut: String
    abstract val signOutHint: String
    abstract val deleteAccount: String
    abstract val deleteAccountTitle: String
    abstract val deleteAccountBody: String
    abstract val needsRecentLogin: String
    abstract val signInFailed: String
    abstract val noGoogleAccount: String
    abstract val accountDeleted: String

    // --- widget ---
    abstract fun toDo(n: Int): String
    abstract val widgetAddHabit: String
    abstract val nothingDueToday: String
    abstract val allCaughtUp: String

    // --- onboarding ---
    abstract val skip: String
    abstract val next: String
    abstract val getStarted: String
    abstract val onb1Title: String
    abstract val onb1Body: String
    abstract val onb2Title: String
    abstract val onb2Body: String
    abstract val onb3Title: String
    abstract val onb3Body: String
    abstract val onbReminders: String
    abstract val onbRemindersHint: String
}

/** The active language. Set from AppSettings (Auto = the phone's language). */
object Lang {
    val all: List<Strings> = listOf(En, Ru, Uz)
    var current: Strings = En
        private set

    fun select(code: String?) {
        current = all.firstOrNull { it.code == code?.take(2)?.lowercase() } ?: En
    }
}

/** Shorthand used across the UI: `L.today`. */
val L: Strings get() = Lang.current

internal fun ruPlural(n: Int, one: String, few: String, many: String): String {
    val n10 = n % 10
    val n100 = n % 100
    return when {
        n10 == 1 && n100 != 11 -> one
        n10 in 2..4 && n100 !in 12..14 -> few
        else -> many
    }
}
