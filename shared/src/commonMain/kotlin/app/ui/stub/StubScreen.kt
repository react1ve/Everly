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
import app.design.AppTheme
import app.design.Space
import app.design.components.ButtonKind
import app.design.components.PillButton
import app.design.components.SheetHeader

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
