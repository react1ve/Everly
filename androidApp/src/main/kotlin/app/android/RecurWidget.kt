package app.android

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.LocalSize
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColor
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.core.schedule.CellState
import app.data.HabitRepository
import app.design.DarkAppColors
import app.design.HabitColorKey
import app.design.LightAppColors
import app.platform.AppClock
import app.platform.WidgetItem
import app.platform.WidgetSnapshot
import app.platform.Analytics
import app.platform.Events
import app.ui.text.L
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext

/** "Today" widget: what's due, with a tap target to check it off. Spec: components.md "Widgets". */
class RecurWidget : GlanceAppWidget() {
    // Small (2x2), medium (3x2 / 4x2) and tall (4x3+): more rows as space allows.
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val koin = GlobalContext.get()
        val repo = koin.get<HabitRepository>()
        val today = koin.get<AppClock>().today()
        val habits = repo.habits.first()
        val items = WidgetSnapshot.build(habits, repo.entries.first(), today, limit = 8)
        provideContent {
            val size = LocalSize.current
            val rows = when {
                size.height >= TALL.height -> 8
                size.height >= MEDIUM.height -> 4
                else -> 3
            }
            WidgetContent(items.take(rows), openCount = items.count { it.state != CellState.DONE }, hasHabits = habits.isNotEmpty())
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val MEDIUM = DpSize(180.dp, 150.dp)
        private val TALL = DpSize(180.dp, 260.dp)

        suspend fun refresh(context: Context) = RecurWidget().updateAll(context)
    }
}

class RecurWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecurWidget()
}

private val HabitIdKey = ActionParameters.Key<String>("habitId")

class ToggleTodayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitIdKey] ?: return
        val koin = GlobalContext.get()
        koin.get<HabitRepository>().toggleDay(id, koin.get<AppClock>().today())
        koin.get<Analytics>().track(Events.WIDGET_TOGGLE)
        RecurWidget().update(context, glanceId)
    }
}

private fun dayNight(light: androidx.compose.ui.graphics.Color, dark: androidx.compose.ui.graphics.Color): GlanceColor =
    ColorProvider(day = light, night = dark)

private fun keyOf(colorKey: String) =
    HabitColorKey.entries.firstOrNull { it.name.equals(colorKey, ignoreCase = true) } ?: HabitColorKey.BLUE

@Composable
private fun WidgetContent(items: List<WidgetItem>, openCount: Int, hasHabits: Boolean) {
    val bg = dayNight(LightAppColors.bg, DarkAppColors.bg)
    val text = dayNight(LightAppColors.text, DarkAppColors.text)
    val muted = dayNight(LightAppColors.textMuted, DarkAppColors.textMuted)
    val allDone = items.isNotEmpty() && items.all { it.state == CellState.DONE }

    Column(
        GlanceModifier.fillMaxSize().background(bg).cornerRadius(20.dp).padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(L.today, style = TextStyle(color = text, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            if (openCount > 0) {
                Spacer(GlanceModifier.width(6.dp))
                Text(L.toDo(openCount), style = TextStyle(color = muted, fontSize = 12.sp))
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        when {
            !hasHabits -> Text(L.widgetAddHabit, style = TextStyle(color = muted, fontSize = 13.sp))
            items.isEmpty() -> Text(L.nothingDueToday, style = TextStyle(color = muted, fontSize = 13.sp))
            else -> {
                if (allDone) {
                    Text(L.allCaughtUp, style = TextStyle(color = muted, fontSize = 13.sp))
                    Spacer(GlanceModifier.height(4.dp))
                }
                items.forEach { item -> WidgetRow(item, text) }
            }
        }
    }
}

@Composable
private fun WidgetRow(item: WidgetItem, text: GlanceColor) {
    val light = LightAppColors.habit(keyOf(item.colorKey))
    val dark = DarkAppColors.habit(keyOf(item.colorKey))
    val done = item.state == CellState.DONE
    val cellBg = if (done) dayNight(light.fill, dark.fill) else dayNight(light.covered, dark.covered)
    val glyph = when {
        done -> "✓"
        item.state == CellState.PARTIAL -> "${item.doseCount}/${item.dosesPerDay}"
        item.state == CellState.OVERDUE -> "!"
        else -> ""
    }
    val glyphColor = if (done) dayNight(light.onFill, dark.onFill) else dayNight(light.ink, dark.ink)

    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier.size(32.dp).cornerRadius(8.dp).background(cellBg)
                .clickable(actionRunCallback<ToggleTodayAction>(actionParametersOf(HabitIdKey to item.habitId))),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, style = TextStyle(color = glyphColor, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(item.name, maxLines = 1, style = TextStyle(color = text, fontSize = 14.sp))
    }
}
