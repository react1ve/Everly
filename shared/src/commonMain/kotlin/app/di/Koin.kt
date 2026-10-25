package app.di

import app.data.HabitRepository
import app.data.SnapshotHabitRepository
import app.platform.AppClock
import app.platform.ReminderCoordinator
import app.platform.SystemAppClock
import app.platform.AppSettings
import app.platform.Analytics
import app.platform.ConsentingAnalytics
import app.platform.AnalyticsSink
import app.platform.LogSink
import app.sync.SyncEngine
import app.sync.SyncableStore
import app.ui.onboarding.OnboardingViewModel
import app.ui.organize.OrganizeViewModel
import app.ui.settings.SettingsViewModel
import app.ui.detail.HabitDetailViewModel
import app.ui.folder.FolderViewModel
import app.ui.grid.GridViewModel
import app.ui.habit.HabitFormViewModel
import com.russhwolf.settings.Settings
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dev seed on first launch. Flip to false before the store build (tracked in build-log.md). */
const val SEED_SAMPLE_DATA = true

val appModule = module {
    single<AppClock> { SystemAppClock() }
    single<Settings> { Settings() }
    single { SnapshotHabitRepository(get(), SEED_SAMPLE_DATA, get<AppClock>().today()) }
    single<HabitRepository> { get<SnapshotHabitRepository>() }
    single<SyncableStore> { get<SnapshotHabitRepository>() }
    single { SyncEngine(get(), get(), get()) } // SyncBackend + AccountService come from the platform module
    single { ReminderCoordinator(get(), get(), get()) } // ReminderScheduler + BackupFiles come from the platform module
    single { AppSettings(get()) }
    single<Analytics> { ConsentingAnalytics(get(), getOrNull<AnalyticsSink>() ?: LogSink) }
    viewModel { GridViewModel(get(), get(), get()) }
    viewModel { params -> HabitFormViewModel(params.get<String>(), get(), get(), get(), get()) }
    viewModel { params -> HabitDetailViewModel(params.get<String>(), get(), get(), get()) }
    viewModel { params -> FolderViewModel(params.get<String>(), get(), get()) }
    viewModel { OrganizeViewModel(get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { OnboardingViewModel(get(), get(), get(), get(), get()) }
}

/** [platform] must register `ReminderScheduler`, `BackupFiles`, `SystemSettings`, `SyncBackend`, `AccountService` (optionally `AnalyticsSink`) (see androidApp RecurApp / iosMain MainViewController). */
fun initKoin(platform: KoinApplication.() -> Unit = {}) {
    if (GlobalContext.getOrNull() != null) return
    val app = startKoin {
        platform()
        modules(appModule)
    }
    app.koin.get<AppSettings>() // applies the chosen language before any text is produced
    app.koin.get<ReminderCoordinator>().start()
    app.koin.get<Analytics>() // pushes the stored consent to the provider at launch
    app.koin.get<SyncEngine>().start()
}
