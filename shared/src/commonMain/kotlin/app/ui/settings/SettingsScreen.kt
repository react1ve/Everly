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
import androidx.compose.ui.tooling.preview.Preview
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
import app.design.components.OptionList
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
import app.ui.preview.PreviewSurface
import app.sync.AccountService
import app.sync.AuthResult
import app.sync.Provider
import app.sync.SyncEngine
import app.sync.Account
import app.sync.SyncStatus
import app.ui.text.Formats
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
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
    val confirmDeleteAccount: Boolean = false,
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
    val accounts: AccountService,
    val sync: SyncEngine,
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

    fun setAnalytics(on: Boolean) = analytics.setConsent(on)

    fun signIn(provider: Provider) = busy {
        when (val r = accounts.signIn(provider)) {
            AuthResult.Success -> {
                analytics.track(Events.SIGNED_IN, mapOf("method" to provider.name.lowercase()))
                analytics.track(Events.SYNC_ENABLED)
                null
            }
            AuthResult.Cancelled -> null
            is AuthResult.Failed -> when (r.reason) {
                AuthResult.Reason.NO_ACCOUNT -> L.noGoogleAccount
                else -> L.signInFailed
            }
        }
    }

    fun signOut() = busy { accounts.signOut(); null }

    fun askDeleteAccount(show: Boolean) = _state.update { it.copy(confirmDeleteAccount = show) }

    fun deleteAccount() = busy {
        _state.update { it.copy(confirmDeleteAccount = false) }
        when (val r = sync.deleteAccount()) {
            AuthResult.Success -> L.accountDeleted
            AuthResult.Cancelled -> null
            is AuthResult.Failed ->
                if (r.reason == AuthResult.Reason.NEEDS_RECENT_LOGIN) L.needsRecentLogin
                else L.somethingWrong(r.detail ?: r.reason.name)
        }
    }

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

/** Settings. */
@Composable
fun SettingsScreen(onClose: () -> Unit, onCatalog: () -> Unit, vm: SettingsViewModel = koinViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val theme by vm.appSettings.theme.collectAsStateWithLifecycle()
    val language by vm.appSettings.language.collectAsStateWithLifecycle()
    val analyticsOn by vm.appSettings.analyticsEnabled.collectAsStateWithLifecycle()
    val account by vm.accounts.account.collectAsStateWithLifecycle()
    val syncStatus by vm.sync.status.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refreshPermission()
        onPauseOrDispose { }
    }
    SettingsContent(
        s, theme, language, analyticsOn, onClose = onClose, onCatalog = onCatalog,
        onTheme = vm.appSettings::setTheme, onLanguage = vm::setLanguage, onAnalytics = vm::setAnalytics,
        onAskNotifications = { vm.askNotifications() }, onOpenAppSettings = vm.system::openAppSettings,
        onOpenExactAlarms = vm.system::openExactAlarmSettings, onOpenBattery = vm.system::openBatterySettings,
        onExport = { vm.export() }, onPickImport = { vm.pickImport() }, onConfirmImport = vm::confirmImport,
        onAskDeleteAll = vm::askDeleteAll, onDeleteAll = vm::deleteAll, onDismissMessage = vm::dismissMessage,
        account = if (vm.accounts.isAvailable) AccountUi(account, syncStatus, vm.accounts.providers) else null,
        onSignIn = vm::signIn, onSignOut = { vm.signOut() },
        onAskDeleteAccount = vm::askDeleteAccount, onDeleteAccount = { vm.deleteAccount() },
    )
}

/** What the account section shows; null = cloud not available in this build (section hidden). */
data class AccountUi(val account: Account?, val status: SyncStatus, val providers: List<Provider>)

@Composable
internal fun SettingsContent(
    s: SettingsUiState,
    theme: ThemeMode,
    language: LanguagePref,
    analyticsOn: Boolean,
    onClose: () -> Unit,
    onCatalog: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onLanguage: (LanguagePref) -> Unit,
    onAnalytics: (Boolean) -> Unit,
    onAskNotifications: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenExactAlarms: () -> Unit,
    onOpenBattery: () -> Unit,
    onExport: () -> Unit,
    onPickImport: () -> Unit,
    onConfirmImport: (Boolean) -> Unit,
    onAskDeleteAll: (Boolean) -> Unit,
    onDeleteAll: () -> Unit,
    onDismissMessage: () -> Unit,
    account: AccountUi? = null,
    onSignIn: (Provider) -> Unit = {},
    onSignOut: () -> Unit = {},
    onAskDeleteAccount: (Boolean) -> Unit = {},
    onDeleteAccount: () -> Unit = {},
) {
    val c = AppTheme.colors

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
                    onSelect = onTheme,
                )
            }

            GroupedSection(caption = L.language) {
                OptionList(
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
                    onSelect = onLanguage,
                )
            }

            GroupedSection(caption = L.reminders) {
                when (s.notificationsAllowed) {
                    true -> FormLabelRow(L.notifications, L.allowed)
                    false -> FormLabelRow(L.notifications, L.offTapToAllow, onClick = onAskNotifications)
                    null -> FormLabelRow(L.notifications, "…")
                }
                if (s.notificationsAllowed == false) {
                    SectionDivider()
                    FormLabelRow(L.openNotificationSettings, null, onClick = onOpenAppSettings)
                }
                s.exactAlarms?.let { exact ->
                    SectionDivider()
                    FormLabelRow(L.onTimeReminders, if (exact) L.on else L.offTapToAllow, onClick = onOpenExactAlarms)
                }
                s.batteryExempt?.let { exempt ->
                    SectionDivider()
                    FormLabelRow(L.batteryOptimisation, if (exempt) L.notRestricted else L.restricted, onClick = onOpenBattery)
                }
            }
            if (s.exactAlarms == false || s.batteryExempt == false) {
                Text(
                    L.batteryHint,
                    style = AppTheme.type.footnote, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.x4),
                )
            }

            if (account != null) {
                AccountSection(
                    account = account.account,
                    status = account.status,
                    providers = account.providers,
                    busy = s.busy,
                    onSignIn = onSignIn,
                    onSignOut = onSignOut,
                    onDelete = { onAskDeleteAccount(true) },
                )
            }

            GroupedSection(caption = L.yourData) {
                FormLabelRow(L.exportBackup, null, onClick = onExport)
                SectionDivider()
                FormLabelRow(L.restoreBackup, null, onClick = onPickImport)
                SectionDivider()
                FormLabelRow(L.deleteEverything, null, onClick = { onAskDeleteAll(true) })
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
                        onCheckedChange = onAnalytics,
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
            onDismissRequest = onDismissMessage,
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = onDismissMessage) { Text(L.ok) } },
        )
    }
    if (s.confirmImport != null) {
        AlertDialog(
            onDismissRequest = { onConfirmImport(false) },
            title = { Text(L.replaceEverything) },
            text = { Text(L.replaceBody) },
            confirmButton = { TextButton(onClick = { onConfirmImport(true) }) { Text(L.restore, color = c.danger) } },
            dismissButton = { TextButton(onClick = { onConfirmImport(false) }) { Text(L.cancel) } },
        )
    }
    if (s.confirmDeleteAccount) {
        AlertDialog(
            onDismissRequest = { onAskDeleteAccount(false) },
            title = { Text(L.deleteAccountTitle) },
            text = { Text(L.deleteAccountBody) },
            confirmButton = { TextButton(onClick = onDeleteAccount) { Text(L.delete, color = c.danger) } },
            dismissButton = { TextButton(onClick = { onAskDeleteAccount(false) }) { Text(L.cancel) } },
        )
    }
    if (s.confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { onAskDeleteAll(false) },
            title = { Text(L.deleteEverythingTitle) },
            text = { Text(L.deleteEverythingBody) },
            confirmButton = { TextButton(onClick = onDeleteAll) { Text(L.delete, color = c.danger) } },
            dismissButton = { TextButton(onClick = { onAskDeleteAll(false) }) { Text(L.cancel) } },
        )
    }
}

@Composable
private fun SettingsSample(s: SettingsUiState, theme: ThemeMode = ThemeMode.SYSTEM, analyticsOn: Boolean = false) =
    SettingsContent(s, theme, LanguagePref.SYSTEM, analyticsOn, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

@Preview(heightDp = 1200)
@Composable
private fun SettingsPreview() = PreviewSurface {
    SettingsSample(SettingsUiState(notificationsAllowed = true, exactAlarms = true, batteryExempt = true), analyticsOn = true)
}

@Preview(heightDp = 1200)
@Composable
private fun SettingsRestrictedPreview() = PreviewSurface {
    SettingsSample(SettingsUiState(notificationsAllowed = false, exactAlarms = false, batteryExempt = false), ThemeMode.LIGHT)
}

@Preview(heightDp = 1200)
@Composable
private fun SettingsDarkPreview() = PreviewSurface(dark = true) {
    SettingsSample(SettingsUiState(notificationsAllowed = true), ThemeMode.DARK) // iOS: no exact-alarm / battery rows
}

@Preview
@Composable
private fun SettingsMessagePreview() = PreviewSurface { SettingsSample(SettingsUiState(message = L.backupSaved)) }

@Preview(heightDp = 1400)
@Composable
private fun SettingsSignedOutPreview() = PreviewSurface {
    SettingsContent(
        SettingsUiState(notificationsAllowed = true), ThemeMode.SYSTEM, LanguagePref.SYSTEM, false,
        {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        account = AccountUi(null, SyncStatus.SignedOut, listOf(Provider.GOOGLE, Provider.APPLE)),
    )
}

@Preview(heightDp = 1400)
@Composable
private fun SettingsSignedInPreview() = PreviewSurface {
    SettingsContent(
        SettingsUiState(notificationsAllowed = true), ThemeMode.SYSTEM, LanguagePref.SYSTEM, true,
        {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        account = AccountUi(Account("u1", "you@example.com", "You", Provider.GOOGLE), SyncStatus.Synced(0, pending = false), listOf(Provider.GOOGLE)),
    )
}

@Composable
private fun AccountSection(
    account: Account?,
    status: SyncStatus,
    providers: List<Provider>,
    busy: Boolean,
    onSignIn: (Provider) -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        GroupedSection(caption = L.accountSync) {
            if (account == null) {
                providers.forEachIndexed { i, p ->
                    if (i > 0) SectionDivider()
                    FormLabelRow(
                        if (p == Provider.GOOGLE) L.continueGoogle else L.continueApple,
                        null,
                        onClick = { if (!busy) onSignIn(p) },
                    )
                }
            } else {
                FormLabelRow(L.signedInAs(account.email ?: account.name ?: "—"), null)
                SectionDivider()
                FormLabelRow(syncStatusText(status), null)
                SectionDivider()
                FormLabelRow(L.signOut, null, onClick = { if (!busy) onSignOut() })
                SectionDivider()
                FormLabelRow(L.deleteAccount, null, onClick = { if (!busy) onDelete() })
            }
        }
        Text(
            if (account == null) L.syncHint else L.signOutHint,
            style = AppTheme.type.footnote, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.x4),
        )
    }
}

private fun syncStatusText(status: SyncStatus): String = when (status) {
    SyncStatus.SignedOut, SyncStatus.Connecting -> L.syncConnecting
    is SyncStatus.Synced -> if (status.pending) L.syncPending else {
        val t = Instant.fromEpochMilliseconds(status.atMillis).toLocalDateTime(TimeZone.currentSystemDefault())
        L.syncedAt(Formats.time(t))
    }
    is SyncStatus.Error -> L.syncProblem(status.detail)
}
