package app.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.data.HabitRepository
import app.design.AppTheme
import app.design.HabitColorKey
import app.design.Radius
import app.design.Space
import app.design.components.ButtonKind
import app.design.components.ColorSwatchPicker
import app.design.components.GroupedSection
import app.design.components.PillButton
import app.design.components.SheetHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class FolderFormState(
    val isNew: Boolean,
    val loaded: Boolean,
    val name: String = "",
    val color: HabitColorKey = HabitColorKey.TEAL,
    val habitCount: Int = 0,
    val confirmDelete: Boolean = false,
    val closed: Boolean = false,
)

/** S07 Folder: name, colour, delete. [folderId] empty = new. */
class FolderViewModel(private val folderId: String, private val repo: HabitRepository) : ViewModel() {
    private val _state = MutableStateFlow(FolderFormState(isNew = folderId.isEmpty(), loaded = folderId.isEmpty()))
    val state: StateFlow<FolderFormState> = _state.asStateFlow()

    init {
        if (folderId.isNotEmpty()) viewModelScope.launch {
            val f = repo.folder(folderId)
            if (f == null) { _state.update { it.copy(closed = true) }; return@launch }
            val count = repo.habits.first().count { it.folderId == folderId }
            _state.update {
                it.copy(
                    loaded = true, name = f.name, habitCount = count,
                    color = HabitColorKey.entries.firstOrNull { k -> k.name.equals(f.colorKey, true) } ?: HabitColorKey.TEAL,
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v.take(60)) }
    fun setColor(v: HabitColorKey) = _state.update { it.copy(color = v) }
    fun askDelete(v: Boolean) = _state.update { it.copy(confirmDelete = v) }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) return
        viewModelScope.launch {
            if (s.isNew) repo.createFolder(s.name, s.color.name.lowercase())
            else repo.updateFolder(folderId, s.name, s.color.name.lowercase())
            _state.update { it.copy(closed = true) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            repo.deleteFolder(folderId)
            _state.update { it.copy(confirmDelete = false, closed = true) }
        }
    }
}

@Composable
fun FolderScreen(
    folderId: String,
    onClose: () -> Unit,
    vm: FolderViewModel = koinViewModel(key = "folder-$folderId") { parametersOf(folderId) },
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    LaunchedEffect(s.closed) { if (s.closed) onClose() }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        SheetHeader(
            if (s.isNew) "New folder" else "Edit folder",
            onClose = onClose, onPrimary = vm::save, primaryEnabled = s.loaded && s.name.isNotBlank(),
        )
        if (!s.loaded) return@Column
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.x4),
            verticalArrangement = Arrangement.spacedBy(Space.x6),
        ) {
            GroupedSection {
                TextField(
                    value = s.name, onValueChange = vm::setName, singleLine = true,
                    placeholder = { Text("Folder name, e.g. Pets", color = c.textMuted) },
                    textStyle = AppTheme.type.body.copy(color = c.text), shape = Radius.card,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
                        focusedIndicatorColor = c.surface, unfocusedIndicatorColor = c.surface, cursorColor = c.accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            GroupedSection(caption = "Colour") { ColorSwatchPicker(s.color, vm::setColor) }
            if (!s.isNew) {
                PillButton("Delete folder", { vm.askDelete(true) }, Modifier.fillMaxWidth(), kind = ButtonKind.SECONDARY)
            }
        }
    }

    if (s.confirmDelete) {
        AlertDialog(
            onDismissRequest = { vm.askDelete(false) },
            title = { Text("Delete “${s.name}”?") },
            text = {
                Text(
                    if (s.habitCount == 0) "The folder is empty." else
                        "Its ${s.habitCount} habit${if (s.habitCount == 1) "" else "s"} stay, without a folder. No history is lost.",
                )
            },
            confirmButton = { TextButton(onClick = vm::delete) { Text("Delete", color = c.danger) } },
            dismissButton = { TextButton(onClick = { vm.askDelete(false) }) { Text("Cancel") } },
        )
    }
}
