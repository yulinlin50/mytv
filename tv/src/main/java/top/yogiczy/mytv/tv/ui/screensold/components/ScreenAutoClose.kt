package top.yogiczy.mytv.tv.ui.screensold.components

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import top.yogiczy.mytv.core.data.utils.Constants

@Stable
class ScreenAutoClose internal constructor(
    @IntRange(from = 0) private val timeout: Long,
    private val onTimeout: () -> Unit = {},
) {
    fun active() {
        lastActiveTime = System.currentTimeMillis()
    }

    @Volatile
    private var lastActiveTime = System.currentTimeMillis()

    suspend fun observe() {
        while (true) {
            val elapsed = System.currentTimeMillis() - lastActiveTime
            if (elapsed >= timeout) {
                onTimeout()
                return
            }
            delay((timeout - elapsed).coerceIn(0, 1000))
        }
    }
}

@Composable
fun rememberScreenAutoCloseState(
    @IntRange(from = 0) timeout: Long = Constants.UI_SCREEN_AUTO_CLOSE_DELAY,
    onTimeout: () -> Unit = {},
) = remember { ScreenAutoClose(timeout = timeout, onTimeout = onTimeout) }.also {
    LaunchedEffect(it) { it.observe() }
    it.active()
}