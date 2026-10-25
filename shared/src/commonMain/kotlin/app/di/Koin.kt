package app.di

import app.data.HabitRepository
import app.data.SnapshotHabitRepository
import app.platform.AppClock
import app.platform.SystemAppClock
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
    single<HabitRepository> { SnapshotHabitRepository(get(), SEED_SAMPLE_DATA, get<AppClock>().today()) }
    viewModel { GridViewModel(get(), get()) }
    viewModel { params -> HabitFormViewModel(params.get<String>(), get(), get()) }
}

fun initKoin(platform: KoinApplication.() -> Unit = {}) {
    if (GlobalContext.getOrNull() != null) return
    startKoin {
        platform()
        modules(appModule)
    }
}
