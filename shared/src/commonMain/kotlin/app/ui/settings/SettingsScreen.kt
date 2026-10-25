package app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.data.BackupFormatException
import app.data.HabitRepository
import app.design.AppTheme
import app.design.Space
import app.design.components.FormLabelRow
import app.design.components.GroupedSection
import app.design.components.SectionDivider
import app.design.components.SegmentedControl
import app.design.components.SheetHeader
import app.platform.AppClock
import app.platform.AppSettings
import app.platform.BackupFiles
import app.platform.ReminderCoordinator
import app.platform.ReminderScheduler
import app.platform.ThemeMode
import app.platform.SystemSettings
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class SettingsUiState(
    val notificationsAllowed: Boolean? = null,
    val exactAlarms: Boolean? = null,
    val batteryExempt: Boolean? = null,
    val message: String? = null,
    val confirmImport: String? = null, // the backup text waiting for "replace everything?"
    val confirmDeleteAll: Boolean = false,
    val busy: Boolean = false,
)

class SettingsViewModel(
    private val repo: HabitRepository,
    private val files: BackupFiles,
    private val reminders: ReminderScheduler,
    private val coordinator: ReminderCoordinator,
    private val clock: AppClock,
    val appSettings: AppSettings,
    val system: SystemSettings,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** Called on every resume: the user may have changed things in system settings. */
    fun refreshPermission() = viewModelScope.launch {
        val allowed = reminders.hasPermission()
        val exact = system.exactAlarmsAllowed()
        val wasExact = _state.value.exactAlarms
        _state.update { it.copy(notificationsAllowed = allowed, exactAlarms = exact, batteryExempt = system.ignoringBatteryOptimisations()) }
        if (exact == true && wasExact == false) coordinator.rescheduleAll() // upgrade inexact alarms to exact
    }

    fun askNotifications() = viewModelScope.launch {
        val ok = reminders.requestPermission()
        _state.update { it.copy(notificationsAllowed = ok) }
        if (ok) coordinator.rescheduleAll()
    }

    fun export() = busy {
        val name = "recurring-backup-${clock.today()}.json"
        val saved = files.save(name, repo.exportBackup())
        if (saved) "Backup saved." else null
    }

    fun pickImport() = busy {
        val text = files.open() ?: return@busy null
        _state.update { it.copy(confirmImport = text) }
        null
    }

    fun confirmImport(yes: Boolean) {
        val text = _state.value.confirmImport
        _state.update { it.copy(confirmImport = null) }
        if (!yes || text == null) return
        busy {
            try {
                val r = repo.importBackup(text)
                coordinator.rescheduleAll()
                "Restored ${r.habits} habits and ${r.entries} entries."
            } catch (e: BackupFormatException) {
                e.message
            }
        }
    }

    fun askDeleteAll(show: Boolean) = _state.update { it.copy(confirmDeleteAll = show) }

    fun deleteAll() = busy {
        _state.update { it.copy(confirmDeleteAll = false) }
        repo.deleteAll()
        coordinator.rescheduleAll()
        "Everything deleted."
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun busy(block: suspend () -> String?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val msg = try { block() } catch (e: Exception) { "Something went wrong: ${e.message}" }
            _state.update { it.copy(busy = false, message = msg ?: it.message) }
        }
    }
}

/** S06 Settings. */
@Composable
fun SettingsScreen(onClose: () -> Unit, onCatalog: () -> Unit, vm: SettingsViewModel = koinViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val theme by vm.appSettings.theme.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    LifecycleResumeEffect(Unit) {
        vm.refreshPermission()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        SheetHeader("Settings", onClose = onClose)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.x4),
            verticalArrangement = Arrangement.spacedBy(Space.x6),
        ) {
            GroupedSection(caption = "Appearance") {
                SegmentedControl(
                    options = ThemeMode.entries,
                    selected = theme,
                    label = { when (it) { ThemeMode.SYSTEM -> "Auto"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" } },
                    onSelect = vm.appSettings::setTheme,
                )
            }

            GroupedSection(caption = "Reminders") {
                when (s.notificationsAllowed) {
                    true -> FormLabelRow("Notifications", "Allowed")
                    false -> FormLabelRow("Notifications", "Off — tap to allow", onClick = { vm.askNotifications() })
                    null -> FormLabelRow("Notifications", "…")
                }
                if (s.notificationsAllowed == false) {
                    SectionDivider()
                    FormLabelRow("Open notification settings", null, onClick = vm.system::openAppSettings)
                }
                s.exactAlarms?.let { exact ->
                    SectionDivider()
                    FormLabelRow("On-time reminders", if (exact) "On" else "Off — tap to allow", onClick = vm.system::openExactAlarmSettings)
                }
                s.batteryExempt?.let { exempt ->
                    SectionDivider()
                    FormLabelRow("Battery optimisation", if (exempt) "Not restricted" else "Restricted", onClick = vm.system::openBatterySettings)
                }
            }
            if (s.exactAlarms == false || s.batteryExempt == false) {
                Text(
                    "Without on-time reminders they can arrive a few minutes late. Some phones also stop reminders " +
                        "for apps under battery optimisation — if yours go missing, set this app to “Not optimised”.",
                    style = AppTheme.type.footnote, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.x4),
                )
            }

            GroupedSection(caption = "Your data") {
                FormLabelRow("Export a backup", null, onClick = { vm.export() })
                SectionDivider()
                FormLabelRow("Restore from a backup", null, onClick = { vm.pickImport() })
                SectionDivider()
                FormLabelRow("Delete everything", null, onClick = { vm.askDeleteAll(true) })
            }
            Text(
                "Everything stays on this device. A backup is a single file you keep — use it to move to a new phone.",
                style = AppTheme.type.footnote, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.x4),
            )

            GroupedSection(caption = "About") {
                FormLabelRow("Version", "0.1.0")
                SectionDivider()
                FormLabelRow("Design catalog", null, onClick = onCatalog)
            }
        }
    }

    s.message?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::dismissMessage,
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("OK") } },
        )
    }
    if (s.confirmImport != null) {
        AlertDialog(
            onDismissRequest = { vm.confirmImport(false) },
            title = { Text("Replace everything?") },
            text = { Text("Restoring replaces all habits, folders and history on this device with the backup.") },
            confirmButton = { TextButton(onClick = { vm.confirmImport(true) }) { Text("Restore", color = c.danger) } },
            dismissButton = { TextButton(onClick = { vm.confirmImport(false) }) { Text("Cancel") } },
        )
    }
    if (s.confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { vm.askDeleteAll(false) },
            title = { Text("Delete everything?") },
            text = { Text("All habits, folders, notes and reminders on this device. Export a backup first if you might want them back.") },
            confirmButton = { TextButton(onClick = vm::deleteAll) { Text("Delete", color = c.danger) } },
            dismissButton = { TextButton(onClick = { vm.askDeleteAll(false) }) { Text("Cancel") } },
        )
    }
}
