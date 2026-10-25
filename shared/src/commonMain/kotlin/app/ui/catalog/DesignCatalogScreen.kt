package app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.core.model.IntervalUnit
import app.core.schedule.Cell
import app.core.schedule.CellState
import app.design.AppTheme
import app.design.HabitColorKey
import app.design.Space
import app.design.components.ButtonKind
import app.design.components.ColorSwatchPicker
import app.design.components.DayCell
import app.design.components.GroupedSection
import app.design.components.NumberStepper
import app.design.components.PillButton
import app.design.components.SegmentedControl
import app.design.components.SheetHeader
import app.ui.preview.PreviewSurface
import kotlinx.datetime.LocalDate

/**
 * Design-system check (replica-design step 4): every primitive in isolation, in every state.
 * Reached from Settings. Screenshot it in light and dark mode.
 */
@Composable
fun DesignCatalogScreen(onClose: () -> Unit) {
    val c = AppTheme.colors
    val day = LocalDate(2026, 10, 17)
    fun cell(state: CellState, count: Int = 0, doses: Int = 1, note: Boolean = false, bell: Boolean = false, today: Boolean = false) =
        Cell(day, state, count, doses, note, today, bell, true)
    val samples = listOf(
        "done" to cell(CellState.DONE, 1),
        "done+note" to cell(CellState.DONE, 1, note = true),
        "1/2" to cell(CellState.PARTIAL, 1, 2),
        "covered" to cell(CellState.COVERED),
        "due" to cell(CellState.DUE),
        "overdue" to cell(CellState.OVERDUE, today = true),
        "idle+bell" to cell(CellState.IDLE, bell = true),
    )
    var color by remember { mutableStateOf(HabitColorKey.YELLOW) }
    var count by remember { mutableIntStateOf(3) }
    var unit by remember { mutableStateOf(IntervalUnit.WEEK) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        SheetHeader("Design catalog", onClose)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.x4),
            verticalArrangement = Arrangement.spacedBy(Space.x6),
        ) {
            listOf(HabitColorKey.YELLOW, HabitColorKey.MINT, HabitColorKey.INDIGO, HabitColorKey.BROWN).forEach { key ->
                GroupedSection(caption = "DayCell · ${key.name.lowercase()}") {
                    Row(Modifier.fillMaxWidth().padding(Space.x3), horizontalArrangement = Arrangement.SpaceBetween) {
                        samples.forEach { (label, cell) ->
                            Column {
                                DayCell(cell, c.habit(key), "Sample", onTap = {}, onLongPress = {})
                                Text(label, style = AppTheme.type.caption, color = c.textMuted)
                            }
                        }
                    }
                }
            }
            GroupedSection(caption = "Stepper + segmented") {
                NumberStepper(count, "units", { count = it }, 1..999)
                SegmentedControl(IntervalUnit.entries, unit, { it.name }, { unit = it })
            }
            GroupedSection(caption = "Colour swatches") { ColorSwatchPicker(color, { color = it }) }
            Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                ButtonKind.entries.forEach { k -> PillButton(k.name.lowercase(), {}, Modifier.fillMaxWidth(), kind = k) }
                PillButton("disabled", {}, Modifier.fillMaxWidth(), enabled = false)
            }
        }
    }
}

@Preview(heightDp = 1400)
@Composable
private fun DesignCatalogPreview() = PreviewSurface { DesignCatalogScreen(onClose = {}) }

@Preview(heightDp = 1400)
@Composable
private fun DesignCatalogDarkPreview() = PreviewSurface(dark = true) { DesignCatalogScreen(onClose = {}) }
