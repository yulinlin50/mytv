package top.yogiczy.mytv.tv.ui.material

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

object SwipeGestureFeedbackDefaults {
    val indicatorSize: Int = 48
    val indicatorColor: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val animationDuration: Int = 300
}

@Composable
fun SwipeGestureFeedback(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
        
        if (enabled) {
            SwipeIndicator(
                onSwipeUp = onSwipeUp,
                onSwipeDown = onSwipeDown,
                onSwipeLeft = onSwipeLeft,
                onSwipeRight = onSwipeRight,
            )
        }
    }
}

@Composable
private fun SwipeIndicator(
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
) {
    var swipeDirection by remember { mutableStateOf<SwipeDirection?>(null) }
    var indicatorOffset by remember { mutableStateOf(Offset.Zero) }
    
    val animatedOffsetX by animateFloatAsState(
        targetValue = indicatorOffset.x,
        animationSpec = tween(SwipeGestureFeedbackDefaults.animationDuration),
        label = "swipe_x"
    )
    val animatedOffsetY by animateFloatAsState(
        targetValue = indicatorOffset.y,
        animationSpec = tween(SwipeGestureFeedbackDefaults.animationDuration),
        label = "swipe_y"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (swipeDirection != null) 1f else 0f,
        animationSpec = tween(SwipeGestureFeedbackDefaults.animationDuration),
        label = "indicator_alpha"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        swipeDirection = null
                        indicatorOffset = Offset.Zero
                    },
                    onDragEnd = {
                        when (swipeDirection) {
                            SwipeDirection.UP -> onSwipeUp()
                            SwipeDirection.DOWN -> onSwipeDown()
                            SwipeDirection.LEFT -> onSwipeLeft()
                            SwipeDirection.RIGHT -> onSwipeRight()
                            null -> {}
                        }
                        swipeDirection = null
                        indicatorOffset = Offset.Zero
                    },
                    onDragCancel = {
                        swipeDirection = null
                        indicatorOffset = Offset.Zero
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        
                        val threshold = 30f
                        
                        when {
                            swipeDirection == null -> {
                                when {
                                    dragAmount.y < -threshold -> swipeDirection = SwipeDirection.UP
                                    dragAmount.y > threshold -> swipeDirection = SwipeDirection.DOWN
                                    dragAmount.x < -threshold -> swipeDirection = SwipeDirection.LEFT
                                    dragAmount.x > threshold -> swipeDirection = SwipeDirection.RIGHT
                                }
                            }
                            swipeDirection == SwipeDirection.UP || swipeDirection == SwipeDirection.DOWN -> {
                                indicatorOffset = Offset(0f, dragAmount.y.coerceIn(-100f, 100f))
                            }
                            swipeDirection == SwipeDirection.LEFT || swipeDirection == SwipeDirection.RIGHT -> {
                                indicatorOffset = Offset(dragAmount.x.coerceIn(-100f, 100f), 0f)
                            }
                        }
                    }
                )
            }
    ) {
        if (swipeDirection != null) {
            SwipeDirectionIndicator(
                direction = swipeDirection!!,
                offsetX = animatedOffsetX,
                offsetY = animatedOffsetY,
                alpha = alpha,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun SwipeDirectionIndicator(
    direction: SwipeDirection,
    offsetX: Float,
    offsetY: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .alpha(alpha)
    ) {
        val (arrow, label) = when (direction) {
            SwipeDirection.UP -> "↑" to "换下一频道"
            SwipeDirection.DOWN -> "↓" to "换上一频道"
            SwipeDirection.LEFT -> "←" to "上一线路"
            SwipeDirection.RIGHT -> "→" to "下一线路"
        }
        
        Box(
            modifier = Modifier
                .size(SwipeGestureFeedbackDefaults.indicatorSize.dp)
                .clip(CircleShape)
                .background(SwipeGestureFeedbackDefaults.indicatorColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = arrow,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

enum class SwipeDirection {
    UP, DOWN, LEFT, RIGHT
}

@Composable
fun GestureFeedbackOverlay(
    modifier: Modifier = Modifier,
    showGestureFeedback: Boolean = true,
    channelChangeFlip: Boolean = false,
    content: @Composable () -> Unit,
) {
    SwipeGestureFeedback(
        modifier = modifier,
        enabled = showGestureFeedback,
        onSwipeUp = { },
        onSwipeDown = { },
    ) {
        content()
    }
}
