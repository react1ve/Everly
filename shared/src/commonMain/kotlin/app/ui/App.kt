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
import app.ui.stub.StubScreen
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
    AppTheme {
        val nav = rememberNavController()
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
                    StubScreen("S04", "Organise", "M3", onClose = { nav.popBackStack() },
                        actions = listOf("New folder" to { nav.navigate(FolderRoute()) }))
                }
                composable<SettingsRoute> {
                    StubScreen("S06", "Settings", "M3", onClose = { nav.popBackStack() },
                        actions = listOf("Design catalog" to { nav.navigate(CatalogRoute) }))
                }
                composable<CatalogRoute> { DesignCatalogScreen(onClose = { nav.popBackStack() }) }
            }
        }
    }
}
