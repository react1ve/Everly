package app.ui.detail

import androidx.compose.foundation.background
import app.ui.text.L
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.core.model.Entry
import app.design.AppTheme
import app.design.Radius
import app.design.Sizes
import app.design.Space
import app.design.components.FormLabelRow
import app.design.components.GroupedSection
import app.design.components.SectionDivider
import app.design.components.SheetHeader
import app.design.components.habitColors
import app.ui.entry.EntrySheet
import app.ui.preview.PreviewData
import app.ui.preview.PreviewSurface
import app.ui.text.Formats
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Habit detail: year view, settings summary, entries. */
@Composable
fun HabitDetailScreen(
    habitId: String,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    vm: HabitDetailViewModel = koinViewModel(key = "detail-$habitId") { parametersOf(habitId) },
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val entryDraft by vm.entryEditor.draft.collectAsStateWithLifecycle()
    val c = AppTheme.colors

    val s = state
    LaunchedEffect(s) { if (s is DetailUiState.Gone) onClose() }
    if (s !is DetailUiState.Content) {
        Box(Modifier.fillMaxSize().background(c.bg)); return
    }
    HabitDetailContent(
        s, onClose = onClose, onEdit = onEdit, onPreviousYear = vm::previousYear, onNextYear = vm::nextYear,
        onAddEntry = vm::addEntry, onEditEntry = vm::editEntry, onDeleteEntry = { vm.deleteEntry(it) },
    )

    entryDraft?.let { draft ->
        EntrySheet(draft, onSave = vm.entryEditor::save, onDelete = vm.entryEditor::delete, onDismiss = vm.entryEditor::close)
    }
}

@Composable
internal fun HabitDetailContent(
    s: DetailUiState.Content,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onAddEntry: () -> Unit,
    onEditEntry: (Entry) -> Unit,
    onDeleteEntry: (Entry) -> Unit,
) {
    val c = AppTheme.colors
    val hc = habitColors(s.habit.colorKey)

    Column(Modifier.fillMaxSize().background(c.bg)) {
        SheetHeader(s.habit.name, onClose = onClose, onPrimary = onEdit, primaryLabel = L.edit)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.x4, end = Space.x4, bottom = Space.x8),
            verticalArrangement = Arrangement.spacedBy(Space.x5),
        ) {
            item {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = Space.x4), verticalAlignment = Alignment.CenterVertically) {
                        Text(L.yearView, style = AppTheme.type.subhead, color = c.textMuted, modifier = Modifier.weight(1f))
                        YearArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, L.previousYear, s.year > s.minYear, onPreviousYear)
                        Text("${s.year}", style = AppTheme.type.bodyEmphasis, color = c.accent)
                        YearArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, L.nextYear, s.year < s.maxYear, onNextYear)
                    }
                    Column(Modifier.fillMaxWidth().clip(Radius.card).background(c.surface).padding(vertical = Space.x3)) {
                        YearHeatmap(s.year, s.yearCells, s.doneThisYear, hc, Modifier.fillMaxWidth())
                        Text(
                            L.daysDoneIn(s.doneThisYear, s.year),
                            style = AppTheme.type.footnote, color = c.textMuted,
                            modifier = Modifier.padding(start = Space.x4, top = Space.x2),
                        )
                    }
                }
            }
            item {
                GroupedSection {
                    FormLabelRow(L.repeats, Formats.interval(s.habit.interval) +
                        if (s.habit.dosesPerDay > 1) " · ${L.perDay(s.habit.dosesPerDay)}" else "")
                    SectionDivider()
                    FormLabelRow(L.nextDue, Formats.weekdayDate(s.nextDue))
                    SectionDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchMin).padding(horizontal = Space.x4),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(L.colour, style = AppTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                        Box(Modifier.size(Sizes.dot * 2).clip(CircleShape).background(hc.fill).border(Sizes.edge, hc.outline, CircleShape))
                    }
                    SectionDivider()
                    FormLabelRow(L.folder, s.folder?.name ?: L.none)
                }
            }
            item {
                GroupedSection {
                    FormLabelRow(L.reminder, Formats.reminderSummary(s.habit.reminder))
                    s.nextReminder?.let {
                        SectionDivider()
                        FormLabelRow(L.nextReminder, Formats.reminderMoment(it))
                    }
                }
            }
            item {
                Text(L.entries, style = AppTheme.type.subhead, color = c.textMuted, modifier = Modifier.padding(start = Space.x4))
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clip(Radius.card).background(c.surface)
                        .clickable(role = Role.Button, onClick = onAddEntry)
                        .heightIn(min = Sizes.touchMin).padding(horizontal = Space.x4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AddCircle, null, tint = c.accent)
                    Spacer(Modifier.size(Space.x2))
                    Text(L.addEntry, style = AppTheme.type.body, color = c.accent)
                }
            }
            if (s.entries.isEmpty()) {
                item {
                    Text(L.nothingLogged(s.year), style = AppTheme.type.body, color = c.textMuted,
                        modifier = Modifier.padding(start = Space.x4))
                }
            }
            items(s.entries, key = { it.date.toString() }) { entry ->
                EntryRow(entry, s.habit.dosesPerDay, hc, onClick = { onEditEntry(entry) }, onDelete = { onDeleteEntry(entry) })
            }
        }
    }
}

@Composable
private fun YearArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier.size(Sizes.touchMin).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) c.accent else c.textMuted.copy(alpha = 0.4f)) }
}

@Composable
private fun EntryRow(entry: Entry, doses: Int, hc: app.design.HabitColors, onClick: () -> Unit, onDelete: () -> Unit) {
    val c = AppTheme.colors
    val done = entry.doseCount >= doses
    var menu by remember { mutableStateOf(false) }
    // Long-press for a context menu (delete), like the original's Mac context menu.
    Box {
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(text = { Text(L.delete, color = c.danger) }, onClick = { menu = false; onDelete() })
    }
    Row(
        Modifier.fillMaxWidth().clip(Radius.card).background(c.surface)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = { menu = true }, onLongClickLabel = L.deleteEntry)
            .heightIn(min = Sizes.touchMin).padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Formats.longDate(entry.date), style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
            entry.note?.let { Text(it, style = AppTheme.type.body, color = c.textMuted, maxLines = 2) }
        }
        Spacer(Modifier.size(Space.x2))
        Box(
            Modifier.size(Sizes.dot * 2.4f).clip(CircleShape)
                .background(if (done) hc.fill else c.surface)
                .border(Sizes.edge, if (done) hc.outline else c.borderInput, CircleShape)
                .semantics { contentDescription = if (done) L.stDone else if (entry.doseCount > 0) L.stPartial(entry.doseCount, doses) else L.noteOnly },
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Rounded.Check, null, tint = hc.onFill, modifier = Modifier.size(Sizes.dot * 1.6f))
            else if (entry.doseCount > 0) Text("${entry.doseCount}", style = AppTheme.type.caption, color = hc.ink)
        }
    }
    }
}

@Composable
private fun DetailSample(id: String, year: Int = PreviewData.today.year) =
    HabitDetailContent(PreviewData.detail(id, year), {}, {}, {}, {}, {}, {}, {})

@Preview(heightDp = 1000)
@Composable
private fun HabitDetailSample() = PreviewSurface { DetailSample("h-omega") }

@Preview(heightDp = 1000)
@Composable
private fun HabitDetailDarkPreview() = PreviewSurface(dark = true) { DetailSample("h-swim") }

@Preview(heightDp = 1000)
@Composable
private fun HabitDetailLongNamePreview() = PreviewSurface { DetailSample("h-long") }

@Preview(heightDp = 1000)
@Composable
private fun HabitDetailReminderPreview() = PreviewSurface { DetailSample("h-eyes") }
