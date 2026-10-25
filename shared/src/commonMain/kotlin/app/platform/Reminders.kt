package app.platform

import app.core.model.Entry
import app.ui.text.L
import app.core.model.Habit
import app.core.schedule.ScheduleEngine
import app.data.HabitRepository
import app.ui.text.Formats
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil

/** Local, on-device notifications. Android: AlarmManager (androidApp). iOS: UNUserNotificationCenter (iosMain). */
interface ReminderScheduler {
    /** One pending reminder per habit: scheduling again with the same [id] replaces it. [at] is local wall-clock time. */
    fun schedule(id: String, title: String, body: String, at: LocalDateTime)
    fun cancel(id: String)
    /** Asks the OS if needed. Returns whether notifications may be shown. */
    suspend fun requestPermission(): Boolean
    suspend fun hasPermission(): Boolean
}

/**
 * Keeps exactly one pending reminder per habit, matching [ScheduleEngine.nextReminder].
 * Re-syncs on every data change and when the day rolls over. Only the soonest
 * [MAX_PENDING] are scheduled (iOS allows 64 pending requests per app).
 */
class ReminderCoordinator(
    private val repo: HabitRepository,
    private val scheduler: ReminderScheduler,
    private val clock: AppClock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var scheduled: Map<String, Pair<LocalDateTime, String>> = emptyMap()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(repo.habits, repo.entries, clock.todayFlow) { h, e, _ -> h to e }
                .collect { (habits, entries) -> sync(habits, entries, force = false) }
        }
    }

    /** After reboot, time-zone change, app update, or a reminder firing. */
    suspend fun rescheduleAll() {
        sync(repo.habits.first(), repo.entries.first(), force = true)
    }

    private suspend fun sync(habits: List<Habit>, entries: List<Entry>, force: Boolean) = mutex.withLock {
        try {
            val now = clock.now()
            val byHabit = entries.groupBy { it.habitId }
            val wanted = habits
                .mapNotNull { h -> ScheduleEngine.nextReminder(h, byHabit[h.id].orEmpty(), now)?.let { h to it } }
                .sortedBy { it.second }
                .take(MAX_PENDING)
            val wantedMap = wanted.associate { (h, at) -> h.id to (at to h.name) }

            (scheduled.keys - wantedMap.keys).forEach { scheduler.cancel(it) }
            for ((h, at) in wanted) {
                if (force || scheduled[h.id] != (at to h.name)) {
                    val due = ScheduleEngine.nextDue(h, byHabit[h.id].orEmpty())
                    scheduler.schedule(h.id, h.name, body(at, due), at)
                }
            }
            scheduled = wantedMap
        } catch (e: Exception) {
            Logger.e(e) { "Reminder sync failed" }
        }
    }

    private fun body(at: LocalDateTime, due: kotlinx.datetime.LocalDate): String {
        val days = at.date.daysUntil(due)
        return when {
            days > 1 -> L.notifComingUp(days, Formats.weekdayDate(due))
            days == 1 -> L.notifDueTomorrow
            days == 0 -> L.notifDueToday
            else -> L.notifStillWaiting(Formats.shortDate(due))
        }
    }

    companion object {
        const val MAX_PENDING = 60
    }
}

/** Used in previews/tests, or if a platform has no scheduler. */
class NoopReminderScheduler : ReminderScheduler {
    override fun schedule(id: String, title: String, body: String, at: LocalDateTime) = Unit
    override fun cancel(id: String) = Unit
    override suspend fun requestPermission() = false
    override suspend fun hasPermission() = false
}
