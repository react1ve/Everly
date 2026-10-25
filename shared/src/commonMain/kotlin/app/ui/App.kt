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
import app.ui.grid.GridScreen
import app.ui.habit.HabitFormScreen
import app.ui.stub.StubScreen
import kotlinx.serialization.Serializable

// Routes: one per screen ID in recon.md. Sheets are full screens in M1; real bottom sheets in M2.
@Serializable data object GridRoute                                         // S01
@Serializable data class HabitDetailRoute(val id: String)                   // S02
@Serializable data class HabitFormRoute(val id: String = "")                // S03 ("" = new)
@Serializable data object OrganizeRoute                                     // S04
@Serializable data class DayNoteRoute(val habitId: String, val date: String) // S05
@Serializable data object SettingsRoute                                     // S06
@Serializable data class FolderRoute(val id: String = "")                   // S07
@Serializable data class EntryRoute(val habitId: String, val date: String = "") // S08
@Serializable data object CatalogRoute                                      // design system check

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
                        onDayNote = { id, date -> nav.navigate(DayNoteRoute(id, date.toString())) },
                        onOrganize = { nav.navigate(OrganizeRoute) },
                        onSettings = { nav.navigate(SettingsRoute) },
                    )
                }
                composable<HabitFormRoute> { entry ->
                    HabitFormScreen(entry.toRoute<HabitFormRoute>().id, onClose = { nav.popBackStack() })
                }
                composable<HabitDetailRoute> { entry ->
                    val id = entry.toRoute<HabitDetailRoute>().id
                    StubScreen("S02", "Habit", "M2", onClose = { nav.popBackStack() },
                        actions = listOf("Edit" to { nav.navigate(HabitFormRoute(id)) },
                            "Add entry" to { nav.navigate(EntryRoute(id)) }))
                }
                composable<OrganizeRoute> {
                    StubScreen("S04", "Organise", "M3", onClose = { nav.popBackStack() },
                        actions = listOf("New folder" to { nav.navigate(FolderRoute()) }))
                }
                composable<DayNoteRoute> { StubScreen("S05", "Note", "M2", onClose = { nav.popBackStack() }) }
                composable<SettingsRoute> {
                    StubScreen("S06", "Settings", "M3", onClose = { nav.popBackStack() },
                        actions = listOf("Design catalog" to { nav.navigate(CatalogRoute) }))
                }
                composable<FolderRoute> { StubScreen("S07", "Folder", "M2", onClose = { nav.popBackStack() }) }
                composable<EntryRoute> { StubScreen("S08", "Entry", "M2", onClose = { nav.popBackStack() }) }
                composable<CatalogRoute> { DesignCatalogScreen(onClose = { nav.popBackStack() }) }
            }
        }
    }
}
