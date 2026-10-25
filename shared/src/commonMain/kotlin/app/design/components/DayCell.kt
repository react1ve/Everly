package app.design.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import app.core.schedule.Cell
import app.core.schedule.CellState
import app.design.AppTheme
import app.design.HabitColorKey
import app.design.HabitColors
import app.design.Radius
import app.design.Sizes
import app.design.Space
import app.ui.preview.PreviewData
import app.ui.preview.PreviewSurface
import app.ui.text.Formats

fun habitColorKey(key: String): HabitColorKey =
    HabitColorKey.entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: HabitColorKey.BLUE

@Composable
fun habitColors(key: String): HabitColors = AppTheme.colors.habit(habitColorKey(key))

/**
 * One day of one habit. Spec: replica/design/components.md "DayCell".
 * State is carried by shape and glyph as well as colour (check, n/m, border style).
 */
@Composable
fun DayCell(
    cell: Cell,
    colors: HabitColors,
    habitName: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val scale by animateFloatAsState(
        targetValue = if (cell.state == CellState.DONE) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
    )
    val bg = when (cell.state) {
        CellState.DONE -> colors.fill
        CellState.PARTIAL, CellState.COVERED -> colors.covered
        CellState.DUE, CellState.OVERDUE, CellState.IDLE -> colors.idle
    }
    val borderColor = when (cell.state) {
        CellState.DUE -> colors.outline
        CellState.OVERDUE -> c.danger
        CellState.DONE -> colors.outline // 1dp edge keeps pale fills (yellow) visible on white
        else -> if (cell.isToday) colors.outline else null // today's column reads as one outlined strip
    }
    val borderWidth = if (cell.state == CellState.DUE || cell.state == CellState.OVERDUE) Sizes.outline else Sizes.edge
    val description = Formats.cellDescription(habitName, cell)

    Box(
        modifier = modifier
            .size(Sizes.gridCell)
            .scale(if (cell.state == CellState.DONE) scale else 1f)
            .background(bg, Radius.cell)
            .let { if (borderColor != null) it.border(borderWidth, borderColor, Radius.cell) else it }
            .combinedClickable(
                enabled = true,
                role = if (cell.dosesPerDay == 1) Role.Checkbox else Role.Button,
                onClick = onTap,
                onLongClick = onLongPress,
            )
            .semantics {
                contentDescription = description
                stateDescription = Formats.cellStateWords(cell)
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            cell.state == CellState.DONE -> Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onFill)
            cell.state == CellState.PARTIAL -> Text(
                "${cell.doseCount}/${cell.dosesPerDay}",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
            )
            cell.state == CellState.OVERDUE && cell.isToday -> Text("!", style = AppTheme.type.title, color = c.danger)
            cell.isReminderDay -> Icon(
                Icons.Rounded.Notifications, contentDescription = null, tint = c.textMuted,
                modifier = Modifier.size(Sizes.dot * 1.6f),
            )
        }
        if (cell.hasNote) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(Space.x1)
                    .size(Sizes.dot * 0.7f)
                    .background(if (cell.state == CellState.DONE) colors.onFill else colors.ink, CircleShape),
            )
        }
    }
}

@Composable
private fun DayCellStates(colorKey: String) {
    val d = PreviewData
    val cells = listOf(
        d.cell(CellState.DONE, 1), d.cell(CellState.DONE, 1, note = true), d.cell(CellState.PARTIAL, 1, 2),
        d.cell(CellState.PARTIAL, 2, 3, note = true), d.cell(CellState.COVERED), d.cell(CellState.DUE),
        d.cell(CellState.OVERDUE), d.cell(CellState.OVERDUE, today = true), d.cell(CellState.IDLE),
        d.cell(CellState.IDLE, bell = true), d.cell(CellState.IDLE, note = true),
    )
    Row(Modifier.padding(Space.x2), horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
        cells.forEach { DayCell(it, habitColors(colorKey), "Sample", onTap = {}, onLongPress = {}) }
    }
}

@Preview(widthDp = 560)
@Composable
private fun DayCellPreview() = PreviewSurface {
    Column { listOf("yellow", "mint", "indigo", "brown", "red").forEach { DayCellStates(it) } }
}

@Preview(widthDp = 560)
@Composable
private fun DayCellDarkPreview() = PreviewSurface(dark = true) {
    Column { listOf("yellow", "mint", "indigo", "brown", "red").forEach { DayCellStates(it) } }
}
