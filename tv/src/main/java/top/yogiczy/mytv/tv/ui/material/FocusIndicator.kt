package top.yogiczy.mytv.tv.ui.material

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import top.yogiczy.mytv.tv.ui.utils.Configs

object FocusDefaults {
    val borderWidth: Dp = 3.dp
    val borderRadius: Dp = 12.dp
    val glowRadius: Dp = 8.dp
    val animationDuration: Int = 1000
    
    val focusColor: Color @Composable get() = MaterialTheme.colorScheme.primary
    val glowColor: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
}

fun Modifier.enhancedFocus(
    isFocused: Boolean,
    borderWidth: Dp = FocusDefaults.borderWidth,
    borderRadius: Dp = FocusDefaults.borderRadius,
    glowRadius: Dp = FocusDefaults.glowRadius,
    focusColor: Color = Color.Unspecified,
    glowColor: Color = Color.Unspecified,
    enableAnimation: Boolean = true,
): Modifier = composed {
    if (!Configs.uiFocusOptimize) {
        return@composed this
    }
    
    val actualFocusColor = if (focusColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        focusColor
    }
    
    val actualGlowColor = if (glowColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        glowColor
    }
    
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(300),
        label = "focus_alpha"
    )
    
    val animatedProgress = if (enableAnimation && isFocused) {
        val infiniteTransition = rememberInfiniteTransition(label = "focus_animation")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(FocusDefaults.animationDuration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "focus_progress"
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    
    val finalAlpha = if (enableAnimation && isFocused) {
        0.6f + animatedProgress.value * 0.4f
    } else if (isFocused) {
        1f
    } else {
        0f
    }
    
    this
        .drawBehind {
            if (isFocused && glowRadius > 0.dp) {
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        this.asFrameworkPaint().apply {
                            this.color = actualGlowColor.toArgb()
                            setShadowLayer(
                                glowRadius.toPx(),
                                0f,
                                0f,
                                actualGlowColor.copy(alpha = finalAlpha).toArgb()
                            )
                        }
                    }
                    canvas.drawRoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        radiusX = borderRadius.toPx(),
                        radiusY = borderRadius.toPx(),
                        paint = paint
                    )
                }
            }
        }
        .border(
            width = if (isFocused) borderWidth else 0.dp,
            brush = Brush.linearGradient(
                colors = if (isFocused) {
                    listOf(
                        actualFocusColor.copy(alpha = finalAlpha),
                        actualFocusColor.copy(alpha = finalAlpha * 0.8f),
                        actualFocusColor.copy(alpha = finalAlpha)
                    )
                } else {
                    listOf(Color.Transparent, Color.Transparent, Color.Transparent)
                }
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(borderRadius)
        )
}

fun Modifier.focusGlow(
    isFocused: Boolean,
    glowRadius: Dp = FocusDefaults.glowRadius,
    glowColor: Color = Color.Unspecified,
): Modifier = composed {
    if (!Configs.uiFocusOptimize || !isFocused) {
        return@composed this
    }
    
    val actualGlowColor = if (glowColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    } else {
        glowColor
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "glow_animation")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_progress"
    )
    
    this.drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                this.asFrameworkPaint().apply {
                    this.color = actualGlowColor.toArgb()
                    setShadowLayer(
                        glowRadius.toPx(),
                        0f,
                        0f,
                        actualGlowColor.copy(alpha = animatedAlpha).toArgb()
                    )
                }
            }
            canvas.drawRoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                radiusX = 12.dp.toPx(),
                radiusY = 12.dp.toPx(),
                paint = paint
            )
        }
    }
}

@Composable
fun FocusableContainer(
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    focusBorderColor: Color = Color.Unspecified,
    focusGlowColor: Color = Color.Unspecified,
    enableAnimation: Boolean = true,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val actualFocusBorderColor = if (focusBorderColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        focusBorderColor
    }
    
    Box(
        modifier = modifier
            .onFocusChanged {
                val newFocused = it.isFocused || it.hasFocus
                if (newFocused != isFocused) {
                    isFocused = newFocused
                    onFocusChanged(newFocused)
                }
            }
            .enhancedFocus(
                isFocused = isFocused,
                focusColor = actualFocusBorderColor,
                glowColor = focusGlowColor,
                enableAnimation = enableAnimation
            )
            .focusable()
    ) {
        content(isFocused)
    }
}
