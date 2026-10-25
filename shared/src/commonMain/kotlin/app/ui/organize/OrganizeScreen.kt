package app.ui.organize

import androidx.compose.foundation.background
import app.ui.text.L
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.core.model.Folder
import app.core.model.Habit
import app.data.HabitRepository
import app.design.AppTheme
import app.design.Radius
import app.design.Sizes
import app.design.Space
import app.design.components.SheetHeader
import app.design.components.habitColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class OrganizeSection(val folder: Folder?, val habits: List<Habit>)

class OrganizeViewModel(private val repo: HabitRepository) : ViewModel() {
    val folders: StateFlow<List<Folder>> = repo.folders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sections: StateFlow<List<OrganizeSection>> =
        combine(repo.habits, repo.folders) { habits, folders ->
            val ids = folders.map { it.id }.toSet()
            listOf(OrganizeSection(null, habits.filter { it.folderId == null || it.folderId !in ids })) +
                folders.map { f -> OrganizeSection(f, habits.filter { it.folderId == f.id }) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun shiftHabit(h: Habit, delta: Int) = viewModelScope.launch { repo.shiftHabit(h.id, delta) }
    fun shiftFolder(f: Folder, delta: Int) = viewModelScope.launch { repo.shiftFolder(f.id, delta) }
    fun move(h: Habit, folderId: String?) = viewModelScope.launch { repo.moveHabit(h.id, folderId) }
}

/**
 * S04 Organise. Reordering is by ↑/↓ buttons (also exposed as accessibility actions);
 * drag-and-drop is a later improvement (see build-log.md).
 */
@Composable
fun OrganizeScreen(
    onClose: () -> Unit,
    onNewFolder: () -> Unit,
    onEditFolder: (String) -> Unit,
    vm: OrganizeViewModel = koinViewModel(),
) {
    val sections by vm.sections.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val c = AppTheme.colors

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetHeader(L.organise, onClose = onClose, modifier = Modifier.weight(1f))
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.x4, end = Space.x4, bottom = Space.x8),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(Radius.card).background(c.surface)
                        .clickable(role = Role.Button, onClick = onNewFolder)
                        .heightIn(min = Sizes.touchMin).padding(horizontal = Space.x4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, null, tint = c.accent)
                    Spacer(Modifier.width(Space.x2))
                    Text(L.newFolder, style = AppTheme.type.body, color = c.accent)
                }
            }
            sections.forEachIndexed { index, section ->
                item(key = section.folder?.id ?: "ungrouped") {
                    Section(
                        section = section,
                        canMoveUp = index > 1,
                        canMoveDown = section.folder != null && index < sections.lastIndex,
                        folders = folders,
                        onShiftFolder = { d -> section.folder?.let { vm.shiftFolder(it, d) } },
                        onEditFolder = { section.folder?.let { onEditFolder(it.id) } },
                        onShiftHabit = { h, d -> vm.shiftHabit(h, d) },
                        onMove = { h, f -> vm.move(h, f) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(
    section: OrganizeSection,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    folders: List<Folder>,
    onShiftFolder: (Int) -> Unit,
    onEditFolder: () -> Unit,
    onShiftHabit: (Habit, Int) -> Unit,
    onMove: (Habit, String?) -> Unit,
) {
    val c = AppTheme.colors
    val folder = section.folder
    Column {
        Row(Modifier.fillMaxWidth().padding(start = Space.x4), verticalAlignment = Alignment.CenterVertically) {
            if (folder != null) {
                Box(Modifier.size(Sizes.dot).clip(CircleShape).background(habitColors(folder.colorKey).fill))
                Spacer(Modifier.width(Space.x2))
            }
            Text(
                folder?.name ?: L.noFolder,
                style = AppTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.textMuted,
                modifier = Modifier.weight(1f),
            )
            if (folder != null) {
                SmallIcon(Icons.Rounded.KeyboardArrowUp, L.moveFolderUp, canMoveUp) { onShiftFolder(-1) }
                SmallIcon(Icons.Rounded.KeyboardArrowDown, L.moveFolderDown, canMoveDown) { onShiftFolder(1) }
                SmallIcon(Icons.Rounded.Edit, L.editFolder, true, onEditFolder)
            }
        }
        Column(Modifier.fillMaxWidth().clip(Radius.card).background(c.surface)) {
            if (section.habits.isEmpty()) {
                Text(L.empty, style = AppTheme.type.body, color = c.textMuted, modifier = Modifier.padding(Space.x4))
            }
            ReorderableHabits(section.habits, folders, onShiftHabit, onMove)
        }
    }
}

/**
 * Drag the handle to reorder within the section. Each swap is committed to the repository as the row
 * crosses half a neighbour's height, so the list is always in a saved state; ↑/↓ and a11y actions remain.
 */
@Composable
private fun ReorderableHabits(
    habits: List<Habit>,
    folders: List<Folder>,
    onShift: (Habit, Int) -> Unit,
    onMove: (Habit, String?) -> Unit,
) {
    val rowPx = with(LocalDensity.current) { Sizes.gridRow.toPx() }
    val haptics = LocalHapticFeedback.current
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentHabits by rememberUpdatedState(habits)

    habits.forEachIndexed { i, h ->
        key(h.id) {
            val dragging = draggingId == h.id
            val handle = Modifier.pointerInput(h.id) {
                detectDragGestures(
                    onDragStart = {
                        draggingId = h.id; dragOffset = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount.y
                        val list = currentHabits
                        val idx = list.indexOfFirst { it.id == h.id }
                        if (dragOffset > rowPx / 2 && idx in 0 until list.lastIndex) {
                            onShift(h, 1); dragOffset -= rowPx
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else if (dragOffset < -rowPx / 2 && idx > 0) {
                            onShift(h, -1); dragOffset += rowPx
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = { draggingId = null; dragOffset = 0f },
                    onDragCancel = { draggingId = null; dragOffset = 0f },
                )
            }
            HabitLine(
                h, i > 0, i < habits.lastIndex, folders, onShift, onMove,
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragOffset else 0f
                        shadowElevation = if (dragging) 12f else 0f
                        scaleX = if (dragging) 1.02f else 1f
                        scaleY = if (dragging) 1.02f else 1f
                    },
                handleModifier = handle,
            )
        }
    }
}

@Composable
private fun HabitLine(
    h: Habit,
    canUp: Boolean,
    canDown: Boolean,
    folders: List<Folder>,
    onShift: (Habit, Int) -> Unit,
    onMove: (Habit, String?) -> Unit,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().height(Sizes.gridRow).background(c.surface).padding(start = Space.x4)
            .semantics {
                customActions = buildList {
                    if (canUp) add(CustomAccessibilityAction(L.moveUp) { onShift(h, -1); true })
                    if (canDown) add(CustomAccessibilityAction(L.moveDown) { onShift(h, 1); true })
                    add(CustomAccessibilityAction(L.moveToFolder) { menu = true; true })
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Sizes.dot).clip(CircleShape).background(habitColors(h.colorKey).fill))
        Spacer(Modifier.width(Space.x3))
        Text(h.name, style = AppTheme.type.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box {
            SmallIcon(Icons.Rounded.DriveFileMove, L.moveToFolder, true) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(L.noFolder) }, onClick = { onMove(h, null); menu = false })
                folders.forEach { f ->
                    DropdownMenuItem(text = { Text(f.name) }, onClick = { onMove(h, f.id); menu = false })
                }
            }
        }
        SmallIcon(Icons.Rounded.KeyboardArrowUp, L.moveUp, canUp) { onShift(h, -1) }
        SmallIcon(Icons.Rounded.KeyboardArrowDown, L.moveDown, canDown) { onShift(h, 1) }
        Box(
            handleModifier.size(Sizes.touchMin).semantics { contentDescription = L.dragToReorder },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.DragHandle, null, tint = c.textMuted) }
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier.size(Sizes.touchMin).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) c.accent else c.textMuted.copy(alpha = 0.35f)) }
}
