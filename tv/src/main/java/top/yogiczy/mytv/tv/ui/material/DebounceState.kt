package top.yogiczy.mytv.tv.ui.material

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Stable
class DebounceState internal constructor(
    @IntRange(from = 0) private val wait: Long,
    private val func: () -> Unit = {},
) {
    private var lastSendTime = 0L
    private val mutex = Mutex()
    
    suspend fun send() {
        val now = System.currentTimeMillis()
        if (now - lastSendTime >= wait) {
            mutex.withLock {
                if (now - lastSendTime >= wait) {
                    lastSendTime = now
                    func()
                }
            }
        }
    }
    
    fun sendImmediate() {
        lastSendTime = System.currentTimeMillis()
        func()
    }
}

@Composable
fun rememberDebounceState(
    @IntRange(from = 0) wait: Long,
    func: () -> Unit = {},
) = DebounceState(wait = wait, func = func).also {
    LaunchedEffect(it) { 
        it.send()
    }
}