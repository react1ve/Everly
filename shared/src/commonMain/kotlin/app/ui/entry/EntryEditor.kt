package app.ui.entry

import androidx.compose.foundation.background
import app.ui.text.L
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
import androidx.compose.ui.tooling.preview.Preview
import app.core.model.Habit
import app.data.HabitRepository
import app.platform.Analytics
import app.platform.Events
import app.design.AppTheme
import app.design.Radius
import app.design.Sizes
import app.design.Space
import app.design.components.ButtonKind
import app.design.components.PillButton
import app.design.components.SheetHeader
import app.design.components.habitColors
import app.ui.preview.PreviewData
import app.ui.preview.PreviewSurface
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
    /** "Add entry": the user picks the day. Grid long-press: the day is fixed. */
    val dateEditable: Boolean,
    val today: LocalDate,
) {
    val done: Boolean get() = doseCount >= habit.dosesPerDay
}

/** Shared by the grid (long-press) and habit detail (add / edit entry). */
class EntryEditor(
    private val repo: HabitRepository,
    private val scope: CoroutineScope,
    private val analytics: Analytics,
) {
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
            if (edited.note.isNotBlank()) analytics.track(Events.NOTE_SAVED)
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
        EntrySheetBody(
            draft, initial.isExisting, onChange = { draft = it }, onClose = ::tryDismiss,
            onSave = { onSave(draft) }, onDelete = { onDelete(initial) }, onPickDate = { pickDate = true },
        )
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
                }) { Text(L.ok) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(L.cancel) } },
        ) { DatePicker(state = state) }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(L.discardChanges) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text(L.discard, color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(L.keepEditing) } },
        )
    }
}

/** The sheet's contents, without the sheet: also what the preview renders. */
@Composable
private fun EntrySheetBody(
    draft: EntryDraft,
    isExisting: Boolean,
    onChange: (EntryDraft) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onPickDate: () -> Unit,
) {
    val c = AppTheme.colors
    val hc = habitColors(draft.habit.colorKey)
    Column(Modifier.fillMaxWidth().imePadding().padding(bottom = Space.x6)) {
        SheetHeader(draft.habit.name, onClose = onClose, onPrimary = onSave)
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
                            onChange(draft.copy(doseCount = if (draft.done) 0 else draft.habit.dosesPerDay))
                        }
                        .semantics {
                            contentDescription = L.doneLabel
                            stateDescription = if (draft.done) L.checked else
                                if (draft.doseCount > 0) L.stPartial(draft.doseCount, draft.habit.dosesPerDay) else L.notChecked
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
                        .let { if (draft.dateEditable) it.clickable(role = Role.Button, onClick = onPickDate) else it },
                )
            }
            TextField(
                value = draft.note,
                onValueChange = { if (it.length <= NOTE_MAX) onChange(draft.copy(note = it)) },
                placeholder = { Text(L.whatHappened, color = c.textMuted) },
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
            if (isExisting) {
                PillButton(L.deleteEntry, onDelete, Modifier.fillMaxWidth(), kind = ButtonKind.GHOST)
            }
        }
    }
}

const val NOTE_MAX = 2000

private fun previewDraft(id: String, date: LocalDate = PreviewData.today, editable: Boolean = false): EntryDraft {
    val habit = PreviewData.habit(id)
    val existing = PreviewData.entriesOf(id).firstOrNull { it.date == date }
    return EntryDraft(habit, date, existing?.doseCount ?: 0, existing?.note.orEmpty(), existing != null, editable, PreviewData.today)
}

@Composable
private fun EntrySample(draft: EntryDraft) = EntrySheetBody(draft, draft.isExisting, {}, {}, {}, {}, {})

@Preview
@Composable
private fun EntrySheetPreview() = PreviewSurface { EntrySample(previewDraft("h-omega")) } // partial 1/2, with note

@Preview
@Composable
private fun EntrySheetNewPreview() = PreviewSurface {
    EntrySample(previewDraft("h-stretch", editable = true).let { it.copy(doseCount = it.habit.dosesPerDay) })
}

@Preview
@Composable
private fun EntrySheetDarkPreview() = PreviewSurface(dark = true) {
    EntrySample(previewDraft("h-swim", PreviewData.entriesOf("h-swim").first { it.note != null }.date))
}

@Preview
@Composable
private fun EntrySheetLongNamePreview() = PreviewSurface { EntrySample(previewDraft("h-long")) }
