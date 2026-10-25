package app.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.core.model.Entry
import app.core.model.Folder
import app.core.model.Habit
import app.core.model.Interval
import app.core.model.IntervalUnit
import app.core.model.ReminderSettings
import app.core.schedule.ScheduleEngine
import app.data.HabitDraft
import app.data.HabitRepository
import app.design.HabitColorKey
import app.platform.AppClock
import app.platform.ReminderScheduler
import app.platform.Analytics
import app.platform.Events
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime

data class HabitFormState(
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    val name: String = "",
    val count: Int = 1,
    val unit: IntervalUnit = IntervalUnit.DAY,
    val doses: Int = 1,
    val color: HabitColorKey = HabitColorKey.BLUE,
    val folderId: String? = null,
    val reminder: ReminderSettings = ReminderSettings(),
    /** null = unknown yet; false = the OS refused notifications. */
    val notificationsAllowed: Boolean? = null,
    val nextReminder: LocalDateTime? = null,
    val showNameError: Boolean = false,
    val saving: Boolean = false,
    val closed: Boolean = false,
    val confirmDelete: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && !saving
}

/** S03 New / Edit habit. [habitId] empty = new. */
class HabitFormViewModel(
    private val habitId: String,
    private val repo: HabitRepository,
    private val clock: AppClock,
    private val reminders: ReminderScheduler,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(HabitFormState(isNew = habitId.isEmpty(), loaded = habitId.isEmpty()))
    val state: StateFlow<HabitFormState> = _state.asStateFlow()
    val folders: StateFlow<List<Folder>> = repo.folders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var original: Habit? = null
    private var entries: List<Entry> = emptyList()

    init {
        viewModelScope.launch {
            if (habitId.isNotEmpty()) {
                val h = repo.habit(habitId)
                if (h == null) { _state.update { it.copy(closed = true) }; return@launch }
                original = h
                entries = repo.entries.first().filter { it.habitId == habitId }
                _state.update {
                    it.copy(
                        loaded = true, name = h.name, count = h.interval.count, unit = h.interval.unit,
                        doses = h.dosesPerDay, folderId = h.folderId, reminder = h.reminder,
                        color = HabitColorKey.entries.firstOrNull { k -> k.name.equals(h.colorKey, true) } ?: HabitColorKey.BLUE,
                    )
                }
            }
            if (_state.value.reminder.enabled) {
                val allowed = reminders.hasPermission()
                _state.update { it.copy(notificationsAllowed = allowed) }
            }
            refreshPreview()
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v.take(Habit.MAX_NAME), showNameError = false) }
    fun setCount(v: Int) = edit { it.copy(count = v.coerceIn(1, Interval.MAX_COUNT)) }
    fun setUnit(v: IntervalUnit) = edit { it.copy(unit = v) }
    fun setDoses(v: Int) = edit { it.copy(doses = v.coerceIn(1, Habit.MAX_DOSES)) }
    fun setColor(v: HabitColorKey) = _state.update { it.copy(color = v) }
    fun setFolder(id: String?) = _state.update { it.copy(folderId = id) }
    fun askDelete(show: Boolean) = _state.update { it.copy(confirmDelete = show) }

    fun createFolderAndSelect(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repo.createFolder(name, _state.value.color.name.lowercase())
            _state.update { it.copy(folderId = id) }
        }
    }

    /** Turning reminders on is where we ask for notification permission — not at app launch. */
    fun setReminderEnabled(on: Boolean) {
        edit { it.copy(reminder = it.reminder.copy(enabled = on)) }
        if (on) viewModelScope.launch {
            val allowed = reminders.requestPermission()
            _state.update { it.copy(notificationsAllowed = allowed) }
        }
    }

    fun setReminderTime(hour: Int, minute: Int) = edit { it.copy(reminder = it.reminder.copy(minuteOfDay = hour * 60 + minute)) }
    fun setRemindDaysBefore(days: Int) = edit { it.copy(reminder = it.reminder.copy(daysBefore = days.coerceIn(0, 30))) }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) { _state.update { it.copy(showNameError = true) }; return }
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val draft = draftOf(s)
            if (s.isNew) {
                repo.createHabit(draft)
                analytics.track(Events.HABIT_CREATED, mapOf("unit" to s.unit.name, "doses" to s.doses.toString()))
            } else {
                repo.updateHabit(habitId, draft)
            }
            if (s.reminder.enabled && original?.reminder?.enabled != true) analytics.track(Events.REMINDER_ENABLED)
            _state.update { it.copy(saving = false, closed = true) }
        }
    }

    fun delete() {
        if (habitId.isEmpty()) return
        viewModelScope.launch {
            repo.deleteHabit(habitId)
            _state.update { it.copy(confirmDelete = false, closed = true) }
        }
    }

    private fun edit(change: (HabitFormState) -> HabitFormState) {
        _state.update(change)
        refreshPreview()
    }

    private fun refreshPreview() {
        val s = _state.value
        val preview = if (!s.reminder.enabled) null else {
            val d = draftOf(s)
            val habit = Habit(
                id = habitId.ifEmpty { "new" }, name = d.name.ifBlank { "new" }, interval = d.interval,
                dosesPerDay = d.dosesPerDay, anchorDate = d.anchorDate, colorKey = d.colorKey, reminder = d.reminder,
            )
            ScheduleEngine.nextReminder(habit, entries, clock.now())
        }
        _state.update { it.copy(nextReminder = preview) }
    }

    private fun draftOf(s: HabitFormState) = HabitDraft(
        name = s.name,
        interval = Interval(s.count, s.unit),
        dosesPerDay = s.doses,
        colorKey = s.color.name.lowercase(),
        folderId = s.folderId,
        reminder = s.reminder,
        anchorDate = original?.anchorDate ?: clock.today(),
    )
}
