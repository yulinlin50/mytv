package top.yogiczy.mytv.tv.ui.screen.channels.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme.Companion.progress
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeRecent
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeRecentCache
import top.yogiczy.mytv.core.util.utils.M3u8AnalysisUtil
import top.yogiczy.mytv.core.util.utils.isIPv6
import top.yogiczy.mytv.core.util.utils.urlHost
import top.yogiczy.mytv.tv.ui.material.LongPressIndicator
import top.yogiczy.mytv.tv.ui.material.enhancedFocus
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.ui.utils.gridColumns
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse

@Composable
fun ChannelsChannelItem(
    modifier: Modifier = Modifier,
    channelProvider: () -> Channel = { Channel() },
    onChannelSelected: () -> Unit = {},
    onChannelFavoriteToggle: () -> Unit = {},
    recentEpgProgrammeProvider: () -> EpgProgrammeRecent? = { null },
    useCache: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val isLowPerformanceMode = Configs.isLowPerformanceMode

    val colorScheme = MaterialTheme.colorScheme
    val localContentColor = LocalContentColor.current
    val containerColor = remember(isFocused) {
        if (isFocused) colorScheme.onSurface
        else colorScheme.onSurface.copy(0.1f)
    }
    val contentColor = remember(isFocused) {
        if (isFocused) colorScheme.surface
        else localContentColor
    }

    val channel = channelProvider()
    val recentEpgProgramme = if (useCache) {
        rememberEpgProgrammeRecentWithCache(channel, recentEpgProgrammeProvider)
    } else {
        rememberEpgProgrammeRecent(recentEpgProgrammeProvider)
    }

    Box(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .width(if (isLowPerformanceMode) 2.0f.gridColumns() else 2.4f.gridColumns())
            .handleKeyEvents(
                onSelect = onChannelSelected,
                onLongSelect = onChannelFavoriteToggle,
            )
            .focusable()
            .enhancedFocus(
                isFocused = isFocused,
                enableAnimation = !isLowPerformanceMode
            )
            .clip(MaterialTheme.shapes.medium),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column {
                if (!isLowPerformanceMode) {
                    ChannelsChannelItemLogoWithPreview(
                        channelProvider = channelProvider,
                        isFocusedProvider = { isFocused },
                    )
                }

                Box(
                    modifier = Modifier
                        .height(if (isLowPerformanceMode) 48.dp else 56.dp)
                        .background(containerColor)
                ) {
                    ChannelsChannelItemContent(
                        recentEpgProgramme = recentEpgProgramme,
                        channelProvider = channelProvider,
                        isFocusedProvider = { isFocused },
                    )

                    if (!isLowPerformanceMode) {
                        ChannelsChannelItemProgress(
                            recentEpgProgramme = recentEpgProgramme,
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
            }

            if (!isLowPerformanceMode) {
                ChannelsChannelItemTagList(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    channelProvider = channelProvider,
                    isFocusedProvider = { isFocused },
                )
                
                LongPressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    isFocused = isFocused,
                    showHint = settingsVM.iptvChannelFavoriteEnable,
                    hintText = "长按收藏/取消收藏"
                )
            }
        }
    }
}

@Composable
private fun ChannelsChannelItemLogoWithPreview(
    modifier: Modifier = Modifier,
    channelProvider: () -> Channel = { Channel() },
    isFocusedProvider: () -> Boolean = { false },
) {
    val isLowPerformanceMode = Configs.isLowPerformanceMode
    
    if (!settingsVM.uiShowChannelLogo) return

    val channel = channelProvider()
    val isFocused = isFocusedProvider()

    // 低性能模式下不显示频道预览
    val showChannelPreview = settingsVM.uiShowChannelPreview && !isLowPerformanceMode

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var lastChannel by remember { mutableStateOf(channel) }
    
    // 使用 snapshotFlow 监听多个状态变化
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow {
            Pair(channel, isFocused)
        }.collect { (currentChannel, currentIsFocused) ->
            // 当频道变化时，重置预览图
            if (currentChannel != lastChannel) {
                preview = null
                lastChannel = currentChannel
            }
            
            // 当需要显示预览图、已获取焦点且预览图未加载时，才加载预览图
            if (showChannelPreview && currentIsFocused && preview == null) {
                val line = currentChannel.lineList.firstOrNull { line ->
                    Configs.iptvChannelLinePlayableUrlList.contains(line.url)
                } ?: currentChannel.lineList.firstOrNull { line ->
                    Configs.iptvChannelLinePlayableHostList.contains(line.url.urlHost())
                } ?: currentChannel.lineList.first()
                preview = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    M3u8AnalysisUtil.getFirstFrame(line.url)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(
                if (isFocused) MaterialTheme.colorScheme.surface.copy(0.9f)
                else MaterialTheme.colorScheme.surface.copy(0.5f)
            )
            .fillMaxWidth()
            .aspectRatio(if (isLowPerformanceMode) 4 / 3f else 16 / 9f),
    ) {
        if (showChannelPreview) {
            AnimatedVisibility(
                preview != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                val emptyBitmap = ImageBitmap(1, 1)

                Image(
                    painter = BitmapPainter(preview?.asImageBitmap() ?: emptyBitmap),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            }
        }

        if (preview == null || isLowPerformanceMode) {
            ChannelsChannelItemLogo(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(if (isLowPerformanceMode) 0.8f else 0.6f),
                channelProvider = channelProvider,
            ) { ChannelsChannelItemNo(channelProvider = channelProvider) }
        }
    }
}

@Composable
fun ChannelsChannelItemLogo(
    modifier: Modifier = Modifier,
    channelProvider: () -> Channel = { Channel() },
    placeholder: @Composable () -> Unit = {},
) {
    val isLowPerformanceMode = Configs.isLowPerformanceMode
    val channel = channelProvider()

    // 低性能模式下直接显示占位符，减少图片加载开销
    if (isLowPerformanceMode) {
        placeholder()
        return
    }

    val logo = if (settingsVM.iptvChannelLogoOverride || channel.logo.isNullOrBlank()) {
        settingsVM.iptvChannelLogoProvider
            .replace("{name}", channel.epgName)
            .replace("{name|lowercase}", channel.epgName.lowercase())
            .replace("{name|uppercase}", channel.epgName.uppercase())
    } else channel.logo

    SubcomposeAsyncImage(
        modifier = modifier,
        model = logo,
        contentDescription = null,
        loading = { placeholder() },
        error = {
            if (channel.logo != null) {
                SubcomposeAsyncImage(
                    model = channel.logo,
                    contentDescription = null,
                    loading = { placeholder() },
                    error = { placeholder() },
                )
            } else placeholder()
        },
    )
}

@Composable
private fun ChannelsChannelItemNo(
    modifier: Modifier = Modifier,
    channelProvider: () -> Channel = { Channel() },
) {
    val channel = channelProvider()
    if (channel.index <= -1) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.onSurface.copy(0.1f),
                MaterialTheme.shapes.medium
            ),
    ) {
        Text(
            channel.no,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun ChannelsChannelItemContent(
    modifier: Modifier = Modifier,
    channelProvider: () -> Channel = { Channel() },
    recentEpgProgramme: EpgProgrammeRecent? = null,
    isFocusedProvider: () -> Boolean = { false },
) {
    val isFocused = isFocusedProvider()

    val channel = channelProvider()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceAround,
    ) {
        Text(
            channel.name,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.ifElse(isFocused, Modifier.basicMarquee()),
        )

        Text(
            recentEpgProgramme?.now?.title ?: "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(LocalContentColor.current.copy(0.8f)),
            modifier = Modifier
                .ifElse(isFocused, Modifier.basicMarquee()),
        )
    }
}

@Composable
private fun ChannelsChannelItemProgress(
    modifier: Modifier = Modifier,
    recentEpgProgramme: EpgProgrammeRecent? = null,
) {
    recentEpgProgramme?.now?.let { nowProgramme ->
        if (settingsVM.uiShowEpgProgrammeProgress) {
            Box(
                modifier = modifier
                    .fillMaxWidth(nowProgramme.progress())
                    .height(2.dp)
                    .background(LocalContentColor.current.copy(0.8f)),
            )
        }
    }
}

@Composable
fun rememberEpgProgrammeRecent(provider: () -> EpgProgrammeRecent? = { null }): EpgProgrammeRecent? {
    var recentEpgProgramme by remember { mutableStateOf<EpgProgrammeRecent?>(null) }
    val latestProvider by rememberUpdatedState(provider)
    
    LaunchedEffect(Unit) {
        while (isActive) {
            val result = EpgList.action { latestProvider() }
            
            if (result != null) {
                recentEpgProgramme = result
                delay(Configs.uiEpgUpdateIntervalMs)
            } else {
                delay(2000)
            }
        }
    }

    return recentEpgProgramme
}

@Composable
fun rememberEpgProgrammeRecentWithCache(
    channel: Channel,
    provider: () -> EpgProgrammeRecent?
): EpgProgrammeRecent? {
    var recentEpgProgramme by remember { mutableStateOf<EpgProgrammeRecent?>(EpgProgrammeRecentCache.get(channel)) }
    val latestProvider by rememberUpdatedState(provider)
    val isLowPerformanceMode = Configs.isLowPerformanceMode
    
    LaunchedEffect(channel) {
        while (isActive) {
            val cached = EpgProgrammeRecentCache.get(channel)
            if (cached != null) {
                recentEpgProgramme = cached
            }
            
            // 低性能模式下使用更长的更新间隔，减少系统开销
            val result = EpgList.action { latestProvider() }
            if (result != null) {
                EpgProgrammeRecentCache.put(channel, result)
                recentEpgProgramme = result
                delay(if (isLowPerformanceMode) 60_000L else Configs.uiEpgUpdateIntervalMs)
            } else {
                delay(if (isLowPerformanceMode) 5000L else 2000L)
            }
        }
    }

    return recentEpgProgramme
}

@Composable
private fun ChannelsChannelItemTag(
    modifier: Modifier = Modifier,
    text: String,
    isFocusedProvider: () -> Boolean = { false },
) {
    val isFocused = isFocusedProvider()

    val colorScheme = MaterialTheme.colorScheme
    val containerColor = remember(isFocused) {
        if (isFocused) colorScheme.onSurface
        else colorScheme.surface.copy(0.5f)
    }

    Box(
        modifier = modifier
            .height(20.dp)
            .background(containerColor, MaterialTheme.shapes.extraSmall),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            lineHeight = TextUnit(12f, TextUnitType.Sp),
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .align(Alignment.Center),
        )
    }
}

@Composable
private fun ChannelsChannelItemTagList(
    modifier: Modifier = Modifier,
    channelProvider: () -> Channel = { Channel() },
    isFocusedProvider: () -> Boolean = { false },
) {
    if (!settingsVM.uiShowChannelLogo) return

    val channel = channelProvider()

    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (channel.lineList.size > 1) {
            ChannelsChannelItemTag(
                text = "${channel.lineList.size}线路",
                isFocusedProvider = isFocusedProvider,
            )
        }

        if (channel.lineList.all { it.url.isIPv6() }) {
            ChannelsChannelItemTag(
                text = "IPV6",
                isFocusedProvider = isFocusedProvider,
            )
        }
    }
}

@Preview
@Composable
private fun ChannelsChannelItemPreview() {
    MyTvTheme {
        ChannelsChannelItem(
            modifier = Modifier.padding(16.dp),
            channelProvider = { Channel.EXAMPLE.copy(index = 9999) },
            recentEpgProgrammeProvider = { EpgProgrammeRecent.EXAMPLE },
        )
    }
}
