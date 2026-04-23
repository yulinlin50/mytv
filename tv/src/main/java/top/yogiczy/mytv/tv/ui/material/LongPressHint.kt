package top.yogiczy.mytv.tv.ui.material

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

object LongPressHintDefaults {
    val hintDelay: Long = 1500L
    val hintDuration: Long = 3000L
    val showDuration: Long = 4000L
}

@Composable
fun LongPressHint(
    modifier: Modifier = Modifier,
    isFocused: Boolean,
    hintDelay: Long = LongPressHintDefaults.hintDelay,
    showHint: Boolean = true,
    hintContent: @Composable () -> Unit,
) {
    var isVisible by remember { mutableStateOf(false) }
    var hasShownHint by remember { mutableStateOf(false) }
    
    LaunchedEffect(isFocused, showHint) {
        if (isFocused && showHint && !hasShownHint) {
            delay(hintDelay)
            isVisible = true
            hasShownHint = true
            delay(LongPressHintDefaults.showDuration)
            isVisible = false
        } else {
            isVisible = false
        }
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
            animationSpec = tween(200),
            initialOffsetY = { it / 2 }
        ),
        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
            animationSpec = tween(200),
            targetOffsetY = { it / 2 }
        ),
        modifier = modifier
    ) {
        hintContent()
    }
}

@Composable
fun LongPressHintBubble(
    modifier: Modifier = Modifier,
    text: String = "长按查看更多选项",
    icon: @Composable () -> Unit = { DefaultLongPressIcon() },
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hint_animation")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hint_alpha"
    )
    
    Row(
        modifier = modifier
            .alpha(animatedAlpha)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DefaultLongPressIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "icon_animation")
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_progress"
    )
    
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.2f + animatedProgress * 0.3f))
    )
}

@Composable
fun LongPressIndicator(
    modifier: Modifier = Modifier,
    isFocused: Boolean,
    showHint: Boolean = true,
    hintText: String = "长按查看更多",
) {
    LongPressHint(
        modifier = modifier,
        isFocused = isFocused,
        showHint = showHint,
    ) {
        LongPressHintBubble(text = hintText)
    }
}

@Composable
fun rememberLongPressState(
    initialHasShown: Boolean = false,
): LongPressState {
    return remember { LongPressState(initialHasShown) }
}

class LongPressState(
    initialHasShown: Boolean = false,
) {
    var hasShownHint by mutableStateOf(initialHasShown)
        private set
    
    var lastFocusTime by mutableLongStateOf(0L)
        private set
    
    fun onFocused() {
        lastFocusTime = System.currentTimeMillis()
    }
    
    fun reset() {
        hasShownHint = false
        lastFocusTime = 0L
    }
    
    fun markAsShown() {
        hasShownHint = true
    }
}

@Composable
fun SmartLongPressHint(
    modifier: Modifier = Modifier,
    isFocused: Boolean,
    state: LongPressState = rememberLongPressState(),
    hintText: String = "长按查看更多选项",
    hintDelay: Long = LongPressHintDefaults.hintDelay,
) {
    var shouldShow by remember { mutableStateOf(false) }
    
    LaunchedEffect(isFocused) {
        if (isFocused && !state.hasShownHint) {
            state.onFocused()
            delay(hintDelay)
            if (System.currentTimeMillis() - state.lastFocusTime >= hintDelay) {
                shouldShow = true
                delay(LongPressHintDefaults.showDuration)
                shouldShow = false
                state.markAsShown()
            }
        } else {
            shouldShow = false
        }
    }
    
    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
            animationSpec = tween(200),
            initialOffsetY = { it / 2 }
        ),
        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
            animationSpec = tween(200),
            targetOffsetY = { it / 2 }
        ),
        modifier = modifier
    ) {
        LongPressHintBubble(text = hintText)
    }
}
