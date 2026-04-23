package top.yogiczy.mytv.tv.ui.material

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

@Composable
fun Visibility(
    visibleProvider: () -> Boolean = { false },
    animated: Boolean = false,
    animationDuration: Int = 200,
    content: @Composable () -> Unit,
) {
    // 优化：使用derivedStateOf缓存visible状态，避免不必要的重组
    val visible by remember { derivedStateOf(visibleProvider) }

    if (animated) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(animationDuration)),
            exit = fadeOut(animationSpec = tween(animationDuration))
        ) { content() }
    } else {
        if (visible) content()
    }
}