package app.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.core.model.Habit
import app.data.HabitRepository
import app.design.AppTheme
import app.design.Radius
import app.design.Sizes
import app.design.Space
import app.design.components.ButtonKind
import app.design.components.PillButton
import app.design.components.SheetHeader
import app.design.components.habitColors
import app.ui.text.Formats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** What the sheet is editing. One per (habit, date), like the entry table. */
data class EntryDraft(
    val habit: Habit,
    val date: LocalDate,
    val doseCount: Int,
    val note: String,
    val isExisting: Boolean,
    /** S08 "Add entry": the user picks the day. S05 long-press: the day is fixed. */
    val dateEditable: Boolean,
    val today: LocalDate,
) {
    val done: Boolean get() = doseCount >= habit.dosesPerDay
}

/** Shared by the grid (S05 long-press) and habit detail (S08 add / edit entry). */
class EntryEditor(private val repo: HabitRepository, private val scope: CoroutineScope) {
    private val _draft = MutableStateFlow<EntryDraft?>(null)
    val draft: StateFlow<EntryDraft?> = _draft.asStateFlow()

    fun open(habitId: String, date: LocalDate, today: LocalDate, dateEditable: Boolean = false) {
        scope.launch {
            val habit = repo.habit(habitId) ?: return@launch
            val existing = repo.entries.first().firstOrNull { it.habitId == habitId && it.date == date }
            _draft.value = EntryDraft(
                habit = habit,
                date = date,
                // New entries from "Add entry" start as done; a long-press keeps whatever the day has.
                doseCount = existing?.doseCount ?: if (dateEditable) habit.dosesPerDay else 0,
                note = existing?.note.orEmpty(),
                isExisting = existing != null,
                dateEditable = dateEditable,
                today = today,
            )
        }
    }

    fun close() { _draft.value = null }

    fun save(edited: EntryDraft) {
        scope.launch {
            repo.setNote(edited.habit.id, edited.date, edited.note, edited.doseCount)
            _draft.value = null
        }
    }

    fun delete(edited: EntryDraft) {
        scope.launch {
            repo.deleteEntry(edited.habit.id, edited.date)
            _draft.value = null
        }
    }
}

/** Bottom sheet: check the day, write what happened. Spec: components.md "NoteSheet". */
@Composable
fun EntrySheet(initial: EntryDraft, onSave: (EntryDraft) -> Unit, onDelete: (EntryDraft) -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    val hc = habitColors(initial.habit.colorKey)
    var draft by remember(initial) { mutableStateOf(initial) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val dirty = draft != initial
    val dirtyNow by rememberUpdatedState(dirty)
    // Swiping the sheet away with unsaved text asks first instead of losing it.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) { target ->
        if (target == SheetValue.Hidden && dirtyNow) { confirmDiscard = true; false } else true
    }
    fun tryDismiss() { if (dirty) confirmDiscard = true else onDismiss() }

    ModalBottomSheet(
        onDismissRequest = ::tryDismiss,
        sheetState = sheetState,
        containerColor = c.bg,
        shape = Radius.sheet,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().padding(bottom = Space.x6)) {
            SheetHeader(draft.habit.name, onClose = ::tryDismiss, onPrimary = { onSave(draft) })
            Column(Modifier.padding(horizontal = Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                Row(
                    Modifier.fillMaxWidth().clip(Radius.card).background(c.surface).heightIn(min = Sizes.touchMin)
                        .padding(horizontal = Space.x4, vertical = Space.x2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.x3),
                ) {
                    // done toggle
                    Box(
                        Modifier.size(Sizes.touchMin * 0.6f).clip(Radius.sm)
                            .background(if (draft.done) hc.fill else c.surface)
                            .border(Sizes.outline, if (draft.done) hc.fill else hc.outline, Radius.sm)
                            .clickable(role = Role.Checkbox) {
                                draft = draft.copy(doseCount = if (draft.done) 0 else draft.habit.dosesPerDay)
                            }
                            .semantics {
                                contentDescription = "Done"
                                stateDescription = if (draft.done) "checked" else
                                    if (draft.doseCount > 0) "${draft.doseCount} of ${draft.habit.dosesPerDay}" else "not checked"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (draft.done) Icon(Icons.Rounded.Check, null, tint = hc.onFill)
                        else if (draft.doseCount > 0) Text("${draft.doseCount}/${draft.habit.dosesPerDay}", style = AppTheme.type.caption, color = hc.ink)
                    }
                    Text(
                        Formats.longDate(draft.date),
                        style = AppTheme.type.body,
                        color = if (draft.dateEditable) c.accent else c.textMuted,
                        modifier = Modifier.weight(1f)
                            .let { if (draft.dateEditable) it.clickable(role = Role.Button) { pickDate = true } else it },
                    )
                }
                TextField(
                    value = draft.note,
                    onValueChange = { if (it.length <= NOTE_MAX) draft = draft.copy(note = it) },
                    placeholder = { Text("What happened?", color = c.textMuted) },
                    minLines = 3,
                    maxLines = 8,
                    textStyle = AppTheme.type.body.copy(color = c.text),
                    shape = Radius.card,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
                        focusedIndicatorColor = c.surface, unfocusedIndicatorColor = c.surface, cursorColor = c.accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (draft.note.length > NOTE_MAX - 200) {
                    Text("${draft.note.length} / $NOTE_MAX", style = AppTheme.type.footnote, color = c.textMuted)
                }
                if (initial.isExisting) {
                    PillButton("Delete this entry", { onDelete(initial) }, Modifier.fillMaxWidth(), kind = ButtonKind.GHOST)
                }
            }
        }
    }

    if (pickDate) {
        val zone = TimeZone.UTC // DatePicker works in UTC millis
        val state = rememberDatePickerState(
            initialSelectedDateMillis = draft.date.atStartOfDayIn(zone).toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val picked = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date
                        if (picked <= draft.today) draft = draft.copy(date = picked)
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text("Discard", color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

const val NOTE_MAX = 2000
