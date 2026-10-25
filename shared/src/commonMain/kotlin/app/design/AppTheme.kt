// Design system entry point (commonMain, :shared:ui). Values mirror tokens.json; colours come from AppColors.kt (generated).
package app.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class AppType(
    val caption: TextStyle = style(11, 13, FontWeight.Medium),
    val footnote: TextStyle = style(13, 18),
    val subhead: TextStyle = style(15, 20),
    val body: TextStyle = style(17, 22),
    val bodyEmphasis: TextStyle = style(17, 22, FontWeight.Medium),
    val title: TextStyle = style(17, 22, FontWeight.SemiBold),
    val date: TextStyle = style(20, 24, FontWeight.SemiBold),
    val headline: TextStyle = style(22, 28, FontWeight.Bold),
    val display: TextStyle = style(34, 41, FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = FontFamily.Default, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

@Immutable
object Space { val x0 = 0.dp; val x1 = 4.dp; val x2 = 8.dp; val x3 = 12.dp; val x4 = 16.dp; val x5 = 20.dp; val x6 = 24.dp; val x8 = 32.dp; val x12 = 48.dp }

@Immutable
object Radius {
    val cell = RoundedCornerShape(10.dp); val sm = RoundedCornerShape(8.dp); val md = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(20.dp); val sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val pill = RoundedCornerShape(percent = 50)
    val heat = RoundedCornerShape(3.dp)
}

@Immutable
object Sizes {
    val gridCell: Dp = 40.dp; val gridColumn: Dp = 48.dp; val gridRow: Dp = 52.dp; val habitColumn: Dp = 172.dp
    val touchMin: Dp = 44.dp; val bottomBar: Dp = 56.dp; val swatch: Dp = 32.dp; val dot: Dp = 10.dp
    val outline: Dp = 2.dp; val edge: Dp = 1.dp
    val heatCell: Dp = 14.dp; val heatGap: Dp = 2.dp
}

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
val LocalAppType = staticCompositionLocalOf { AppType() }

object AppTheme {
    val colors: AppColors @Composable get() = LocalAppColors.current
    val type: AppType @Composable get() = LocalAppType.current
}

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkAppColors else LightAppColors
    // Material3 is mapped so stock components (TextField, Switch, ModalBottomSheet, DatePicker) inherit the roles.
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.text,
        surface = c.surface, onSurface = c.text, surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.textMuted,
        outline = c.borderInput, outlineVariant = c.border, error = c.danger, scrim = c.scrim,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.text,
        surface = c.surface, onSurface = c.text, surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.textMuted,
        outline = c.borderInput, outlineVariant = c.border, error = c.danger, scrim = c.scrim,
    )
    CompositionLocalProvider(LocalAppColors provides c, LocalAppType provides AppType()) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
