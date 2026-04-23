package top.yogiczy.mytv.tv.ui.screensold.videoplayercontroller.components

import androidx.annotation.IntRange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import top.yogiczy.mytv.tv.ui.material.Slider
import top.yogiczy.mytv.tv.ui.material.SliderDefaults
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * 播放器位置控制器
 * 支持直播和回放两种模式的进度控制
 *
 * @param isPlaybackMode 是否为回放模式（VOD），直播模式下拖动会进入时移
 */
@Composable
fun VideoPlayerControllerPositionCtrl(
    modifier: Modifier = Modifier,
    currentPositionProvider: () -> Long = { 0L },
    durationProvider: () -> Pair<Long, Long> = { 0L to 0L },
    onSeekTo: (Long) -> Unit = {},
    isPlaybackMode: Boolean = false,
) {
    var seekToPosition by remember { mutableStateOf<Long?>(null) }

    val debounce = rememberDebounce(
        wait = 500L,
        func = {
            seekToPosition?.let { nnSeekToPosition ->
                val startPosition = durationProvider().first
                onSeekTo(nnSeekToPosition - startPosition)
                seekToPosition = null
            }
        },
    )
    LaunchedEffect(seekToPosition) {
        if (seekToPosition != null) debounce.active()
    }

    fun seekBackward(ms: Long) {
        val currentPosition = currentPositionProvider()
        val startPosition = durationProvider().first
        seekToPosition = max(startPosition, (seekToPosition ?: currentPosition) - ms)
    }

    fun seekForward(ms: Long) {
        val currentPosition = currentPositionProvider()
        val endPosition = durationProvider().second
        // 在回放模式下，使用 endPosition 作为上限；直播模式下才使用 System.currentTimeMillis()
        val upperBound = when {
            endPosition <= 0L -> {
                // 无效的结束位置，使用当前时间作为上限（直播模式）或不允许前进（回放模式）
                if (isPlaybackMode) currentPosition else System.currentTimeMillis()
            }
            isPlaybackMode -> endPosition
            else -> min(endPosition, System.currentTimeMillis())
        }
        seekToPosition = min(upperBound, (seekToPosition ?: currentPosition) + ms)
    }

    fun onSliderValueChange(newProgress: Float) {
        val duration = durationProvider()
        if (duration.second > duration.first) {
            val newPosition = duration.first + ((duration.second - duration.first) * newProgress).toLong()
            seekToPosition = newPosition
        }
    }

    fun onSliderValueChangeFinished() {
        if (seekToPosition != null) {
            debounce.active()
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoPlayerControllerBtn(
            imageVector = Icons.Default.KeyboardDoubleArrowLeft,
            onSelect = { seekBackward(1000L * 60 * 10) },
        )
        VideoPlayerControllerBtn(
            imageVector = Icons.Default.ChevronLeft,
            onSelect = { seekBackward(1000L * 60 * 1) },
        )

        VideoPlayerControllerBtn(
            imageVector = Icons.Default.ChevronRight,
            onSelect = { seekForward(1000L * 60 * 1) },
        )
        VideoPlayerControllerBtn(
            imageVector = Icons.Default.KeyboardDoubleArrowRight,
            onSelect = { seekForward(1000L * 60 * 10) },
        )

        VideoPlayerControllerPositionProgress(
            modifier = Modifier.padding(start = 10.dp),
            currentPositionProvider = { seekToPosition ?: currentPositionProvider() },
            durationProvider = durationProvider,
            isPlaybackMode = isPlaybackMode,
            onSeekTo = ::onSliderValueChange,
            onSeekToFinished = ::onSliderValueChangeFinished,
        )
    }
}

@Composable
private fun VideoPlayerControllerPositionProgress(
    modifier: Modifier = Modifier,
    currentPositionProvider: () -> Long = { 0L },
    durationProvider: () -> Pair<Long, Long> = { 0L to 0L },
    isPlaybackMode: Boolean = false,
    onSeekTo: ((Float) -> Unit)? = null,
    onSeekToFinished: (() -> Unit)? = null,
) {
    val currentPosition = currentPositionProvider()
    val duration = durationProvider()
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 开始时间
        Text(
            text = timeFormat.format(duration.first),
        )

        // 进度条 - 回放模式显示可拖动进度，直播模式也支持时移
        val progress = if (duration.second > duration.first) {
            (currentPosition - duration.first).toFloat() / (duration.second - duration.first)
        } else 1f

        Slider(
            value = progress.coerceIn(0f, 1f),
            onValueChange = { newProgress ->
                onSeekTo?.invoke(newProgress)
            },
            onValueChangeFinished = {
                onSeekToFinished?.invoke()
            },
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
            colors = SliderDefaults.colors(
                trackColor = MaterialTheme.colorScheme.surface.copy(0.2f),
                progressColor = MaterialTheme.colorScheme.onSurface,
                thumbColor = MaterialTheme.colorScheme.onSurface,
                thumbActiveColor = MaterialTheme.colorScheme.onSurface,
            ),
            thumbSize = 14.dp,
            trackHeight = 6.dp,
        )

        // 当前时间 / 结束时间
        Text(
            text = "${timeFormat.format(currentPosition)} / ${timeFormat.format(duration.second)}",
        )
    }
}

@Stable
class Debounce internal constructor(
    @IntRange(from = 0) private val wait: Long,
    private val func: () -> Unit = {},
) {
    fun active() {
        channel.trySend(wait)
    }

    private val channel = Channel<Long>(Channel.CONFLATED)

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        channel.consumeAsFlow().debounce { it }.collect {
            func()
        }
    }
}

@Composable
fun rememberDebounce(
    @IntRange(from = 0) wait: Long,
    func: () -> Unit = {},
) = remember { Debounce(wait = wait, func = func) }.also {
    LaunchedEffect(it) { it.observe() }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun VideoPlayerControllerPositionCtrlPreview() {
    MyTvTheme {
        Box(modifier = Modifier.width(600.dp)) {
            VideoPlayerControllerPositionCtrl(
                currentPositionProvider = { System.currentTimeMillis() },
                durationProvider = {
                    System.currentTimeMillis() - 1000L * 60 * 60 to System.currentTimeMillis() + 1000L * 60 * 60
                },
            )
        }
    }
}
