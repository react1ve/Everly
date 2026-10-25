package app.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.core.model.Habit
import app.core.model.Interval
import app.core.model.IntervalUnit
import app.data.HabitDraft
import app.data.HabitRepository
import app.design.HabitColorKey
import app.platform.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HabitFormState(
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    val name: String = "",
    val count: Int = 1,
    val unit: IntervalUnit = IntervalUnit.DAY,
    val doses: Int = 1,
    val color: HabitColorKey = HabitColorKey.BLUE,
    val showNameError: Boolean = false,
    val saving: Boolean = false,
    val closed: Boolean = false,
    val confirmDelete: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && !saving
}

/** S03 New / Edit habit. [habitId] empty = new. Reminder + folder fields arrive in M2. */
class HabitFormViewModel(
    private val habitId: String,
    private val repo: HabitRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val _state = MutableStateFlow(HabitFormState(isNew = habitId.isEmpty(), loaded = habitId.isEmpty()))
    val state: StateFlow<HabitFormState> = _state.asStateFlow()
    private var original: Habit? = null

    init {
        if (habitId.isNotEmpty()) viewModelScope.launch {
            val h = repo.habit(habitId)
            if (h == null) { _state.update { it.copy(closed = true) }; return@launch }
            original = h
            _state.update {
                it.copy(
                    loaded = true, name = h.name, count = h.interval.count, unit = h.interval.unit,
                    doses = h.dosesPerDay,
                    color = HabitColorKey.entries.firstOrNull { k -> k.name.equals(h.colorKey, true) } ?: HabitColorKey.BLUE,
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v.take(Habit.MAX_NAME), showNameError = false) }
    fun setCount(v: Int) = _state.update { it.copy(count = v.coerceIn(1, Interval.MAX_COUNT)) }
    fun setUnit(v: IntervalUnit) = _state.update { it.copy(unit = v) }
    fun setDoses(v: Int) = _state.update { it.copy(doses = v.coerceIn(1, Habit.MAX_DOSES)) }
    fun setColor(v: HabitColorKey) = _state.update { it.copy(color = v) }
    fun askDelete(show: Boolean) = _state.update { it.copy(confirmDelete = show) }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) { _state.update { it.copy(showNameError = true) }; return }
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val draft = HabitDraft(
                name = s.name,
                interval = Interval(s.count, s.unit),
                dosesPerDay = s.doses,
                colorKey = s.color.name.lowercase(),
                folderId = original?.folderId,
                reminder = original?.reminder ?: app.core.model.ReminderSettings(),
                anchorDate = original?.anchorDate ?: clock.today(),
            )
            if (s.isNew) repo.createHabit(draft) else repo.updateHabit(habitId, draft)
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
}
