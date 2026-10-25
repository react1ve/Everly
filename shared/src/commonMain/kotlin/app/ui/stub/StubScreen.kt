package app.ui.stub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.design.AppTheme
import app.design.Space
import app.design.components.ButtonKind
import app.design.components.PillButton
import app.design.components.SheetHeader
import app.ui.preview.PreviewSurface

/** Placeholder for screens that are routed but not built yet (see build-log.md). */
@Composable
fun StubScreen(
    id: String,
    title: String,
    plannedFor: String,
    onClose: () -> Unit,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        SheetHeader(title = title, onClose = onClose)
        Column(Modifier.fillMaxWidth().padding(Space.x6), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            Text("$id · coming in $plannedFor", style = AppTheme.type.subhead, color = c.textMuted)
            actions.forEach { (label, action) ->
                PillButton(label, action, Modifier.fillMaxWidth(), kind = ButtonKind.SECONDARY)
            }
        }
    }
}

@Preview
@Composable
private fun StubScreenPreview() = PreviewSurface {
    StubScreen("S09", "Statistics", "v1.1", onClose = {}, actions = listOf("Open settings" to {}, "Back to grid" to {}))
}

@Preview
@Composable
private fun StubScreenDarkPreview() = PreviewSurface(dark = true) { StubScreen("S09", "Statistics", "v1.1", onClose = {}) }
