package app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import app.core.schedule.Cell
import app.core.schedule.CellState
import app.design.AppTheme
import app.design.HabitColors
import app.design.Space
import app.design.Sizes
import app.design.Radius
import app.design.components.habitColors
import app.ui.preview.PreviewData
import app.ui.preview.PreviewSurface
import app.ui.text.Formats
import kotlinx.datetime.DayOfWeek

private val CELL = Sizes.heatCell
private val GAP = Sizes.heatGap
private val CELL_SHAPE = Radius.heat

/**
 * GitHub-style year: one column per week (Mon..Sun), month labels on top.
 * Screen readers get one summary instead of 365 nodes.
 */
@Composable
fun YearHeatmap(
    year: Int,
    cells: List<Cell>,
    doneCount: Int,
    colors: HabitColors,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    // Pad the first week so columns start on Monday.
    val lead = cells.firstOrNull()?.date?.dayOfWeek?.let { it.ordinal - DayOfWeek.MONDAY.ordinal } ?: 0
    val weeks: List<List<Cell?>> = remember(cells) {
        (List<Cell?>(lead) { null } + cells).chunked(7)
    }
    val todayWeek = weeks.indexOfFirst { w -> w.any { it?.isToday == true } }
    val listState = rememberLazyListState()
    LaunchedEffect(year, todayWeek) {
        listState.scrollToItem(if (todayWeek >= 0) (todayWeek - 8).coerceAtLeast(0) else 0)
    }

    LazyRow(
        state = listState,
        modifier = modifier.clearAndSetSemantics { contentDescription = "$year: $doneCount days done" },
        horizontalArrangement = Arrangement.spacedBy(GAP),
        contentPadding = PaddingValues(horizontal = Space.x3),
    ) {
        itemsIndexed(weeks) { index, week ->
            Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                val first = week.firstOrNull { it != null }
                val showMonth = first != null && (index == 0 || first.date.day <= 7 && week.any { it?.date?.day == 1 })
                Box(Modifier.height(Space.x4)) {
                    if (showMonth && first != null) {
                        val monthStart = week.firstOrNull { it?.date?.day == 1 }?.date ?: first.date
                        Text(Formats.monthLong(monthStart).take(3), style = AppTheme.type.caption, color = c.textMuted)
                    }
                }
                week.forEach { cell -> HeatCell(cell, colors) }
                repeat(7 - week.size) { HeatCell(null, colors) }
            }
        }
    }
}

@Composable
private fun HeatCell(cell: Cell?, colors: HabitColors) {
    val c = AppTheme.colors
    if (cell == null) { Box(Modifier.size(CELL)); return }
    val bg = when (cell.state) {
        CellState.DONE -> colors.fill
        CellState.PARTIAL, CellState.COVERED -> colors.covered
        CellState.OVERDUE -> c.danger.copy(alpha = 0.18f)
        else -> c.bg
    }
    Box(
        Modifier.size(CELL).background(bg, CELL_SHAPE)
            .let { if (cell.isToday) it.border(Sizes.edge * 1.5f, c.accent, CELL_SHAPE) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (cell.isReminderDay) Box(Modifier.size(Space.x1).background(c.textMuted, CircleShape))
    }
}

@Composable
private fun YearHeatmapSamples() {
    val d = PreviewData
    Column(Modifier.padding(vertical = Space.x3), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
        // Daily with a two-dose day, every few days with notes, a yearly habit with a reminder day ahead.
        listOf("h-omega", "h-swim", "h-eyes").forEach { id ->
            val cells = d.yearCells(id)
            YearHeatmap(d.today.year, cells, cells.count { it.state == CellState.DONE }, habitColors(d.habit(id).colorKey))
        }
        YearHeatmap(d.today.year - 1, d.yearCells("h-stretch", d.today.year - 1), 0, habitColors("indigo")) // empty past year
    }
}

@Preview(widthDp = 420)
@Composable
private fun YearHeatmapPreview() = PreviewSurface { YearHeatmapSamples() }

@Preview(widthDp = 420)
@Composable
private fun YearHeatmapDarkPreview() = PreviewSurface(dark = true) { YearHeatmapSamples() }
