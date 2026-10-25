package app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.core.model.Interval
import app.core.model.ReminderSettings
import app.core.schedule.Cell
import app.core.schedule.CellState
import app.data.HabitDraft
import app.data.HabitRepository
import app.design.AppTheme
import app.design.HabitColorKey
import app.design.Radius
import app.design.Sizes
import app.design.Space
import app.design.components.ButtonKind
import app.design.components.DayCell
import app.design.components.PillButton
import app.platform.Analytics
import app.platform.AppClock
import app.platform.AppSettings
import app.platform.Events
import app.platform.ReminderScheduler
import app.ui.grid.GridViewModel
import app.ui.preview.PreviewSurface
import app.ui.text.L
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.koin.compose.viewmodel.koinViewModel

class OnboardingViewModel(
    private val repo: HabitRepository,
    private val clock: AppClock,
    private val settings: AppSettings,
    private val reminders: ReminderScheduler,
    private val analytics: Analytics,
) : ViewModel() {

    /** Adds the chosen starters (with reminders if asked) and never shows onboarding again. */
    fun finish(templates: Set<GridViewModel.Template>, remind: Boolean) {
        viewModelScope.launch {
            val allowed = remind && reminders.requestPermission()
            templates.forEach { t ->
                repo.createHabit(
                    HabitDraft(
                        name = L.templates[t.ordinal].second,
                        interval = Interval(t.count, t.unit),
                        colorKey = t.colorKey,
                        reminder = ReminderSettings(enabled = allowed),
                        anchorDate = clock.today(),
                    ),
                )
            }
            analytics.track(Events.ONBOARDING_DONE, mapOf("starters" to templates.size.toString(), "reminders" to allowed.toString()))
            settings.setOnboarded()
        }
    }

    fun skip() = settings.setOnboarded()
}

/** First run: what it's for, the rolling rule, pick starters. Copy and visuals are our own. */
@Composable
fun OnboardingScreen(vm: OnboardingViewModel = koinViewModel()) {
    OnboardingContent(onSkip = vm::skip, onFinish = vm::finish)
}

@Composable
internal fun OnboardingContent(
    onSkip: () -> Unit,
    onFinish: (Set<GridViewModel.Template>, Boolean) -> Unit,
    initialStep: Int = 0,
) {
    val c = AppTheme.colors
    var step by remember { mutableIntStateOf(initialStep) }
    var picked by remember { mutableStateOf(setOf(GridViewModel.Template.VITAMIN)) }
    var remind by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().background(c.bg).padding(Space.x6)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Dots(step)
            Spacer(Modifier.weight(1f))
            if (step < 2) PillButton(L.skip, onSkip, kind = ButtonKind.GHOST)
        }
        AnimatedContent(targetState = step, modifier = Modifier.weight(1f)) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.x8),
                verticalArrangement = Arrangement.spacedBy(Space.x4),
            ) {
                when (page) {
                    0 -> {
                        Title(L.onb1Title); Body(L.onb1Body)
                        Spacer(Modifier.height(Space.x4)); SampleRow(listOf(CellState.DONE, CellState.COVERED, CellState.COVERED, CellState.DUE, CellState.IDLE))
                    }
                    1 -> {
                        Title(L.onb2Title); Body(L.onb2Body)
                        Spacer(Modifier.height(Space.x4)); SampleRow(listOf(CellState.OVERDUE, CellState.OVERDUE, CellState.DONE, CellState.COVERED, CellState.COVERED))
                    }
                    else -> {
                        Title(L.onb3Title); Body(L.onb3Body)
                        GridViewModel.Template.entries.forEach { t ->
                            val on = t in picked
                            Row(
                                Modifier.fillMaxWidth().clip(Radius.card).background(c.surface)
                                    .toggleable(on, role = Role.Checkbox) { picked = if (it) picked + t else picked - t }
                                    .heightIn(min = Sizes.touchMin).padding(horizontal = Space.x3),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = c.accent))
                                Text(L.templates[t.ordinal].first, style = AppTheme.type.body, color = c.text)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(Radius.card).background(c.surface)
                                .toggleable(remind, role = Role.Switch) { remind = it }
                                .heightIn(min = Sizes.touchMin).padding(horizontal = Space.x4, vertical = Space.x2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(L.onbReminders, style = AppTheme.type.body, color = c.text)
                                Text(L.onbRemindersHint, style = AppTheme.type.footnote, color = c.textMuted)
                            }
                            Switch(remind, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = c.success, checkedThumbColor = c.surface))
                        }
                    }
                }
            }
        }
        PillButton(
            if (step < 2) L.next else L.getStarted,
            { if (step < 2) step++ else onFinish(picked, remind) },
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Title(text: String) = Text(
    text, style = AppTheme.type.display, color = AppTheme.colors.text, modifier = Modifier.semantics { heading() },
)

@Composable
private fun Body(text: String) = Text(text, style = AppTheme.type.body, color = AppTheme.colors.textMuted)

@Composable
private fun Dots(step: Int) {
    val c = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
        repeat(3) { i ->
            Box(
                Modifier.size(Sizes.dot).clip(CircleShape)
                    .background(if (i == step) c.accent else c.border)
                    .border(Sizes.edge, if (i == step) c.accent else c.border, CircleShape),
            )
        }
    }
}

/** A few real DayCells to show the idea, not a picture of the original. */
@Composable
private fun SampleRow(states: List<CellState>) {
    val c = AppTheme.colors
    val start = kotlinx.datetime.LocalDate(2026, 1, 5)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
        states.forEachIndexed { i, st ->
            DayCell(
                cell = Cell(start.plus(i, DateTimeUnit.DAY), st, if (st == CellState.DONE) 1 else 0, 1, false, i == 2, false, false),
                colors = c.habit(HabitColorKey.TEAL),
                habitName = "",
                onTap = {}, onLongPress = {},
            )
        }
    }
}

@Preview
@Composable
private fun OnboardingPreview() = PreviewSurface { OnboardingContent({}, { _, _ -> }) }

@Preview
@Composable
private fun OnboardingRulePreview() = PreviewSurface { OnboardingContent({}, { _, _ -> }, initialStep = 1) }

@Preview
@Composable
private fun OnboardingStartersPreview() = PreviewSurface { OnboardingContent({}, { _, _ -> }, initialStep = 2) }

@Preview
@Composable
private fun OnboardingDarkPreview() = PreviewSurface(dark = true) { OnboardingContent({}, { _, _ -> }) }

@Preview
@Composable
private fun OnboardingStartersDarkPreview() = PreviewSurface(dark = true) { OnboardingContent({}, { _, _ -> }, initialStep = 2) }
