package top.yogiczy.mytv.tv.ui.screensold.videoplayercontroller.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.utils.PlaybackUtil
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 回放专用控制器
 * 提供更适合回放场景的控制按钮
 */
@Composable
fun VideoPlayerControllerPlayback(
    modifier: Modifier = Modifier,
    isPlayingProvider: () -> Boolean = { false },
    isBufferingProvider: () -> Boolean = { false },
    currentPositionProvider: () -> Long = { 0L },
    durationProvider: () -> Pair<Long, Long> = { 0L to 0L },
    onPlay: () -> Unit = {},
    onPause: () -> Unit = {},
    onSeekBack: (Long) -> Unit = {},
    onSeekForward: (Long) -> Unit = {},
) {
    val isPlaying = isPlayingProvider()
    val isBuffering = isBufferingProvider()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 快退10秒
        VideoPlayerControllerBtn(
            imageVector = Icons.Default.Replay10,
            onSelect = { onSeekBack(10_000) },
        )

        // 快退60秒
        VideoPlayerControllerBtn(
            imageVector = Icons.Default.FastRewind,
            onSelect = { onSeekBack(60_000) },
        )

        // 播放/暂停
        VideoPlayerControllerBtn(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            onSelect = { if (isPlaying) onPause() else onPlay() },
        )

        // 快进60秒
        VideoPlayerControllerBtn(
            imageVector = Icons.Default.FastForward,
            onSelect = { onSeekForward(60_000) },
        )

        // 快进10秒
        VideoPlayerControllerBtn(
            imageVector = Icons.Default.Forward10,
            onSelect = { onSeekForward(10_000) },
        )
    }

    // 显示当前播放进度
    val currentPosition = currentPositionProvider()
    val (startTime, endTime) = durationProvider()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 判断是否为绝对时间戳（Unix时间戳，大于2000年的毫秒值）
        // 同时检查 startTime 和 endTime，确保一致性
        val isAbsoluteTime = startTime > PlaybackUtil.UNIX_TIMESTAMP_THRESHOLD_MS &&
                endTime > PlaybackUtil.UNIX_TIMESTAMP_THRESHOLD_MS

        // 开始时间
        val startTimeText = if (isAbsoluteTime) {
            timeFormat.format(startTime)
        } else {
            // 如果是相对时间（从0开始），格式化为时长
            formatDuration(startTime.coerceAtLeast(0L))
        }

        Text(
            text = startTimeText,
            style = androidx.tv.material3.MaterialTheme.typography.bodySmall,
        )

        // 当前位置 / 结束时间
        val currentPositionText = if (isAbsoluteTime && currentPosition > PlaybackUtil.UNIX_TIMESTAMP_THRESHOLD_MS) {
            timeFormat.format(currentPosition)
        } else {
            formatDuration(currentPosition.coerceAtLeast(0L))
        }
        val endTimeText = if (isAbsoluteTime) {
            timeFormat.format(endTime)
        } else {
            formatDuration(endTime.coerceAtLeast(0L))
        }

        Text(
            text = "$currentPositionText / $endTimeText",
            style = androidx.tv.material3.MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * 将毫秒时长格式化为 HH:mm:ss 格式
 */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun VideoPlayerControllerPlaybackPreview() {
    MyTvTheme {
        PreviewWithLayoutGrids {
            Box(modifier = Modifier.padding(20.dp)) {
                VideoPlayerControllerPlayback(
                    isPlayingProvider = { true },
                    currentPositionProvider = { System.currentTimeMillis() },
                    durationProvider = {
                        System.currentTimeMillis() - 1000L * 60 * 60 to
                                System.currentTimeMillis() + 1000L * 60 * 30
                    },
                )
            }
        }
    }
}
