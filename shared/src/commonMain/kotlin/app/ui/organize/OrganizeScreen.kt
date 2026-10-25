package app.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.zIndex
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
import app.design.components.CircleIconButton
import app.design.components.habitColors
import app.ui.preview.PreviewData
import app.ui.preview.PreviewSurface
import app.ui.text.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** One line of the organise list: a section header (folder, or null = no folder) or a habit under it. */
sealed interface OrganizeItem {
    val key: String
    data class Header(val folder: Folder?) : OrganizeItem { override val key = "f-${folder?.id ?: "none"}" }
    data class Item(val habit: Habit) : OrganizeItem { override val key = "h-${habit.id}" }
}

/** Habits without a folder (or whose folder is gone) first, then each folder with its habits. */
fun organizeItems(habits: List<Habit>, folders: List<Folder>): List<OrganizeItem> {
    val ids = folders.map { it.id }.toSet()
    return buildList {
        add(OrganizeItem.Header(null))
        habits.filter { it.folderId == null || it.folderId !in ids }.forEach { add(OrganizeItem.Item(it)) }
        folders.forEach { f ->
            add(OrganizeItem.Header(f))
            habits.filter { it.folderId == f.id }.forEach { add(OrganizeItem.Item(it)) }
        }
    }
}

class OrganizeViewModel(private val repo: HabitRepository) : ViewModel() {
    val folders: StateFlow<List<Folder>> = repo.folders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val items: StateFlow<List<OrganizeItem>> =
        combine(repo.habits, repo.folders, ::organizeItems)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun shiftHabit(h: Habit, delta: Int) = viewModelScope.launch { repo.shiftHabit(h.id, delta) }
    fun shiftFolder(f: Folder, delta: Int) = viewModelScope.launch { repo.shiftFolder(f.id, delta) }
    fun move(h: Habit, folderId: String?) = viewModelScope.launch { repo.moveHabit(h.id, folderId) }
    fun place(h: Habit, folderId: String?, index: Int) = viewModelScope.launch { repo.placeHabit(h.id, folderId, index) }
    fun reorderFolders(ids: List<String>) = viewModelScope.launch { repo.reorderFolders(ids) }
}

/**
 * Organise. Long-press a row (or grab its handle) and drag: within a section, or across a folder header
 * into another folder. Long-press a folder header to drag the whole folder. ⋯ and the folder button
 * offer the same moves without dragging, and every move is also an accessibility action.
 */
@Composable
fun OrganizeScreen(
    onClose: () -> Unit,
    onNewFolder: () -> Unit,
    onEditFolder: (String) -> Unit,
    vm: OrganizeViewModel = koinViewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    OrganizeContent(
        items = items,
        folders = folders,
        onClose = onClose,
        onNewFolder = onNewFolder,
        onEditFolder = onEditFolder,
        onShiftHabit = vm::shiftHabit,
        onShiftFolder = vm::shiftFolder,
        onMove = vm::move,
        onPlace = vm::place,
        onReorderFolders = vm::reorderFolders,
    )
}

@Composable
internal fun OrganizeContent(
    items: List<OrganizeItem>,
    folders: List<Folder>,
    onClose: () -> Unit,
    onNewFolder: () -> Unit,
    onEditFolder: (String) -> Unit,
    onShiftHabit: (Habit, Int) -> Unit,
    onShiftFolder: (Folder, Int) -> Unit,
    onMove: (Habit, String?) -> Unit,
    onPlace: (Habit, String?, Int) -> Unit,
    onReorderFolders: (List<String>) -> Unit,
) {
    val c = AppTheme.colors
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val drag = remember(listState) { OrganizeDrag(listState, scope, haptics) }
    drag.onPlace = onPlace
    drag.onReorderFolders = onReorderFolders

    // A finished drag keeps showing its own order until the repository's new order arrives.
    LaunchedEffect(items) { if (drag.key == null) drag.local = null }
    LaunchedEffect(drag.key != null) { if (drag.key != null) drag.autoScroll() }
    val shown = drag.local ?: items
    drag.latest = shown

    Column(Modifier.fillMaxSize().background(c.bg)) {
        OrganizeHeader(onNewFolder, onClose)
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = Space.x4, end = Space.x4, bottom = Space.x8),
        ) {
            itemsIndexed(shown, key = { _, it -> it.key }, contentType = { _, it -> it::class }) { i, item ->
                val dragging = drag.key == item.key
                val lift = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) drag.displacement() else 0f
                        shadowElevation = if (dragging) 16f else 0f
                        scaleX = if (dragging) 1.02f else 1f
                        scaleY = if (dragging) 1.02f else 1f
                    }
                    .let { if (dragging) it else it.animateItem() }
                when (item) {
                    is OrganizeItem.Header -> SectionHeader(
                        folder = item.folder,
                        first = i == 0,
                        empty = shown.getOrNull(i + 1) !is OrganizeItem.Item,
                        canMoveUp = item.folder != null && folders.firstOrNull()?.id != item.folder.id,
                        canMoveDown = item.folder != null && folders.lastOrNull()?.id != item.folder.id,
                        onEdit = { item.folder?.let { onEditFolder(it.id) } },
                        onShift = { d -> item.folder?.let { onShiftFolder(it, d) } },
                        dragModifier = if (item.folder == null) Modifier else Modifier.pointerInput(item.key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { drag.start(item) },
                                onDrag = { change, amount -> change.consume(); drag.by(amount.y) },
                                onDragEnd = drag::end,
                                onDragCancel = drag::end,
                            )
                        },
                        modifier = lift,
                    )
                    is OrganizeItem.Item -> {
                        val group = shown.groupAround(i)
                        HabitLine(
                            habit = item.habit,
                            topOfCard = shown.getOrNull(i - 1) !is OrganizeItem.Item,
                            bottomOfCard = shown.getOrNull(i + 1) !is OrganizeItem.Item,
                            canUp = group.first < i,
                            canDown = group.last > i,
                            folders = folders,
                            onShift = onShiftHabit,
                            onMove = onMove,
                            rowDrag = Modifier.pointerInput(item.key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { drag.start(item) },
                                    onDrag = { change, amount -> change.consume(); drag.by(amount.y) },
                                    onDragEnd = drag::end,
                                    onDragCancel = drag::end,
                                )
                            },
                            handleDrag = Modifier.pointerInput(item.key) {
                                detectDragGestures(
                                    onDragStart = { drag.start(item) },
                                    onDrag = { change, amount -> change.consume(); drag.by(amount.y) },
                                    onDragEnd = drag::end,
                                    onDragCancel = drag::end,
                                )
                            },
                            modifier = lift,
                        )
                    }
                }
            }
        }
    }
}

/** Indices of the habit rows in the same section as index [i]. */
private fun List<OrganizeItem>.groupAround(i: Int): IntRange {
    var a = i
    while (getOrNull(a - 1) is OrganizeItem.Item) a--
    var b = i
    while (getOrNull(b + 1) is OrganizeItem.Item) b++
    return a..b
}

/**
 * Drag state for the organise list. While dragging, [local] is a private copy of the list that is
 * reordered as the dragged row passes the middle of a neighbour; nothing is saved until the finger lifts.
 * Dragging a folder hides its habits so the folder moves as one row past whole neighbouring sections.
 */
@Stable
private class OrganizeDrag(
    private val list: LazyListState,
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
) {
    var onPlace: (Habit, String?, Int) -> Unit = { _, _, _ -> }
    var onReorderFolders: (List<String>) -> Unit = {}
    /** The list as currently shown; read when a drag starts (gesture handlers outlive recompositions). */
    var latest: List<OrganizeItem> = emptyList()

    var local by mutableStateOf<List<OrganizeItem>?>(null)
    var key by mutableStateOf<String?>(null)
        private set
    private var source: List<OrganizeItem> = emptyList()
    private var startTop = 0
    private var distance by mutableFloatStateOf(0f)

    private val items get() = local.orEmpty()
    private fun info(k: String?): LazyListItemInfo? = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == k }

    /** Where the dragged row should be drawn, relative to where the list lays it out. */
    fun displacement(): Float = info(key)?.let { startTop + distance - it.offset } ?: 0f

    fun start(item: OrganizeItem) {
        val current = latest
        source = current
        startTop = info(item.key)?.offset ?: return
        local = if (item is OrganizeItem.Header && item.folder != null) {
            current.filterNot { it is OrganizeItem.Item && it.habit.folderId == item.folder.id }
        } else current
        distance = 0f
        key = item.key
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun by(dy: Float) {
        if (key == null) return
        distance += dy
        swapIfPastNeighbour()
    }

    suspend fun autoScroll() {
        while (key != null) {
            withFrameNanos { }
            val me = info(key) ?: continue
            val top = startTop + distance
            val viewTop = list.layoutInfo.viewportStartOffset
            val viewBottom = list.layoutInfo.viewportEndOffset - list.layoutInfo.afterContentPadding
            val step = when {
                top < viewTop -> (top - viewTop).coerceAtLeast(-40f) / 3f
                top + me.size > viewBottom -> (top + me.size - viewBottom).coerceAtMost(40f) / 3f
                else -> 0f
            }
            if (step != 0f && list.scrollBy(step) != 0f) swapIfPastNeighbour()
        }
    }

    private fun swapIfPastNeighbour() {
        val k = key ?: return
        val me = info(k) ?: return
        val list = items
        val i = list.indexOfFirst { it.key == k }
        // Wait for the list to lay out the previous swap before judging the next one.
        if (i < 0 || me.index != i) return
        val top = startTop + distance
        val bottom = top + me.size
        val item = list[i]
        if (item is OrganizeItem.Item) {
            val next = info(list.getOrNull(i + 1)?.key)
            val prev = info(list.getOrNull(i - 1)?.key)
            when {
                next != null && bottom > next.offset + next.size / 2 -> moveTo(i, i + 1)
                // index 0 is the "no folder" header: nothing goes above it
                prev != null && i - 1 > 0 && top < prev.offset + prev.size / 2 -> moveTo(i, i - 1)
            }
        } else {
            // Next section: from the following header to the row before the one after it.
            if (i + 1 <= list.lastIndex) {
                val end = (i + 2..list.lastIndex).firstOrNull { list[it] is OrganizeItem.Header } ?: list.size
                val a = info(list[i + 1].key)
                val b = info(list[end - 1].key)
                if (a != null && bottom > a.offset + ((b?.let { it.offset + it.size } ?: (a.offset + a.size)) - a.offset) / 2) {
                    moveTo(i, end - 1)
                    return
                }
            }
            val p = (i - 1 downTo 0).firstOrNull { list[it] is OrganizeItem.Header } ?: return
            if ((list[p] as OrganizeItem.Header).folder == null) return // folders stay below "no folder"
            val a = info(list[p].key) ?: return
            val b = info(list[i - 1].key) ?: return
            if (top < a.offset + (b.offset + b.size - a.offset) / 2) moveTo(i, p)
        }
    }

    private fun moveTo(from: Int, to: Int) {
        val firstVisible = list.firstVisibleItemIndex
        val firstOffset = list.firstVisibleItemScrollOffset
        local = items.toMutableList().apply { add(to, removeAt(from)) }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        // The list keeps its first visible row in place by key; when that row is the one moving, pin the scroll.
        if (from == firstVisible || to == firstVisible) scope.launch { list.scrollToItem(firstVisible, firstOffset) }
    }

    fun end() {
        val k = key ?: return
        val list = items
        val i = list.indexOfFirst { it.key == k }
        when (val item = list.getOrNull(i)) {
            is OrganizeItem.Item -> {
                val header = (i downTo 0).first { list[it] is OrganizeItem.Header }
                val folderId = (list[header] as OrganizeItem.Header).folder?.id
                val index = i - header - 1
                val was = source.indexOfFirst { it.key == k }
                val wasHeader = (was downTo 0).first { source[it] is OrganizeItem.Header }
                if (folderId != (source[wasHeader] as OrganizeItem.Header).folder?.id || index != was - wasHeader - 1) {
                    onPlace(item.habit, folderId, index)
                } else local = null
            }
            is OrganizeItem.Header -> {
                val order = list.mapNotNull { (it as? OrganizeItem.Header)?.folder }
                if (order.map { it.id } != source.mapNotNull { (it as? OrganizeItem.Header)?.folder?.id }) {
                    // Put the hidden habits back under their folder until the saved order arrives.
                    val habits = source.filterIsInstance<OrganizeItem.Item>().map { it.habit }
                    local = organizeItems(habits, order)
                    onReorderFolders(order.map { it.id })
                } else local = null
            }
            null -> local = null
        }
        key = null
        distance = 0f
    }
}

@Composable
private fun OrganizeHeader(onNewFolder: () -> Unit, onClose: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mirrors the two buttons on the right so the title stays centred.
        Spacer(Modifier.width(Sizes.touchMin * 2 + Space.x2))
        Text(
            L.organise, style = AppTheme.type.title, color = c.text, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        CircleIconButton(Icons.Rounded.CreateNewFolder, L.newFolder, onNewFolder)
        Spacer(Modifier.width(Space.x2))
        Box(
            Modifier.size(Sizes.touchMin).clip(CircleShape).background(c.accent)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = L.close },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, null, tint = c.onAccent) }
    }
}

@Composable
private fun SectionHeader(
    folder: Folder?,
    first: Boolean,
    empty: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onShift: (Int) -> Unit,
    dragModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    var menu by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().background(c.bg).padding(top = if (first) Space.x2 else Space.x5)) {
        Row(
            dragModifier.fillMaxWidth().heightIn(min = Sizes.touchMin).padding(start = Space.x4)
                .semantics {
                    heading()
                    if (folder != null) customActions = buildList {
                        add(CustomAccessibilityAction(L.editFolder) { onEdit(); true })
                        if (canMoveUp) add(CustomAccessibilityAction(L.moveFolderUp) { onShift(-1); true })
                        if (canMoveDown) add(CustomAccessibilityAction(L.moveFolderDown) { onShift(1); true })
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (folder != null) {
                Icon(Icons.Rounded.Folder, null, tint = habitColors(folder.colorKey).fill, modifier = Modifier.size(Space.x5))
                Spacer(Modifier.width(Space.x2))
            }
            Text(
                folder?.name ?: L.noFolder,
                style = AppTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.textMuted,
                modifier = Modifier.weight(1f).padding(vertical = Space.x2),
            )
            if (folder != null) {
                Box {
                    SmallIcon(Icons.Rounded.MoreHoriz, L.folderOptions) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(L.editFolder) }, onClick = { menu = false; onEdit() })
                        if (canMoveUp) DropdownMenuItem(text = { Text(L.moveFolderUp) }, onClick = { menu = false; onShift(-1) })
                        if (canMoveDown) DropdownMenuItem(text = { Text(L.moveFolderDown) }, onClick = { menu = false; onShift(1) })
                    }
                }
            }
        }
        if (empty) {
            Text(
                L.empty, style = AppTheme.type.body, color = c.textMuted,
                modifier = Modifier.fillMaxWidth().clip(Radius.card).background(c.surface).padding(Space.x4),
            )
        }
    }
}

@Composable
private fun HabitLine(
    habit: Habit,
    topOfCard: Boolean,
    bottomOfCard: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    folders: List<Folder>,
    onShift: (Habit, Int) -> Unit,
    onMove: (Habit, String?) -> Unit,
    rowDrag: Modifier,
    handleDrag: Modifier,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    var menu by remember { mutableStateOf(false) }
    val corner = CardCorner
    val shape = RoundedCornerShape(
        topStart = if (topOfCard) corner else Space.x0, topEnd = if (topOfCard) corner else Space.x0,
        bottomStart = if (bottomOfCard) corner else Space.x0, bottomEnd = if (bottomOfCard) corner else Space.x0,
    )
    Column(modifier.fillMaxWidth().clip(shape).background(c.surface)) {
        Row(
            rowDrag.fillMaxWidth().heightIn(min = Sizes.gridRow).padding(start = Space.x4)
                .semantics {
                    customActions = buildList {
                        if (canUp) add(CustomAccessibilityAction(L.moveUp) { onShift(habit, -1); true })
                        if (canDown) add(CustomAccessibilityAction(L.moveDown) { onShift(habit, 1); true })
                        add(CustomAccessibilityAction(L.moveToFolder) { menu = true; true })
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(Sizes.dot).clip(CircleShape).background(habitColors(habit.colorKey).fill))
            Spacer(Modifier.width(Space.x3))
            Text(
                habit.name, style = AppTheme.type.body, color = c.text,
                modifier = Modifier.weight(1f).padding(vertical = Space.x3),
            )
            Box {
                SmallIcon(Icons.Rounded.FolderOpen, L.moveToFolder) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(L.noFolder) }, onClick = { onMove(habit, null); menu = false })
                    folders.forEach { f ->
                        DropdownMenuItem(text = { Text(f.name) }, onClick = { onMove(habit, f.id); menu = false })
                    }
                }
            }
            Box(
                handleDrag.size(Sizes.touchMin).semantics { contentDescription = L.dragToReorder },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.DragHandle, null, tint = c.textMuted) }
        }
        if (!bottomOfCard) {
            Box(Modifier.fillMaxWidth().padding(start = Space.x4 + Sizes.dot + Space.x3).height(Sizes.edge).background(c.border))
        }
    }
}

private val CardCorner = Space.x4

@Composable
private fun SmallIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(Sizes.touchMin).clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = AppTheme.colors.accent) }
}

@Composable
private fun OrganizePreviewContent() {
    val habits = PreviewData.habits
    val folders = PreviewData.folders + Folder("f-empty", "Pets", "orange", 9)
    OrganizeContent(
        items = organizeItems(habits, folders), folders = folders,
        onClose = {}, onNewFolder = {}, onEditFolder = {}, onShiftHabit = { _, _ -> }, onShiftFolder = { _, _ -> },
        onMove = { _, _ -> }, onPlace = { _, _, _ -> }, onReorderFolders = {},
    )
}

@Preview
@Composable
private fun OrganizePreview() = PreviewSurface { OrganizePreviewContent() }

@Preview
@Composable
private fun OrganizeDarkPreview() = PreviewSurface(dark = true) { OrganizePreviewContent() }

@Preview
@Composable
private fun OrganizeHeaderPreview() = PreviewSurface { OrganizeHeader({}, {}) }
