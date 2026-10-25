package app.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.design.AppTheme
import app.design.Radius
import app.design.Sizes
import app.design.Space
import app.design.components.ButtonKind
import app.design.components.DayCell
import app.design.components.PillButton
import app.design.components.habitColors
import app.ui.text.Formats
import app.ui.entry.EntrySheet
import app.core.model.Folder
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * S01 Grid. Habits are rows (name pinned on the left), days are columns.
 * One horizontal offset drives the date header and every row, and each row only
 * composes the columns that are on screen, so years of history stay cheap.
 */
@Composable
fun GridScreen(
    onNewHabit: () -> Unit,
    onOpenHabit: (String) -> Unit,
    onEditFolder: (String) -> Unit,
    onOrganize: () -> Unit,
    onSettings: () -> Unit,
    vm: GridViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val note by vm.entryEditor.draft.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
        when (val s = state) {
            GridUiState.Loading -> Unit
            is GridUiState.Empty -> EmptyGrid(onNewHabit, vm::addTemplate)
            is GridUiState.Content -> GridContent(
                s, vm::toggle, onOpenHabit, vm::openNote, vm::toggleFolder, onEditFolder, onNewHabit, onOrganize, onSettings,
            )
        }
    }
    note?.let { draft ->
        EntrySheet(draft, onSave = vm.entryEditor::save, onDelete = vm.entryEditor::delete, onDismiss = vm.entryEditor::close)
    }
}

@Composable
private fun GridContent(
    s: GridUiState.Content,
    onToggle: (String, LocalDate) -> Unit,
    onOpenHabit: (String) -> Unit,
    onDayNote: (String, LocalDate) -> Unit,
    onToggleFolder: (Folder) -> Unit,
    onEditFolder: (String) -> Unit,
    onNewHabit: () -> Unit,
    onOrganize: () -> Unit,
    onSettings: () -> Unit,
) {
    val density = LocalDensity.current
    val colPx = with(density) { Sizes.gridColumn.toPx() }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val stripWidthPx = with(density) { (maxWidth - Sizes.habitColumn).toPx() }.coerceAtLeast(colPx)
        val maxOffset = (s.days.size * colPx - stripWidthPx).coerceAtLeast(0f)
        // Today sits second from the right, so tomorrow is visible.
        val todayTarget = ((s.todayIndex + 2) * colPx - stripWidthPx).coerceIn(0f, maxOffset)

        var offset by remember { mutableFloatStateOf(todayTarget) }
        var initialised by remember { mutableStateOf(false) }
        LaunchedEffect(todayTarget) { if (!initialised) { offset = todayTarget; initialised = true } }

        val scrollState: ScrollableState = rememberScrollableState { delta ->
            val old = offset
            offset = (offset - delta).coerceIn(0f, maxOffset)
            old - offset
        }
        val firstVisible = floor(offset / colPx).toInt()
        val lastVisible = ceil((offset + stripWidthPx) / colPx).toInt() - 1
        val todayVisible = s.todayIndex in firstVisible..lastVisible
        val leftmostDay = s.days.getOrElse(firstVisible.coerceIn(0, s.days.lastIndex)) { s.today }

        // Now-line: drawn first so it sits behind the cells and shows in the gaps.
        if (todayVisible) {
            val headerPx = with(density) { (Sizes.gridRow + Space.x2).toPx() }
            val habitColPx = with(density) { Sizes.habitColumn.toPx() }
            Box(
                Modifier
                    .offset { IntOffset((habitColPx + s.todayIndex * colPx - offset + colPx / 2).roundToInt(), headerPx.roundToInt()) }
                    .width(Sizes.edge)
                    .fillMaxHeight()
                    .background(AppTheme.colors.accent.copy(alpha = 0.5f)),
            )
        }

        Column(Modifier.fillMaxSize().scrollable(scrollState, Orientation.Horizontal)) {
            HeaderRow(s, leftmostDay, offset = { offset })
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Sizes.bottomBar + Space.x8),
            ) {
                items(s.items, key = { it.key }, contentType = { it::class }) { item ->
                    when (item) {
                        is GridItem.FolderHeader -> FolderHeader(item, onToggleFolder, onEditFolder)
                        is GridItem.Row -> HabitRow(item.row, offset = { offset }, onToggle, onOpenHabit, onDayNote)
                    }
                }
            }
        }

        BottomActionBar(
            showToday = !todayVisible,
            onToday = { scope.launch { scrollState.animateScrollBy(offset - todayTarget) } },
            onNewHabit = onNewHabit,
            onOrganize = onOrganize,
            onSettings = onSettings,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Renders only the visible columns of [count], positioned by the shared [offset]. */
@Composable
private fun VisibleColumns(
    count: Int,
    height: Dp,
    offset: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable (index: Int) -> Unit,
) {
    val colPx = with(LocalDensity.current) { Sizes.gridColumn.toPx() }
    BoxWithConstraints(modifier.height(height).clipToBounds()) {
        val widthPx = constraints.maxWidth.toFloat()
        val o = offset()
        val first = floor(o / colPx).toInt().coerceAtLeast(0)
        val last = ceil((o + widthPx) / colPx).toInt().coerceAtMost(count - 1)
        for (i in first..last) {
            key(i) {
                Box(
                    Modifier
                        .offset { IntOffset((i * colPx - offset()).roundToInt(), 0) }
                        .width(Sizes.gridColumn)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) { content(i) }
            }
        }
    }
}

@Composable
private fun HeaderRow(s: GridUiState.Content, leftmostDay: LocalDate, offset: () -> Float) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().background(c.bg), verticalAlignment = Alignment.CenterVertically) {
        Text(
            Formats.monthLong(leftmostDay) + if (leftmostDay.year != s.today.year) " ${leftmostDay.year}" else "",
            style = AppTheme.type.headline, color = c.text,
            modifier = Modifier.width(Sizes.habitColumn).padding(start = Space.x4).semantics { heading() },
        )
        VisibleColumns(s.days.size, Sizes.gridRow + Space.x2, offset, Modifier.weight(1f)) { i ->
            val day = s.days[i]
            val isToday = day == s.today
            val weekend = Formats.isWeekend(day.dayOfWeek)
            val tint = when { isToday -> c.today; weekend -> c.weekend; else -> c.text }
            Column(
                Modifier.width(Sizes.gridCell).clip(Radius.sm)
                    .background(if (isToday) c.today.copy(alpha = 0.12f) else c.bg)
                    .padding(vertical = Space.x1),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Formats.weekdayShort(day.dayOfWeek), style = AppTheme.type.caption,
                    color = if (isToday || weekend) tint else c.textMuted)
                Text("${day.day}", style = AppTheme.type.date, color = tint)
            }
        }
    }
}

@Composable
private fun HabitRow(
    row: HabitRowUi,
    offset: () -> Float,
    onToggle: (String, LocalDate) -> Unit,
    onOpenHabit: (String) -> Unit,
    onDayNote: (String, LocalDate) -> Unit,
) {
    val c = AppTheme.colors
    val hc = habitColors(row.habit.colorKey)
    val folderBar = row.folderColorKey?.let { habitColors(it).fill }
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().height(Sizes.gridRow), verticalAlignment = Alignment.CenterVertically) {
        // pinned name column; habits in a folder get a bar in the folder's colour
        Box(Modifier.width(Space.x1).fillMaxHeight().padding(vertical = Space.x1).background(folderBar ?: c.bg))
        Row(
            Modifier.width(Sizes.habitColumn - Space.x1).fillMaxHeight().padding(vertical = Space.x1)
                .clip(Radius.md).background(hc.row)
                .clickable(role = Role.Button) { onOpenHabit(row.habit.id) }
                .semantics { contentDescription = "${row.habit.name}, ${row.subtitle}. Opens details" }
                .padding(start = Space.x3, end = Space.x1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.habit.name, style = AppTheme.type.bodyEmphasis, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.subtitle, style = AppTheme.type.footnote, color = hc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textMuted)
        }
        VisibleColumns(row.cells.size, Sizes.gridRow, offset, Modifier.weight(1f)) { i ->
            val cell = row.cells[i]
            DayCell(
                cell = cell,
                colors = hc,
                habitName = row.habit.name,
                onTap = {
                    if (cell.isTappable) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggle(row.habit.id, cell.date)
                    }
                },
                onLongPress = {
                    if (cell.isTappable) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDayNote(row.habit.id, cell.date)
                    }
                },
            )
        }
    }
}

/** Folder section header: tap to collapse, long-press to edit. Spec: components.md "FolderSectionHeader". */
@Composable
private fun FolderHeader(item: GridItem.FolderHeader, onToggle: (Folder) -> Unit, onEdit: (String) -> Unit) {
    val c = AppTheme.colors
    val fc = habitColors(item.folder.colorKey)
    val rotation by animateFloatAsState(if (item.folder.collapsed) -90f else 0f)
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onClick = { onToggle(item.folder) },
                onLongClick = { onEdit(item.folder.id) },
                onLongClickLabel = "Edit folder",
            )
            .semantics {
                heading()
                contentDescription = "${item.folder.name}, ${item.count} habits"
                stateDescription = if (item.folder.collapsed) "collapsed" else "expanded"
            }
            .padding(start = Space.x3, top = Space.x4, bottom = Space.x1),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = fc.ink, modifier = Modifier.size(Space.x5).rotate(rotation))
            Spacer(Modifier.width(Space.x1))
            Text(item.folder.name, style = AppTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.text)
            Text(" (${item.count})", style = AppTheme.type.subhead, color = c.textMuted)
        }
        Box(Modifier.padding(top = Space.x1).width(Sizes.habitColumn * 0.6f).height(Sizes.outline).background(fc.fill))
    }
}

@Composable
private fun BottomActionBar(
    showToday: Boolean,
    onToday: () -> Unit,
    onNewHabit: () -> Unit,
    onOrganize: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showToday) {
            Box(
                Modifier.shadow(Space.x2, Radius.pill).clip(Radius.pill).background(c.surfaceRaised)
                    .clickable(role = Role.Button, onClick = onToday)
                    .semantics { contentDescription = "Scroll to today" }
                    .padding(horizontal = Space.x5, vertical = Space.x3),
            ) { Text("Today", style = AppTheme.type.bodyEmphasis, color = c.text) }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.shadow(Space.x2, Radius.pill).clip(Radius.pill).background(c.surfaceRaised).padding(horizontal = Space.x1),
            horizontalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            BarIcon(Icons.Rounded.Add, "New habit", onNewHabit)
            BarIcon(Icons.Rounded.SwapVert, "Organise habits", onOrganize)
            BarIcon(Icons.Rounded.Settings, "Settings", onSettings)
        }
    }
}

@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(Sizes.touchMin + Space.x1).clip(Radius.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = AppTheme.colors.text) }
}

/** Our own empty state (none was seen in the original). Copy is ours. */
@Composable
private fun EmptyGrid(onNewHabit: () -> Unit, onTemplate: (GridViewModel.Template) -> Unit) {
    val c = AppTheme.colors
    Column(
        Modifier.fillMaxSize().padding(Space.x6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nothing repeating yet", style = AppTheme.type.display.copy(fontWeight = FontWeight.Bold), color = c.text,
            modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.x3))
        Text(
            "Add the things that come round — a pill, a filter, an insurance renewal. " +
                "Check them off and the next date works itself out.",
            style = AppTheme.type.body, color = c.textMuted,
        )
        Spacer(Modifier.height(Space.x6))
        PillButton("Add your first", onNewHabit, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.x5))
        Text("Or start from one of these", style = AppTheme.type.subhead, color = c.textMuted)
        Spacer(Modifier.height(Space.x2))
        GridViewModel.Template.entries.forEach { t ->
            PillButton(t.label, { onTemplate(t) }, Modifier.fillMaxWidth().padding(vertical = Space.x1), kind = ButtonKind.SECONDARY)
        }
    }
}

