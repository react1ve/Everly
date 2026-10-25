package app.ui.habit

import androidx.compose.foundation.clickable
import app.ui.text.L
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import app.core.model.Folder
import app.core.model.ReminderSettings
import app.design.AppTheme
import app.design.Sizes
import app.design.Space
import app.design.components.FormLabelRow
import app.design.components.GroupedSection
import app.design.components.SectionDivider
import app.ui.preview.PreviewData
import app.ui.preview.PreviewSurface
import app.ui.text.Formats
import kotlinx.datetime.LocalDateTime

@Composable
fun FolderPickerSection(
    folders: List<Folder>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCreate: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val current = folders.firstOrNull { it.id == selectedId }

    GroupedSection(caption = L.folder) {
        Box {
            FormLabelRow(L.folder, current?.name ?: L.none, onClick = { open = true })
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(L.none) }, onClick = { onSelect(null); open = false })
                folders.forEach { f ->
                    DropdownMenuItem(text = { Text(f.name) }, onClick = { onSelect(f.id); open = false })
                }
                DropdownMenuItem(text = { Text(L.newFolderEllipsis, color = AppTheme.colors.accent) }, onClick = { open = false; naming = true })
            }
        }
    }

    if (naming) {
        NameDialog(
            title = L.newFolder,
            confirm = L.create,
            onConfirm = { onCreate(it); naming = false },
            onDismiss = { naming = false },
        )
    }
}

@Composable
fun NameDialog(title: String, confirm: String, initial: String = "", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it.take(60) }, singleLine = true, placeholder = { Text(L.folderExample) })
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L.cancel) } },
    )
}

private val DAYS_BEFORE_OPTIONS = listOf(0, 1, 2, 3, 7, 14, 30)

@Composable
fun ReminderSection(
    s: HabitFormState,
    onEnabled: (Boolean) -> Unit,
    onTime: (Int, Int) -> Unit,
    onDaysBefore: (Int) -> Unit,
) {
    val c = AppTheme.colors
    var pickTime by remember { mutableStateOf(false) }
    var pickBefore by remember { mutableStateOf(false) }

    GroupedSection(caption = L.reminder) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Sizes.touchMin).padding(horizontal = Space.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(L.remindMe, style = AppTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
            Switch(
                checked = s.reminder.enabled,
                onCheckedChange = onEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = c.success, checkedThumbColor = c.surface),
            )
        }
        if (s.reminder.enabled) {
            SectionDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = Sizes.touchMin)
                    .clickable(role = Role.Button) { pickTime = true }
                    .semantics { contentDescription = L.reminderTimeA11y(Formats.time(s.reminder.minuteOfDay)) }
                    .padding(horizontal = Space.x4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(L.time, style = AppTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                Box(Modifier.padding(vertical = Space.x2)) {
                    Text(
                        Formats.time(s.reminder.minuteOfDay),
                        style = AppTheme.type.body, color = c.text,
                        modifier = Modifier.padding(horizontal = Space.x3, vertical = Space.x1),
                    )
                }
            }
            SectionDivider()
            Box {
                FormLabelRow(L.whenLabel, Formats.daysBefore(s.reminder.daysBefore), onClick = { pickBefore = true })
                DropdownMenu(expanded = pickBefore, onDismissRequest = { pickBefore = false }) {
                    DAYS_BEFORE_OPTIONS.forEach { d ->
                        DropdownMenuItem(text = { Text(Formats.daysBefore(d)) }, onClick = { onDaysBefore(d); pickBefore = false })
                    }
                }
            }
            SectionDivider()
            FormLabelRow(L.nextReminder, s.nextReminder?.let(Formats::reminderMoment) ?: "—")
            if (s.notificationsAllowed == false) {
                Text(
                    L.notificationsOffWarning,
                    style = AppTheme.type.footnote, color = c.danger,
                    modifier = Modifier.padding(horizontal = Space.x4, vertical = Space.x2),
                )
            }
        }
    }

    if (pickTime) {
        val state = rememberTimePickerState(
            initialHour = s.reminder.minuteOfDay / 60,
            initialMinute = s.reminder.minuteOfDay % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text(L.reminderTimeTitle) },
            text = { TimePicker(state = state) },
            confirmButton = { TextButton(onClick = { onTime(state.hour, state.minute); pickTime = false }) { Text(L.ok) } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(L.cancel) } },
        )
    }
}

@Preview
@Composable
private fun FolderPickerSectionPreview() = PreviewSurface {
    Column(Modifier.padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
        FolderPickerSection(PreviewData.folders, null, {}, {})
        FolderPickerSection(PreviewData.folders, PreviewData.folders.first().id, {}, {})
    }
}

@Preview
@Composable
private fun NameDialogPreview() = PreviewSurface { NameDialog(L.newFolder, L.create, "Garden", {}, {}) }

@Preview(heightDp = 700)
@Composable
private fun ReminderSectionPreview() = PreviewSurface {
    val on = ReminderSettings(enabled = true, minuteOfDay = 8 * 60 + 30, daysBefore = 7)
    Column(Modifier.padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
        ReminderSection(HabitFormState(), {}, { _, _ -> }, {}) // off
        ReminderSection(HabitFormState(reminder = on, notificationsAllowed = true, nextReminder = LocalDateTime(2026, 10, 22, 8, 30)), {}, { _, _ -> }, {})
        ReminderSection(HabitFormState(reminder = on, notificationsAllowed = false), {}, { _, _ -> }, {}) // OS refused
    }
}

@Preview(heightDp = 400)
@Composable
private fun ReminderSectionDarkPreview() = PreviewSurface(dark = true) {
    Column(Modifier.padding(Space.x4)) {
        ReminderSection(HabitFormState(reminder = ReminderSettings(enabled = true), notificationsAllowed = true, nextReminder = PreviewData.now), {}, { _, _ -> }, {})
    }
}
