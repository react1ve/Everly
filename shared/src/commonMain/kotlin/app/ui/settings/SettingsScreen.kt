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
import app.platform.Analytics
import app.platform.Events
import app.platform.LanguagePref
import app.ui.text.L
import app.design.Sizes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.Alignment
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
    private val analytics: Analytics,
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
        if (saved) { analytics.track(Events.BACKUP_EXPORTED); L.backupSaved } else null
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
                analytics.track(Events.BACKUP_RESTORED)
                L.restored(r.habits, r.entries)
            } catch (e: BackupFormatException) {
                when (e.reason) {
                    BackupFormatException.Reason.NEWER_VERSION -> L.backupNewer
                    BackupFormatException.Reason.NOT_A_BACKUP -> L.backupInvalid
                }
            }
        }
    }

    fun askDeleteAll(show: Boolean) = _state.update { it.copy(confirmDeleteAll = show) }

    fun deleteAll() = busy {
        _state.update { it.copy(confirmDeleteAll = false) }
        repo.deleteAll()
        coordinator.rescheduleAll()
        L.everythingDeleted
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun setLanguage(pref: LanguagePref) {
        appSettings.setLanguage(pref)
        viewModelScope.launch { coordinator.rescheduleAll() } // notification texts in the new language
    }

    private fun busy(block: suspend () -> String?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val msg = try { block() } catch (e: Exception) { L.somethingWrong(e.message ?: e::class.simpleName.orEmpty()) }
            _state.update { it.copy(busy = false, message = msg ?: it.message) }
        }
    }
}

/** S06 Settings. */
@Composable
fun SettingsScreen(onClose: () -> Unit, onCatalog: () -> Unit, vm: SettingsViewModel = koinViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val theme by vm.appSettings.theme.collectAsStateWithLifecycle()
    val language by vm.appSettings.language.collectAsStateWithLifecycle()
    val analyticsOn by vm.appSettings.analyticsEnabled.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    LifecycleResumeEffect(Unit) {
        vm.refreshPermission()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        SheetHeader(L.settings, onClose = onClose)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.x4),
            verticalArrangement = Arrangement.spacedBy(Space.x6),
        ) {
            GroupedSection(caption = L.appearance) {
                SegmentedControl(
                    options = ThemeMode.entries,
                    selected = theme,
                    label = { when (it) { ThemeMode.SYSTEM -> L.themeAuto; ThemeMode.LIGHT -> L.themeLight; ThemeMode.DARK -> L.themeDark } },
                    onSelect = vm.appSettings::setTheme,
                )
            }

            GroupedSection(caption = L.language) {
                SegmentedControl(
                    options = LanguagePref.entries,
                    selected = language,
                    label = {
                        when (it) {
                            LanguagePref.SYSTEM -> L.languageAuto
                            LanguagePref.EN -> "English"
                            LanguagePref.RU -> "Русский"
                            LanguagePref.UZ -> "O‘zbek"
                        }
                    },
                    onSelect = vm::setLanguage,
                )
            }

            GroupedSection(caption = L.reminders) {
                when (s.notificationsAllowed) {
                    true -> FormLabelRow(L.notifications, L.allowed)
                    false -> FormLabelRow(L.notifications, L.offTapToAllow, onClick = { vm.askNotifications() })
                    null -> FormLabelRow(L.notifications, "…")
                }
                if (s.notificationsAllowed == false) {
                    SectionDivider()
                    FormLabelRow(L.openNotificationSettings, null, onClick = vm.system::openAppSettings)
                }
                s.exactAlarms?.let { exact ->
                    SectionDivider()
                    FormLabelRow(L.onTimeReminders, if (exact) L.on else L.offTapToAllow, onClick = vm.system::openExactAlarmSettings)
                }
                s.batteryExempt?.let { exempt ->
                    SectionDivider()
                    FormLabelRow(L.batteryOptimisation, if (exempt) L.notRestricted else L.restricted, onClick = vm.system::openBatterySettings)
                }
            }
            if (s.exactAlarms == false || s.batteryExempt == false) {
                Text(
                    L.batteryHint,
                    style = AppTheme.type.footnote, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.x4),
                )
            }

            GroupedSection(caption = L.yourData) {
                FormLabelRow(L.exportBackup, null, onClick = { vm.export() })
                SectionDivider()
                FormLabelRow(L.restoreBackup, null, onClick = { vm.pickImport() })
                SectionDivider()
                FormLabelRow(L.deleteEverything, null, onClick = { vm.askDeleteAll(true) })
            }
            Text(
                L.dataHint,
                style = AppTheme.type.footnote, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.x4),
            )

            GroupedSection {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Sizes.touchMin).padding(horizontal = Space.x4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(L.shareUsage, style = AppTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                    Switch(
                        checked = analyticsOn,
                        onCheckedChange = vm.appSettings::setAnalytics,
                        colors = SwitchDefaults.colors(checkedTrackColor = c.success, checkedThumbColor = c.surface),
                    )
                }
            }
            Text(L.shareUsageHint, style = AppTheme.type.footnote, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.x4))

            GroupedSection(caption = L.about) {
                FormLabelRow(L.version, "0.1.0")
                SectionDivider()
                FormLabelRow(L.designCatalog, null, onClick = onCatalog)
            }
        }
    }

    s.message?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::dismissMessage,
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::dismissMessage) { Text(L.ok) } },
        )
    }
    if (s.confirmImport != null) {
        AlertDialog(
            onDismissRequest = { vm.confirmImport(false) },
            title = { Text(L.replaceEverything) },
            text = { Text(L.replaceBody) },
            confirmButton = { TextButton(onClick = { vm.confirmImport(true) }) { Text(L.restore, color = c.danger) } },
            dismissButton = { TextButton(onClick = { vm.confirmImport(false) }) { Text(L.cancel) } },
        )
    }
    if (s.confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { vm.askDeleteAll(false) },
            title = { Text(L.deleteEverythingTitle) },
            text = { Text(L.deleteEverythingBody) },
            confirmButton = { TextButton(onClick = vm::deleteAll) { Text(L.delete, color = c.danger) } },
            dismissButton = { TextButton(onClick = { vm.askDeleteAll(false) }) { Text(L.cancel) } },
        )
    }
}
