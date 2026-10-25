package app.design.components

import androidx.compose.foundation.background
import app.ui.text.L
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.design.AppTheme
import app.design.HabitColorKey
import app.design.Radius
import app.design.Sizes
import app.design.Space

/** ✕ · title · primary action. Spec: components.md "SheetHeader". */
@Composable
fun SheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    onPrimary: (() -> Unit)? = null,
    primaryLabel: String? = null, // text pill (e.g. "Edit") instead of the check circle
) {
    val c = AppTheme.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(Icons.Rounded.Close, L.close, onClose)
        Text(title, style = AppTheme.type.title, color = c.text, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.weight(1f).padding(horizontal = Space.x2))
        when {
            onPrimary == null -> Spacer(Modifier.size(Sizes.touchMin))
            primaryLabel != null -> PillButton(primaryLabel, onPrimary, enabled = primaryEnabled)
            else -> Box(
                Modifier.size(Sizes.touchMin).clip(CircleShape)
                    .background(if (primaryEnabled) c.accent else c.accent.copy(alpha = 0.4f))
                    .clickable(enabled = primaryEnabled, role = Role.Button, onClick = onPrimary)
                    .semantics { contentDescription = L.save },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, null, tint = c.onAccent) }
        }
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Box(
        modifier.size(Sizes.touchMin).clip(CircleShape).background(c.surfaceRaised)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = c.text) }
}

enum class ButtonKind { PRIMARY, SECONDARY, GHOST, DANGER }

@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
) {
    val c = AppTheme.colors
    val (bg, fg) = when (kind) {
        ButtonKind.PRIMARY -> c.accent to c.onAccent
        ButtonKind.SECONDARY -> c.surfaceRaised to c.text
        ButtonKind.GHOST -> c.surface.copy(alpha = 0f) to c.accent
        ButtonKind.DANGER -> c.danger to c.surface
    }
    Box(
        modifier.heightIn(min = Sizes.touchMin).clip(Radius.pill)
            .background(if (enabled) bg else bg.copy(alpha = bg.alpha * 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.x5),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AppTheme.type.bodyEmphasis, color = if (enabled) fg else fg.copy(alpha = 0.6f))
    }
}

/** Inset card that holds form rows. Spec: components.md "GroupedSection". */
@Composable
fun GroupedSection(caption: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = AppTheme.colors
    Column(modifier.fillMaxWidth()) {
        if (caption != null) {
            Text(caption, style = AppTheme.type.subhead, color = c.textMuted,
                modifier = Modifier.padding(start = Space.x4, bottom = Space.x2))
        }
        Column(Modifier.fillMaxWidth().clip(Radius.card).background(c.surface).padding(vertical = Space.x1)) {
            content()
        }
    }
}

/** − / + with the value and its unit. */
@Composable
fun NumberStepper(
    value: Int,
    unit: String,
    onChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$value", style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
        Spacer(Modifier.width(Space.x2))
        Text(unit, style = AppTheme.type.body, color = c.textMuted, modifier = Modifier.weight(1f))
        Row(Modifier.clip(Radius.pill).background(c.surfaceRaised)) {
            StepButton("−", L.decrease, value > range.first) { onChange(value - 1) }
            Box(Modifier.width(Sizes.edge).height(Space.x6).align(Alignment.CenterVertically).background(c.border))
            StepButton("+", L.increase, value < range.last) { onChange(value + 1) }
        }
    }
}

@Composable
private fun StepButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier.size(width = Sizes.touchMin + Space.x2, height = Sizes.touchMin * 0.8f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = AppTheme.type.headline.copy(fontWeight = FontWeight.Normal),
            color = if (enabled) c.text else c.textMuted.copy(alpha = 0.5f))
    }
}

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x2)
            .clip(Radius.pill).background(c.bg).padding(Space.x1 / 2).selectableGroup(),
    ) {
        options.forEach { option ->
            val isSel = option == selected
            Box(
                Modifier.weight(1f).heightIn(min = Space.x8).clip(Radius.pill)
                    .background(if (isSel) c.surface else c.bg)
                    .selectable(selected = isSel, role = Role.Tab, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), style = AppTheme.type.footnote.copy(fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (isSel) c.text else c.textMuted)
            }
        }
    }
}

/** 12 swatches in two rows. Spec: components.md "ColorSwatchPicker". */
@Composable
fun ColorSwatchPicker(selected: HabitColorKey, onSelect: (HabitColorKey) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(modifier.fillMaxWidth().padding(Space.x4).selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
        HabitColorKey.entries.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { key ->
                    val hc = c.habit(key)
                    val isSel = key == selected
                    Box(
                        Modifier.size(Sizes.touchMin)
                            .selectable(isSel, role = Role.RadioButton, onClick = { onSelect(key) })
                            .semantics { contentDescription = key.name.lowercase().replaceFirstChar { it.uppercase() } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSel) Box(Modifier.size(Sizes.swatch + Space.x2).border(Space.x1 * 0.75f, c.text, CircleShape))
                        Box(Modifier.size(Sizes.swatch).clip(CircleShape).background(hc.fill).border(Sizes.edge, hc.outline, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
fun SectionDivider() {
    Box(Modifier.fillMaxWidth().padding(start = Space.x4).height(Sizes.edge).background(AppTheme.colors.border))
}

@Composable
fun FormLabelRow(label: String, value: String? = null, onClick: (() -> Unit)? = null) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchMin)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = AppTheme.type.body, color = c.textMuted, modifier = Modifier.widthIn(max = Sizes.habitColumn))
    }
}
