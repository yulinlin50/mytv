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
    
    val focusEnterDuration: Int = 300
    val focusAnimationDuration: Int = 1000
    val glowAnimationDuration: Int = 800
    
    val minAlpha: Float = 0.6f
    val alphaRange: Float = 0.4f
    val glowMinAlpha: Float = 0.3f
    val glowMaxAlpha: Float = 0.6f
    val glowColorAlpha: Float = 0.3f
    val enhancedGlowAlpha: Float = 0.5f
    val alphaThreshold: Float = 0.01f
    
    val focusColor: Color @Composable get() = MaterialTheme.colorScheme.primary
    val glowColor: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = enhancedGlowAlpha)
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
        MaterialTheme.colorScheme.primary.copy(alpha = FocusDefaults.enhancedGlowAlpha)
    } else {
        glowColor
    }
    
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(FocusDefaults.focusEnterDuration),
        label = "focus_alpha"
    )
    
    val animatedProgressValue by if (enableAnimation && isFocused) {
        val infiniteTransition = rememberInfiniteTransition(label = "focus_animation")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(FocusDefaults.focusAnimationDuration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "focus_progress"
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    
    val finalAlpha = if (enableAnimation && isFocused) {
        FocusDefaults.minAlpha + animatedProgressValue * FocusDefaults.alphaRange
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
    if (!Configs.uiFocusOptimize) {
        return@composed this
    }
    
    val actualGlowColor = if (glowColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary.copy(alpha = FocusDefaults.glowColorAlpha)
    } else {
        glowColor
    }
    
    val animatedEnterAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(FocusDefaults.focusEnterDuration),
        label = "glow_enter_alpha"
    )
    
    if (!isFocused && animatedEnterAlpha <= FocusDefaults.alphaThreshold) {
        return@composed this
    }
    
    val animatedGlowAlpha by if (isFocused) {
        val infiniteTransition = rememberInfiniteTransition(label = "glow_animation")
        infiniteTransition.animateFloat(
            initialValue = FocusDefaults.glowMinAlpha,
            targetValue = FocusDefaults.glowMaxAlpha,
            animationSpec = infiniteRepeatable(
                animation = tween(FocusDefaults.glowAnimationDuration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow_progress"
        )
    } else {
        remember { mutableStateOf(FocusDefaults.glowMinAlpha) }
    }
    
    val finalAlpha = animatedGlowAlpha * animatedEnterAlpha
    
    this.drawBehind {
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
                radiusX = FocusDefaults.borderRadius.toPx(),
                radiusY = FocusDefaults.borderRadius.toPx(),
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
