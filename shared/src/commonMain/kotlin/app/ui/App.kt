package app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.design.AppTheme
import app.ui.catalog.DesignCatalogScreen
import app.ui.detail.HabitDetailScreen
import app.ui.folder.FolderScreen
import app.ui.grid.GridScreen
import app.ui.habit.HabitFormScreen
import app.ui.organize.OrganizeScreen
import app.ui.settings.SettingsScreen
import app.platform.AppSettings
import app.platform.ThemeMode
import app.platform.DeepLinks
import app.ui.onboarding.OnboardingScreen
import androidx.compose.runtime.key
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.koinInject
import kotlinx.serialization.Serializable

// Routes: one per screen ID in recon.md.
// S05 (day note) and S08 (entry) are bottom sheets owned by S01 / S02, not routes.
@Serializable data object GridRoute                          // S01
@Serializable data class HabitDetailRoute(val id: String)    // S02
@Serializable data class HabitFormRoute(val id: String = "") // S03 ("" = new)
@Serializable data object OrganizeRoute                      // S04
@Serializable data object SettingsRoute                      // S06
@Serializable data class FolderRoute(val id: String = "")    // S07 ("" = new)
@Serializable data object CatalogRoute                       // design system check

@Composable
fun App() {
    val appSettings = koinInject<AppSettings>()
    val themeMode by appSettings.theme.collectAsState()
    val language by appSettings.language.collectAsState()
    val onboarded by appSettings.onboarded.collectAsState()
    // Following the phone's language: pick up a change made while the app was in the background.
    LifecycleResumeEffect(Unit) {
        appSettings.applyLanguage()
        onPauseOrDispose { }
    }
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    AppTheme(dark = dark) {
      if (!onboarded) {
        Box(Modifier.fillMaxSize().background(AppTheme.colors.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            OnboardingScreen()
        }
      } else key(language) { // new language = fresh screens and view models, so every label is re-read
        val nav = rememberNavController()
        val linkedHabit by DeepLinks.habitId.collectAsState()
        LaunchedEffect(linkedHabit) {
            linkedHabit?.let { id ->
                nav.navigate(HabitDetailRoute(id)) { launchSingleTop = true }
                DeepLinks.consumed()
            }
        }
        Box(Modifier.fillMaxSize().background(AppTheme.colors.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            NavHost(nav, startDestination = GridRoute) {
                composable<GridRoute> {
                    GridScreen(
                        onNewHabit = { nav.navigate(HabitFormRoute()) },
                        onOpenHabit = { nav.navigate(HabitDetailRoute(it)) },
                        onEditFolder = { nav.navigate(FolderRoute(it)) },
                        onOrganize = { nav.navigate(OrganizeRoute) },
                        onSettings = { nav.navigate(SettingsRoute) },
                    )
                }
                composable<HabitDetailRoute> { entry ->
                    val id = entry.toRoute<HabitDetailRoute>().id
                    HabitDetailScreen(id, onClose = { nav.popBackStack() }, onEdit = { nav.navigate(HabitFormRoute(id)) })
                }
                composable<HabitFormRoute> { entry ->
                    HabitFormScreen(entry.toRoute<HabitFormRoute>().id, onClose = { nav.popBackStack() })
                }
                composable<FolderRoute> { entry ->
                    FolderScreen(entry.toRoute<FolderRoute>().id, onClose = { nav.popBackStack() })
                }
                composable<OrganizeRoute> {
                    OrganizeScreen(
                        onClose = { nav.popBackStack() },
                        onNewFolder = { nav.navigate(FolderRoute()) },
                        onEditFolder = { nav.navigate(FolderRoute(it)) },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(onClose = { nav.popBackStack() }, onCatalog = { nav.navigate(CatalogRoute) })
                }
                composable<CatalogRoute> { DesignCatalogScreen(onClose = { nav.popBackStack() }) }
            }
        }
      }
    }
}
