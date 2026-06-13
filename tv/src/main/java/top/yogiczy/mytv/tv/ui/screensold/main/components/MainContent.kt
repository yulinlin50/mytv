package top.yogiczy.mytv.tv.ui.screensold.main.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.Epg
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.match
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.recentProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.core.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.tv.ui.material.PopupContent
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.Visibility
import top.yogiczy.mytv.tv.ui.material.popupable
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsSubCategories
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.screensold.channel.ChannelNumberSelectScreen
import top.yogiczy.mytv.tv.ui.screensold.channel.ChannelScreen
import top.yogiczy.mytv.tv.ui.screensold.channel.ChannelTempScreen
import top.yogiczy.mytv.tv.ui.screensold.channel.rememberChannelNumberSelectState
import top.yogiczy.mytv.tv.ui.screensold.channelline.ChannelLineScreen
import top.yogiczy.mytv.tv.ui.screensold.classicchannel.ClassicChannelScreen
import top.yogiczy.mytv.tv.ui.screensold.datetime.DatetimeScreen
import top.yogiczy.mytv.tv.ui.screensold.epg.EpgProgrammeProgressScreen
import top.yogiczy.mytv.tv.ui.screensold.epg.EpgScreen
import top.yogiczy.mytv.tv.ui.screensold.epgreverse.EpgReverseScreen
import top.yogiczy.mytv.tv.ui.screensold.quickop.QuickOpScreen
import top.yogiczy.mytv.tv.ui.screensold.trackselectable.TracksScreen
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.VideoPlayerScreen
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.PlayerMetadata
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.rememberVideoPlayerStateNew
import top.yogiczy.mytv.tv.ui.screensold.videoplayercontroller.VideoPlayerControllerScreen
import top.yogiczy.mytv.tv.ui.screensold.videoplayerdiaplaymode.VideoPlayerDisplayModeScreen
import top.yogiczy.mytv.tv.ui.screensold.webview.WebViewScreen
import top.yogiczy.mytv.tv.ui.utils.handleDragGestures
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    filteredChannelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    favoriteChannelListProvider: () -> ChannelList = { ChannelList() },
    epgListProvider: () -> EpgList = { EpgList() },
    settingsViewModel: SettingsViewModel = settingsVM,
    onChannelFavoriteToggle: (Channel) -> Unit = {},
    toSettingsScreen: (SettingsSubCategories?) -> Unit = {},
    toDashboardScreen: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    val systemVolumeProvider: () -> Float = {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (maxVolume > 0) currentVolume.toFloat() / maxVolume.toFloat() else 1f
    }

    val videoPlayerState =
        rememberVideoPlayerStateNew(defaultDisplayModeProvider = { settingsViewModel.videoPlayerDisplayMode })
    val mainContentState = rememberMainContentState(
        videoPlayerState = videoPlayerState,
        channelGroupListProvider = filteredChannelGroupListProvider,
        favoriteChannelListProvider = favoriteChannelListProvider,
        epgListProvider = epgListProvider,
        systemVolumeProvider = systemVolumeProvider,
    )
    val channelNumberSelectState = rememberChannelNumberSelectState {
        val idx = it.toInt() - 1
        filteredChannelGroupListProvider().channelList.getOrNull(idx)?.let { channel ->
            mainContentState.changeCurrentChannel(channel)
        }
    }

    Box(
        modifier = modifier
            .popupable()
            .onPreviewKeyEvent {
                if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                    if (mainContentState.hasVisiblePopup()) {
                        mainContentState.closeAllPopups()
                        true
                    } else {
                        onBackPressed()
                        true
                    }
                } else {
                    false
                }
            }
            .handleKeyEvents(
                onUp = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToNext()
                    else mainContentState.changeCurrentChannelToPrev()
                },
                onDown = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToPrev()
                    else mainContentState.changeCurrentChannelToNext()
                },
                onLeft = {
                    if (mainContentState.currentChannel.lineList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelLineIdx - 1,
                        )
                    }
                },
                onRight = {
                    if (mainContentState.currentChannel.lineList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelLineIdx + 1,
                        )
                    }
                },
                onSelect = { mainContentState.isChannelScreenVisible = true },
                onLongSelect = { mainContentState.isQuickOpScreenVisible = true },
                onSettings = { mainContentState.isQuickOpScreenVisible = true },
                onLongLeft = { mainContentState.isEpgScreenVisible = true },
                onLongRight = { mainContentState.isChannelLineScreenVisible = true },
                onLongDown = { mainContentState.isVideoPlayerControllerScreenVisible = true },
                onNumber = { channelNumberSelectState.input(it) },
            )
            .handleDragGestures(
                onSwipeDown = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToNext()
                    else mainContentState.changeCurrentChannelToPrev()
                },
                onSwipeUp = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToPrev()
                    else mainContentState.changeCurrentChannelToNext()
                },
                onSwipeRight = {
                    if (mainContentState.currentChannel.lineList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelLineIdx - 1,
                        )
                    }
                },
                onSwipeLeft = {
                    if (mainContentState.currentChannel.lineList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelLineIdx + 1,
                        )
                    }
                },
            ),
    ) {
        VideoPlayerScreen(
            state = videoPlayerState,
            showMetadataProvider = { settingsViewModel.debugShowVideoPlayerMetadata },
        )

        Visibility({ mainContentState.currentChannelLine.url.startsWith("webview://") }) {
            WebViewScreen(
                urlProvider = { mainContentState.currentChannelLine.url },
                onVideoResolutionChanged = { width, height ->
                    videoPlayerState.updateMetadata { meta ->
                        meta.copy(
                            video = (meta.video ?: PlayerMetadata.VideoTrack()).copy(
                                width = width,
                                height = height,
                            ),
                        )
                    }
                    mainContentState.isTempChannelScreenVisible = false
                },
            )
        }
    }

    Visibility({ settingsViewModel.uiShowEpgProgrammePermanentProgress }) {
        EpgProgrammeProgressScreen(
            currentEpgProgrammeProvider = {
                mainContentState.currentPlaybackEpgProgramme ?: epgListProvider().recentProgramme(
                    mainContentState.currentChannel
                )?.now
            },
            // 使用 currentTimelinePosition() 替代 videoPlayerState.currentPosition
            // 因为 EpgProgramme.progress() 期望的是 Unix 时间戳，而不是相对偏移量
            videoPlayerCurrentPositionProvider = { mainContentState.currentTimelinePosition() },
        )
    }

    Visibility({
        !mainContentState.isTempChannelScreenVisible
                && !mainContentState.isChannelScreenVisible
                && !mainContentState.isQuickOpScreenVisible
                && !mainContentState.isEpgScreenVisible
                && !mainContentState.isChannelLineScreenVisible
                && channelNumberSelectState.channelNumber.isEmpty()
    }) {
        DatetimeScreen(showModeProvider = { settingsViewModel.uiTimeShowMode })
    }

    ChannelNumberSelectScreen(channelNumberProvider = { channelNumberSelectState.channelNumber })

    Visibility({
        mainContentState.isTempChannelScreenVisible
                && !mainContentState.isChannelScreenVisible
                && !mainContentState.isQuickOpScreenVisible
                && !mainContentState.isEpgScreenVisible
                && !mainContentState.isChannelLineScreenVisible
                && !mainContentState.isVideoPlayerControllerScreenVisible
                && channelNumberSelectState.channelNumber.isEmpty()
    }) {
        ChannelTempScreen(
            channelProvider = { mainContentState.currentChannel },
            channelLineIdxProvider = { mainContentState.currentChannelLineIdx },
            recentEpgProgrammeProvider = {
                epgListProvider().recentProgramme(mainContentState.currentChannel)
            },
            isInTimeShiftProvider = { mainContentState.isInTimeShift() },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            playerMetadataProvider = { videoPlayerState.metadata },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isEpgScreenVisible },
        onDismissRequest = { mainContentState.isEpgScreenVisible = false },
    ) {
        EpgScreen(
            epgProvider = {
                epgListProvider().match(mainContentState.currentChannel) ?: Epg.empty(
                    mainContentState.currentChannel
                )
            },
            epgProgrammeReserveListProvider = {
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList.filter {
                    it.channel == mainContentState.currentChannel.name
                })
            },
            supportPlaybackProvider = { mainContentState.hasPlaybackSupport() },
            canPlaybackProvider = { programme -> mainContentState.canPlaybackProgramme(programme) },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            hasCatchupTagProvider = { mainContentState.hasPlaybackSupport() },
            hasEpgDataProvider = { 
                val epg = epgListProvider().match(mainContentState.currentChannel)
                epg != null && epg.programmeList.isNotEmpty()
            },
            onEpgProgrammePlayback = {
                mainContentState.isEpgScreenVisible = false
                mainContentState.changeCurrentChannel(
                    mainContentState.currentChannel,
                    mainContentState.currentChannelLineIdx,
                    it,
                )
            },
            onEpgProgrammeReserve = { programme ->
                mainContentState.reverseEpgProgrammeOrNot(
                    mainContentState.currentChannel, programme
                )
            },
            onClose = { mainContentState.isEpgScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isChannelLineScreenVisible },
        onDismissRequest = { mainContentState.isChannelLineScreenVisible = false },
    ) {
        ChannelLineScreen(
            channelProvider = { mainContentState.currentChannel },
            currentLineProvider = { mainContentState.currentChannelLine },
            onLineSelected = {
                mainContentState.isChannelLineScreenVisible = false
                mainContentState.changeCurrentChannel(
                    mainContentState.currentChannel,
                    mainContentState.currentChannel.lineList.indexOf(it),
                )
            },
            onClose = { mainContentState.isChannelLineScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isVideoPlayerControllerScreenVisible },
        onDismissRequest = { mainContentState.isVideoPlayerControllerScreenVisible = false },
    ) {
        VideoPlayerControllerScreen(
            isVideoPlayerPlayingProvider = { videoPlayerState.isPlaying },
            isVideoPlayerBufferingProvider = { videoPlayerState.isBuffering },
            videoPlayerCurrentPositionProvider = { mainContentState.currentTimelinePosition() },
            videoPlayerDurationProvider = { mainContentState.currentTimelineRange() },
            isPlaybackModeProvider = { videoPlayerState.isPlaybackMode },
            onVideoPlayerPlay = { videoPlayerState.playWithFadeIn(systemVolumeProvider()) },
            onVideoPlayerPause = { videoPlayerState.pauseWithFadeOut() },
            onVideoPlayerSeekTo = { mainContentState.seekToPosition(it) },
            onClose = { mainContentState.isVideoPlayerControllerScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isVideoPlayerDisplayModeScreenVisible },
        onDismissRequest = { mainContentState.isVideoPlayerDisplayModeScreenVisible = false },
    ) {
        VideoPlayerDisplayModeScreen(
            currentDisplayModeProvider = { videoPlayerState.displayMode },
            onDisplayModeChanged = { videoPlayerState.displayMode = it },
            onApplyToGlobal = {
                mainContentState.isVideoPlayerDisplayModeScreenVisible = false
                settingsViewModel.videoPlayerDisplayMode = videoPlayerState.displayMode
                Snackbar.show("已应用到全局")
            },
            onClose = { mainContentState.isVideoPlayerDisplayModeScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isVideoTracksScreenVisible },
        onDismissRequest = { mainContentState.isVideoTracksScreenVisible = false },
    ) {
        TracksScreen(
            title = "视轨",
            trackListProvider = { videoPlayerState.metadata.videoTracks },
            onTrackChanged = {
                videoPlayerState.selectVideoTrack(it)
                mainContentState.isVideoTracksScreenVisible = false
            },
            onClose = { mainContentState.isVideoTracksScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isAudioTracksScreenVisible },
        onDismissRequest = { mainContentState.isAudioTracksScreenVisible = false },
    ) {
        TracksScreen(
            title = "音轨",
            trackListProvider = { videoPlayerState.metadata.audioTracks },
            onTrackChanged = {
                videoPlayerState.selectAudioTrack(it)
                mainContentState.isAudioTracksScreenVisible = false
            },
            onClose = { mainContentState.isAudioTracksScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isSubtitleTracksScreenVisible },
        onDismissRequest = { mainContentState.isSubtitleTracksScreenVisible = false },
    ) {
        TracksScreen(
            title = "字幕",
            trackListProvider = { videoPlayerState.metadata.subtitleTracks },
            onTrackChanged = {
                videoPlayerState.selectSubtitleTrack(it)
                mainContentState.isSubtitleTracksScreenVisible = false
            },
            onClose = { mainContentState.isSubtitleTracksScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isQuickOpScreenVisible },
        onDismissRequest = { mainContentState.isQuickOpScreenVisible = false },
    ) {
        QuickOpScreen(
            currentChannelProvider = { mainContentState.currentChannel },
            currentChannelLineIdxProvider = { mainContentState.currentChannelLineIdx },
            currentChannelNumberProvider = {
                (filteredChannelGroupListProvider().channelList.indexOf(mainContentState.currentChannel) + 1).toString()
            },
            epgListProvider = epgListProvider,
            isInTimeShiftProvider = { mainContentState.isInTimeShift() },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            playerDisplayModeProvider = { videoPlayerState.displayMode },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            onShowEpg = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isEpgScreenVisible = true
            },
            onShowChannelLine = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isChannelLineScreenVisible = true
            },
            onShowVideoPlayerController = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isVideoPlayerControllerScreenVisible = true
            },
            onShowVideoPlayerDisplayMode = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isVideoPlayerDisplayModeScreenVisible = true
            },
            onShowVideoTracks = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isVideoTracksScreenVisible = true
            },
            onShowAudioTracks = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isAudioTracksScreenVisible = true
            },
            onShowSubtitleTracks = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isSubtitleTracksScreenVisible = true
            },
            toSettingsScreen = {
                mainContentState.isQuickOpScreenVisible = false
                toSettingsScreen(it)
            },
            onClearCache = {
                settingsViewModel.iptvChannelLinePlayableHostList = emptySet()
                settingsViewModel.iptvChannelLinePlayableUrlList = emptySet()
                coroutineScope.launch {
                    IptvRepository.clearAllCache()
                    EpgRepository.clearAllCache()
                    Snackbar.show("缓存已清除，请重启应用")
                }
            },
            toDashboardScreen = {
                mainContentState.isQuickOpScreenVisible = false
                toDashboardScreen()
            },
            onToggleLiveSubtitle = {
                videoPlayerState.toggleLiveSubtitle()
            },
            onClose = { mainContentState.isQuickOpScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isChannelScreenVisible && !settingsViewModel.uiUseClassicPanelScreen },
        onDismissRequest = { mainContentState.isChannelScreenVisible = false },
    ) {
        ChannelScreen(
            channelGroupListProvider = filteredChannelGroupListProvider,
            favoriteChannelListProvider = favoriteChannelListProvider,
            currentChannelProvider = { mainContentState.currentChannel },
            currentChannelLineIdxProvider = { mainContentState.currentChannelLineIdx },
            showChannelLogoProvider = { settingsViewModel.uiShowChannelLogo },
            onChannelSelected = {
                mainContentState.isChannelScreenVisible = false
                mainContentState.changeCurrentChannel(it)
            },
            onChannelFavoriteToggle = onChannelFavoriteToggle,
            epgListProvider = epgListProvider,
            showEpgProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
            isInTimeShiftProvider = { mainContentState.isInTimeShift() },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            channelFavoriteEnabledProvider = { settingsViewModel.iptvChannelFavoriteEnable },
            channelFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
            onChannelFavoriteListVisibleChange = {
                settingsViewModel.iptvChannelFavoriteListVisible = it
            },
            onClose = { mainContentState.isChannelScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isChannelScreenVisible && settingsViewModel.uiUseClassicPanelScreen },
        onDismissRequest = { mainContentState.isChannelScreenVisible = false },
    ) {
        ClassicChannelScreen(
            channelGroupListProvider = filteredChannelGroupListProvider,
            favoriteChannelListProvider = favoriteChannelListProvider,
            currentChannelProvider = { mainContentState.currentChannel },
            currentChannelLineIdxProvider = { mainContentState.currentChannelLineIdx },
            showChannelLogoProvider = { settingsViewModel.uiShowChannelLogo },
            onChannelSelected = {
                mainContentState.isChannelScreenVisible = false
                mainContentState.changeCurrentChannel(it)
            },
            onChannelFavoriteToggle = onChannelFavoriteToggle,
            epgListProvider = epgListProvider,
            epgProgrammeReserveListProvider = {
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList)
            },
            showEpgProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
            isInTimeShiftProvider = { mainContentState.isInTimeShift() },
            supportPlaybackProvider = { mainContentState.hasPlaybackSupport(it, null) },
            canPlaybackProvider = { channel, programme ->
                mainContentState.canPlaybackProgramme(channel, null, programme)
            },
            hasCatchupTagProvider = { channel -> mainContentState.hasPlaybackSupport(channel, null) },
            hasEpgDataProvider = { channel ->
                val epg = epgListProvider().match(channel)
                epg != null && epg.programmeList.isNotEmpty()
            },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            onEpgProgrammePlayback = { channel, programme ->
                mainContentState.isChannelScreenVisible = false
                mainContentState.changeCurrentChannel(channel, null, programme)
            },
            onEpgProgrammeReserve = { channel, programme ->
                mainContentState.reverseEpgProgrammeOrNot(channel, programme)
            },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            channelFavoriteEnabledProvider = { settingsViewModel.iptvChannelFavoriteEnable },
            channelFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
            onChannelFavoriteListVisibleChange = {
                settingsViewModel.iptvChannelFavoriteListVisible = it
            },
            onClose = { mainContentState.isChannelScreenVisible = false },
        )
    }

    EpgReverseScreen(
        epgProgrammeReserveListProvider = { settingsViewModel.epgChannelReserveList },
        onConfirmReserve = { reserve ->
            filteredChannelGroupListProvider().channelList.firstOrNull { it.name == reserve.channel }
                ?.let {
                    mainContentState.changeCurrentChannel(it)
                }
        },
        onDeleteReserve = { reserve ->
            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList - reserve)
        },
    )
}
