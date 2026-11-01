package io.github.patience

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Native port of open-claude-ui c1f923a: cds-theme.css, CdsButton,
// ConfirmationDialog and the borderless session rows. See docs/design.md.
internal class CdsColors {
    val canvas = Color.Black
    val ink = Color.White
    val secondary = Color.White
    val panel = Color.Black
    val border = ink.copy(alpha = .09f)
    val hover = ink.copy(alpha = .05f)
    val selected = ink.copy(alpha = .09f)
}

internal val LocalCds = staticCompositionLocalOf { CdsColors() }
internal val CdsEaseOut = CubicBezierEasing(.165f, .84f, .44f, 1f)
private val CdsSwift = CubicBezierEasing(.16f, 1f, .3f, 1f)

// Exactly the 15-stop --cds-btn-spring curve, sampled at equal intervals.
private val CdsButtonSpring = Easing { fraction ->
    val stops = floatArrayOf(0f, .2459f, .6526f, .9468f, 1.0764f, 1.0915f,
        1.0585f, 1.0219f, .9993f, .9914f, .9921f, .9957f, .9988f, 1.0004f, 1f)
    val position = fraction.coerceIn(0f, 1f) * (stops.size - 1)
    val index = position.toInt().coerceAtMost(stops.size - 2)
    stops[index] + (stops[index + 1] - stops[index]) * (position - index)
}

@Composable
internal fun CdsText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 16,
    secondary: Boolean = false,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
) {
    val colors = LocalCds.current
    BasicText(text, modifier, style = TextStyle(
        color = if (secondary) colors.secondary else colors.ink,
        fontSize = size.sp,
        lineHeight = (if (size == 20) 28 else 24).sp,
        fontFamily = FontFamily.SansSerif,
        fontWeight = weight,
    ), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

internal enum class Glyph { Back }

@Composable
internal fun CdsButton(
    label: String,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    iconOnly: Boolean = false,
    filled: Boolean = false,
    @DrawableRes artwork: Int? = null,
    onClick: () -> Unit,
) {
    val colors = LocalCds.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (pressed) .975f else 1f,
        tween(if (pressed) 60 else 450, easing = if (pressed) CdsEaseOut else CdsButtonSpring))
    val fill by animateColorAsState(when {
        filled -> colors.selected
        pressed -> colors.selected
        hovered || focused -> colors.hover
        else -> Color.Transparent
    }, tween(150, easing = CdsSwift))
    val shape = RoundedCornerShape(if (iconOnly) 8.dp else 12.dp)
    // Keep CDS's 32/40dp paint geometry while giving touch input a 48dp target.
    Box(modifier.height(48.dp).then(if (iconOnly) Modifier.width(48.dp) else Modifier)
        .clip(shape).hoverable(interactions)
        .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Row(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (iconOnly) Modifier.size(32.dp) else Modifier.height(40.dp))
            .background(fill, shape)
            .then(if (focused) Modifier.border(1.5.dp, colors.ink, shape) else Modifier)
            .padding(horizontal = if (iconOnly) 0.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            val ink = colors.ink
            if (artwork != null) {
                Image(painterResource(artwork), contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
            } else if (glyph != null) CdsGlyph(glyph, ink)
            if (!iconOnly) {
                if (glyph != null) Spacer(Modifier.width(8.dp))
                BasicText(label, style = TextStyle(color = ink, fontSize = 16.sp,
                    fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun CdsGlyph(glyph: Glyph, color: Color) {
    // CDS Icon protocol: 256-unit viewBox, 16-unit regular stroke, 20dp display.
    Canvas(Modifier.size(20.dp)) {
        val unit = size.width / 256f
        val path = Path().apply {
            fun move(x: Float, y: Float) = moveTo(x * unit, y * unit)
            fun line(x: Float, y: Float) = lineTo(x * unit, y * unit)
            when (glyph) {
                Glyph.Back -> { move(160f, 48f); line(80f, 128f); line(160f, 208f) }
            }
        }
        drawPath(path, color, style = Stroke(16f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
