package top.yogiczy.mytv.tv.ui.screensold.epg.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme.Companion.isLive
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunchedSaveable
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun EpgProgrammeItem(
    modifier: Modifier = Modifier,
    epgProgrammeProvider: () -> EpgProgramme = { EpgProgramme() },
    supportPlaybackProvider: () -> Boolean = { false },
    canPlaybackProvider: (EpgProgramme) -> Boolean = { false },
    isPlaybackProvider: () -> Boolean = { false },
    hasReservedProvider: () -> Boolean = { false },
    hasCatchupTagProvider: () -> Boolean = { false },
    hasEpgDataProvider: () -> Boolean = { false },
    onPlayback: () -> Unit = {},
    onReserve: () -> Unit = {},
    focusOnLive: Boolean = true,
) {
    val programme = epgProgrammeProvider()
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val now = System.currentTimeMillis()
    val isLive = remember(programme.startAt, programme.endAt) { 
        now >= programme.startAt && now < programme.endAt 
    }
    
    val isEnded = programme.endAt < now
    val hasCatchupTag = hasCatchupTagProvider()
    val hasEpgData = hasEpgDataProvider()
    val supportPlayback = supportPlaybackProvider()

    // 回看激活条件：支持回放 + 有EPG数据 + 节目已结束 + 在回看时间范围内
    // 支持回放的条件：有回看标签 或 线路支持回放（通过URL模式检测）
    val canPlayback = (hasCatchupTag || supportPlayback) && hasEpgData && isEnded && canPlaybackProvider(programme)
    val isFuture = programme.startAt > now

    var isFocused by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme
    val localContentColor = LocalContentColor.current
    val containerColor = remember(isFocused, isLive) {
        if (isFocused) colorScheme.onSurface
        else if (isLive) colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else Color.Transparent
    }
    val contentColor = remember(isFocused, isLive) {
        if (isFocused) colorScheme.surface
        else if (isLive) colorScheme.onSurface
        else localContentColor
    }

    Box(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .focusable()
            .fillMaxWidth()
            .sizeIn(minHeight = 40.dp)
            .background(containerColor, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .handleKeyEvents(
                onSelect = {
                    if (canPlayback) onPlayback()
                    else if (isFuture) onReserve()
                }
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${timeFormat.format(programme.startAt)}    ${programme.title}",
                maxLines = if (isFocused) Int.MAX_VALUE else 1,
                color = contentColor,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )

            CompositionLocalProvider(
                LocalContentColor provides contentColor,
                LocalTextStyle provides MaterialTheme.typography.labelSmall,
            ) {
                when {
                    isLive -> {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    isPlaybackProvider() -> {
                        Text("正在回放")
                    }
                    canPlayback -> {
                        Text("回放")
                    }
                    isEnded && hasCatchupTag && !hasEpgData -> {
                        Text("无EPG", color = contentColor.copy(0.5f))
                    }
                    isEnded && supportPlaybackProvider() -> {
                        Text("已过期", color = contentColor.copy(0.5f))
                    }
                    isFuture -> {
                        if (hasReservedProvider()) Text("已预约")
                        else Text("预约")
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun EpgProgrammeItemPreview() {
    MyTvTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            EpgProgrammeItem(
                epgProgrammeProvider = { EpgProgramme.EXAMPLE },
            )
            EpgProgrammeItem(
                epgProgrammeProvider = {
                    EpgProgramme.EXAMPLE.copy(
                        startAt = System.currentTimeMillis() - 3600000,
                        endAt = System.currentTimeMillis() - 1800000,
                    )
                },
                supportPlaybackProvider = { true },
            )
            EpgProgrammeItem(
                epgProgrammeProvider = {
                    EpgProgramme.EXAMPLE.copy(
                        startAt = System.currentTimeMillis() - 72 * 3600000,
                        endAt = System.currentTimeMillis() - 71 * 3600000,
                    )
                },
                supportPlaybackProvider = { true },
            )
            EpgProgrammeItem(
                epgProgrammeProvider = {
                    EpgProgramme.EXAMPLE.copy(
                        startAt = System.currentTimeMillis() + 100000,
                        endAt = System.currentTimeMillis() + 200000,
                    )
                },
            )
            EpgProgrammeItem(
                epgProgrammeProvider = {
                    EpgProgramme.EXAMPLE.copy(
                        startAt = System.currentTimeMillis() + 100000,
                        endAt = System.currentTimeMillis() + 200000,
                    )
                },
                hasReservedProvider = { true },
            )
        }
    }
}
