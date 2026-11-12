package app.patro.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.patro.dotmatrix.DotMatrix
import kotlin.random.Random

val CardShape = RoundedCornerShape(24.dp)

/** True when the user turned animations off (Settings → Accessibility → Remove animations). */
@Composable
fun animationsOff(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Dot-matrix text. When [animate] is on, lit dots "fill in" in random order on first show,
 * like Nothing Weather's hero number.
 */
@Composable
fun DotText(
    text: String,
    dot: Dp,
    modifier: Modifier = Modifier,
    color: Color = LocalColors.current.fg,
    dimColor: Color = Color.Transparent,
    animate: Boolean = false,
    contentDescription: String = text,
) {
    val m = remember(text) { DotMatrix(text) }
    val off = animationsOff()
    val progress = remember(text) { Animatable(if (animate && !off) 0f else 1f) }
    LaunchedEffect(text) {
        if (progress.value < 1f) progress.animateTo(1f, tween(700, easing = LinearEasing))
    }
    // Each dot gets a fixed random start time in [0, 0.75]; it fades in over 0.25.
    val starts = remember(text) {
        val rnd = Random(text.hashCode())
        FloatArray(m.cols * m.rows) { rnd.nextFloat() * 0.75f }
    }
    val pitch = dot * DotMatrix.PITCH
    val width = if (m.cols == 0) 0.dp else pitch * (m.cols - 1) + dot
    val height = pitch * (m.rows - 1) + dot
    val density = LocalDensity.current
    Canvas(
        modifier
            .size(width, height)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val d = with(density) { dot.toPx() }
        val p = d * DotMatrix.PITCH
        val r = d / 2f
        val t = progress.value
        m.forEach { c, row, on ->
            val center = Offset(r + c * p, r + row * p)
            if (on) {
                val a = ((t - starts[row * m.cols + c]) / 0.25f).coerceIn(0f, 1f)
                if (dimColor.alpha > 0f && a < 1f) drawCircle(dimColor, r, center)
                if (a > 0f) drawCircle(color.copy(alpha = color.alpha * a), r, center)
            } else if (dimColor.alpha > 0f) {
                drawCircle(dimColor, r, center)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = LocalColors.current.dim) {
    Text(text.uppercase(), style = Type.label, color = color, modifier = modifier)
}

@Composable
fun PatroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(c.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
        content = content,
    )
}

/** Small rounded text button in Nothing's style (outlined pill). */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = LocalColors.current
    val bg = when {
        accent -> c.accent
        selected -> c.fg
        else -> Color.Transparent
    }
    val fg = when {
        accent -> c.onAccent
        selected -> c.bg
        enabled -> c.fg
        else -> c.dim
    }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (!accent && !selected) Modifier.border(1.dp, c.line, RoundedCornerShape(50)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.small, color = fg, textAlign = TextAlign.Center)
    }
}

/** Round icon-ish button holding a short text glyph ("+", "‹", "›", "×"). */
@Composable
fun RoundButton(glyph: String, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(c.faint)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = Type.title, color = c.fg)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun SpacedRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        content = content,
    )
}
