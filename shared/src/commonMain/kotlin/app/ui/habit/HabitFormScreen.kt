package app.ui.habit

import androidx.compose.foundation.background
import app.ui.text.L
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.core.model.Folder
import app.core.model.Habit
import app.core.model.Interval
import app.core.model.IntervalUnit
import app.core.model.ReminderSettings
import app.design.AppTheme
import app.design.HabitColorKey
import app.design.Radius
import app.design.Space
import app.design.components.ButtonKind
import app.design.components.ColorSwatchPicker
import app.design.components.GroupedSection
import app.design.components.NumberStepper
import app.design.components.PillButton
import app.design.components.SectionDivider
import app.design.components.SegmentedControl
import app.design.components.SheetHeader
import app.design.components.habitColorKey
import app.ui.preview.PreviewData
import app.ui.preview.PreviewSurface
import app.ui.text.Formats
import kotlinx.datetime.LocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun HabitFormScreen(
    habitId: String,
    onClose: () -> Unit,
    vm: HabitFormViewModel = koinViewModel(key = "habit-form-$habitId") { parametersOf(habitId) },
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    LaunchedEffect(s.closed) { if (s.closed) onClose() }
    HabitFormContent(
        s, folders, onClose = onClose, onSave = vm::save, onName = vm::setName, onCount = vm::setCount, onUnit = vm::setUnit,
        onDoses = vm::setDoses, onColor = vm::setColor, onFolder = vm::setFolder, onCreateFolder = vm::createFolderAndSelect,
        onReminderEnabled = vm::setReminderEnabled, onReminderTime = vm::setReminderTime, onDaysBefore = vm::setRemindDaysBefore,
        onAskDelete = vm::askDelete, onDelete = vm::delete,
    )
}

@Composable
internal fun HabitFormContent(
    s: HabitFormState,
    folders: List<Folder>,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onName: (String) -> Unit,
    onCount: (Int) -> Unit,
    onUnit: (IntervalUnit) -> Unit,
    onDoses: (Int) -> Unit,
    onColor: (HabitColorKey) -> Unit,
    onFolder: (String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onReminderEnabled: (Boolean) -> Unit,
    onReminderTime: (Int, Int) -> Unit,
    onDaysBefore: (Int) -> Unit,
    onAskDelete: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val c = AppTheme.colors

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        SheetHeader(
            title = if (s.isNew) L.newHabitTitle else L.editHabitTitle,
            onClose = onClose,
            onPrimary = onSave,
            primaryEnabled = s.canSave && s.loaded,
        )
        if (!s.loaded) return@Column

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.x4),
            verticalArrangement = Arrangement.spacedBy(Space.x6),
        ) {
            Spacer(Modifier.height(Space.x1))
            GroupedSection {
                TextField(
                    value = s.name,
                    onValueChange = onName,
                    placeholder = { Text(L.namePlaceholder, color = c.textMuted) },
                    singleLine = false,
                    maxLines = 4,
                    isError = s.showNameError,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    textStyle = AppTheme.type.body.copy(color = c.text),
                    shape = Radius.card,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, errorContainerColor = c.surface,
                        focusedIndicatorColor = c.surface, unfocusedIndicatorColor = c.surface, errorIndicatorColor = c.danger,
                        cursorColor = c.accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (s.showNameError) {
                    Text(L.nameError, style = AppTheme.type.footnote, color = c.danger,
                        modifier = Modifier.padding(horizontal = Space.x4, vertical = Space.x2))
                }
            }

            GroupedSection(caption = L.repeatsEvery) {
                NumberStepper(s.count, Formats.unitLabel(s.unit, s.count), onCount, 1..Interval.MAX_COUNT)
                SegmentedControl(
                    options = IntervalUnit.entries,
                    selected = s.unit,
                    label = { u -> L.unitName(u) },
                    onSelect = onUnit,
                )
                SectionDivider()
                NumberStepper(s.doses, L.timesThatDay(s.doses), onDoses, 1..Habit.MAX_DOSES)
            }

            GroupedSection(caption = L.colour) {
                ColorSwatchPicker(s.color, onColor)
            }

            FolderPickerSection(folders, s.folderId, onFolder, onCreateFolder)

            ReminderSection(s, onReminderEnabled, onReminderTime, onDaysBefore)

            if (!s.isNew) {
                PillButton(L.deleteHabit, { onAskDelete(true) }, Modifier.fillMaxWidth(), kind = ButtonKind.SECONDARY)
            }
            Spacer(Modifier.height(Space.x8))
        }
    }

    if (s.confirmDelete) {
        AlertDialog(
            onDismissRequest = { onAskDelete(false) },
            title = { Text(L.deleteHabitTitle) },
            text = { Text(L.deleteHabitBody) },
            confirmButton = { TextButton(onClick = onDelete) { Text(L.delete, color = c.danger) } },
            dismissButton = { TextButton(onClick = { onAskDelete(false) }) { Text(L.keepIt) } },
        )
    }
}

/** What HabitFormViewModel shows once [id] has loaded. */
private fun editState(id: String): HabitFormState {
    val h = PreviewData.habit(id)
    return HabitFormState(
        isNew = false, loaded = true, name = h.name, count = h.interval.count, unit = h.interval.unit, doses = h.dosesPerDay,
        color = habitColorKey(h.colorKey), folderId = h.folderId, reminder = h.reminder,
        notificationsAllowed = if (h.reminder.enabled) true else null,
    )
}

@Composable
private fun HabitFormSample(s: HabitFormState) =
    HabitFormContent(s, PreviewData.folders, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {})

@Preview(heightDp = 1200)
@Composable
private fun HabitFormNewPreview() = PreviewSurface { HabitFormSample(HabitFormState(isNew = true, loaded = true)) }

@Preview(heightDp = 1200)
@Composable
private fun HabitFormNewNameErrorPreview() = PreviewSurface {
    HabitFormSample(HabitFormState(isNew = true, loaded = true, showNameError = true))
}

@Preview(heightDp = 1200)
@Composable
private fun HabitFormEditPreview() = PreviewSurface {
    HabitFormSample(editState("h-eyes").copy(nextReminder = LocalDateTime(2026, 10, 22, 9, 0)))
}

@Preview(heightDp = 1200)
@Composable
private fun HabitFormEditDarkPreview() = PreviewSurface(dark = true) { HabitFormSample(editState("h-omega")) }

@Preview(heightDp = 1200)
@Composable
private fun HabitFormLongNamePreview() = PreviewSurface {
    HabitFormSample(editState("h-long").copy(reminder = ReminderSettings(enabled = true, daysBefore = 1), notificationsAllowed = false))
}

@Preview
@Composable
private fun HabitFormLoadingPreview() = PreviewSurface { HabitFormSample(HabitFormState(isNew = false, loaded = false)) }
