package app.patro.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class PatroColors(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val cardPressed: Color,
    val fg: Color,
    val dim: Color,
    val faint: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
)

val Red = Color(0xFFD71921)

private val DarkColors = PatroColors(
    dark = true,
    bg = Color(0xFF000000),
    card = Color(0xFF141414),
    cardPressed = Color(0xFF1F1F1F),
    fg = Color(0xFFF2F2F2),
    dim = Color(0xFF8A8A8A),
    faint = Color(0xFF262626),
    line = Color(0xFF2A2A2A),
    accent = Red,
    onAccent = Color.White,
)

private val LightColors = PatroColors(
    dark = false,
    bg = Color(0xFFF2F2F0),
    card = Color(0xFFFFFFFF),
    cardPressed = Color(0xFFE8E8E6),
    fg = Color(0xFF111111),
    dim = Color(0xFF6E6E6E),
    faint = Color(0xFFE0E0DE),
    line = Color(0xFFDADAD8),
    accent = Red,
    onAccent = Color.White,
)

val LocalColors = staticCompositionLocalOf { DarkColors }

object Type {
    val label = TextStyle(fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontFamily = FontFamily.SansSerif)
    val small = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontFamily = FontFamily.SansSerif)
    val title = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontFamily = FontFamily.SansSerif)
    val headline = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontFamily = FontFamily.SansSerif)
}

/** [themePref]: 0 = system, 1 = dark, 2 = light. */
@Composable
fun PatroTheme(themePref: Int, content: @Composable () -> Unit) {
    val dark = when (themePref) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.fg, onPrimary = c.bg, secondary = c.dim, background = c.bg, onBackground = c.fg,
            surface = c.card, onSurface = c.fg, surfaceVariant = c.faint, onSurfaceVariant = c.dim,
            surfaceContainerLow = c.card, surfaceContainer = c.card, surfaceContainerHigh = c.card,
            surfaceContainerHighest = c.faint, outline = c.line, error = Red,
        )
    } else {
        lightColorScheme(
            primary = c.fg, onPrimary = c.bg, secondary = c.dim, background = c.bg, onBackground = c.fg,
            surface = c.card, onSurface = c.fg, surfaceVariant = c.faint, onSurfaceVariant = c.dim,
            surfaceContainerLow = c.card, surfaceContainer = c.card, surfaceContainerHigh = c.card,
            surfaceContainerHighest = c.faint, outline = c.line, error = Red,
        )
    }
    CompositionLocalProvider(LocalColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
