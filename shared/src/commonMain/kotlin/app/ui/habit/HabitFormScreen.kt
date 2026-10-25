package app.ui.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.core.model.Habit
import app.core.model.Interval
import app.core.model.IntervalUnit
import app.design.AppTheme
import app.design.Radius
import app.design.Space
import app.design.components.ButtonKind
import app.design.components.ColorSwatchPicker
import app.design.components.GroupedSection
import app.design.components.NumberStepper
import app.design.components.PillButton
import app.design.components.SectionDivider
import app.design.components.SegmentedControl
import app.design.components.SheetHeader
import app.ui.text.Formats
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun HabitFormScreen(
    habitId: String,
    onClose: () -> Unit,
    vm: HabitFormViewModel = koinViewModel(key = "habit-form-$habitId") { parametersOf(habitId) },
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.closed) { if (s.closed) onClose() }
    val c = AppTheme.colors

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        SheetHeader(
            title = if (s.isNew) "New habit" else "Edit habit",
            onClose = onClose,
            onPrimary = vm::save,
            primaryEnabled = s.canSave && s.loaded,
        )
        if (!s.loaded) return@Column

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.x4),
            verticalArrangement = Arrangement.spacedBy(Space.x6),
        ) {
            Spacer(Modifier.height(Space.x1))
            GroupedSection {
                TextField(
                    value = s.name,
                    onValueChange = vm::setName,
                    placeholder = { Text("What comes round? e.g. 🦷 Dentist", color = c.textMuted) },
                    singleLine = true,
                    isError = s.showNameError,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.save() }),
                    textStyle = AppTheme.type.body.copy(color = c.text),
                    shape = Radius.card,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, errorContainerColor = c.surface,
                        focusedIndicatorColor = c.surface, unfocusedIndicatorColor = c.surface, errorIndicatorColor = c.danger,
                        cursorColor = c.accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (s.showNameError) {
                    Text("Give it a name", style = AppTheme.type.footnote, color = c.danger,
                        modifier = Modifier.padding(horizontal = Space.x4, vertical = Space.x2))
                }
            }

            GroupedSection(caption = "Repeats every") {
                NumberStepper(s.count, Formats.unitLabel(s.unit, s.count), vm::setCount, 1..Interval.MAX_COUNT)
                SegmentedControl(
                    options = IntervalUnit.entries,
                    selected = s.unit,
                    label = { u -> Formats.unitLabel(u, 1).replaceFirstChar { it.uppercase() } },
                    onSelect = vm::setUnit,
                )
                SectionDivider()
                NumberStepper(s.doses, if (s.doses == 1) "time that day" else "times that day", vm::setDoses, 1..Habit.MAX_DOSES)
            }

            GroupedSection(caption = "Colour") {
                ColorSwatchPicker(s.color, vm::setColor)
            }

            if (!s.isNew) {
                PillButton("Delete habit", { vm.askDelete(true) }, Modifier.fillMaxWidth(), kind = ButtonKind.SECONDARY)
            }
            Spacer(Modifier.height(Space.x8))
        }
    }

    if (s.confirmDelete) {
        AlertDialog(
            onDismissRequest = { vm.askDelete(false) },
            title = { Text("Delete this habit?") },
            text = { Text("Its whole history goes with it. This can't be undone.") },
            confirmButton = { TextButton(onClick = vm::delete) { Text("Delete", color = c.danger) } },
            dismissButton = { TextButton(onClick = { vm.askDelete(false) }) { Text("Keep it") } },
        )
    }
}
