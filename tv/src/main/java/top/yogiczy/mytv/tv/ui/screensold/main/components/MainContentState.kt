package top.yogiczy.mytv.tv.ui.screensold.main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelFirstOrNull
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelGroupIdx
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelIdx
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelLastOrNull
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.entities.channel.ChannelLineList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.Epg
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.recentProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserve
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.core.data.utils.PlaybackUtil
import top.yogiczy.mytv.core.util.utils.urlHost
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.VideoPlayerStateNew
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.rememberVideoPlayerStateNew
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

@Stable
class MainContentState(
    private val coroutineScope: CoroutineScope,
    private val videoPlayerState: VideoPlayerStateNew,
    private val channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    private val favoriteChannelListProvider: () -> ChannelList = { ChannelList() },
    private val epgListProvider: () -> EpgList = { EpgList() },
    private val settingsViewModel: SettingsViewModel,
    private val systemVolumeProvider: () -> Float = { 1f },
) : Loggable("MainContentState") {
    companion object {
        // 使用 DateTimeFormatter 替代 SimpleDateFormat，线程安全且性能更好
        private val TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }
    private var _currentChannel by mutableStateOf(Channel())
    val currentChannel get() = _currentChannel

    private var _currentChannelLineIdx by mutableIntStateOf(0)
    val currentChannelLineIdx get() = _currentChannelLineIdx

    val currentChannelLine get() = _currentChannel.lineList[_currentChannelLineIdx]

    private var _currentPlaybackEpgProgramme by mutableStateOf<EpgProgramme?>(null)
    val currentPlaybackEpgProgramme get() = _currentPlaybackEpgProgramme

    private var playbackUrlCandidates: List<String> = emptyList()
    private var playbackUrlCandidateIndex = 0
    private var pendingInitialPlaybackSeekMs: Long? = null
    private var activePlaybackWindowStartMs: Long = 0L

    private var _tempChannelScreenHideJob: Job? = null

    private var _isTempChannelScreenVisible by mutableStateOf(false)
    var isTempChannelScreenVisible
        get() = _isTempChannelScreenVisible
        set(value) {
            _isTempChannelScreenVisible = value
        }

    private var _isChannelScreenVisible by mutableStateOf(false)
    var isChannelScreenVisible
        get() = _isChannelScreenVisible
        set(value) {
            _isChannelScreenVisible = value
        }

    private var _isVideoPlayerControllerScreenVisible by mutableStateOf(false)
    var isVideoPlayerControllerScreenVisible
        get() = _isVideoPlayerControllerScreenVisible
        set(value) {
            _isVideoPlayerControllerScreenVisible = value
        }

    private var _isQuickOpScreenVisible by mutableStateOf(false)
    var isQuickOpScreenVisible
        get() = _isQuickOpScreenVisible
        set(value) {
            _isQuickOpScreenVisible = value
        }

    private var _isEpgScreenVisible by mutableStateOf(false)
    var isEpgScreenVisible
        get() = _isEpgScreenVisible
        set(value) {
            _isEpgScreenVisible = value
        }

    private var _isChannelLineScreenVisible by mutableStateOf(false)
    var isChannelLineScreenVisible
        get() = _isChannelLineScreenVisible
        set(value) {
            _isChannelLineScreenVisible = value
        }

    private var _isVideoPlayerDisplayModeScreenVisible by mutableStateOf(false)
    var isVideoPlayerDisplayModeScreenVisible
        get() = _isVideoPlayerDisplayModeScreenVisible
        set(value) {
            _isVideoPlayerDisplayModeScreenVisible = value
        }

    private var _isVideoTracksScreenVisible by mutableStateOf(false)
    var isVideoTracksScreenVisible
        get() = _isVideoTracksScreenVisible
        set(value) {
            _isVideoTracksScreenVisible = value
        }

    private var _isAudioTracksScreenVisible by mutableStateOf(false)
    var isAudioTracksScreenVisible
        get() = _isAudioTracksScreenVisible
        set(value) {
            _isAudioTracksScreenVisible = value
        }

    private var _isSubtitleTracksScreenVisible by mutableStateOf(false)
    var isSubtitleTracksScreenVisible
        get() = _isSubtitleTracksScreenVisible
        set(value) {
            _isSubtitleTracksScreenVisible = value
        }

    /**
     * 检查是否有弹窗显示
     */
    fun hasVisiblePopup(): Boolean {
        return isChannelScreenVisible ||
                isEpgScreenVisible ||
                isChannelLineScreenVisible ||
                isVideoPlayerControllerScreenVisible ||
                isVideoPlayerDisplayModeScreenVisible ||
                isVideoTracksScreenVisible ||
                isAudioTracksScreenVisible ||
                isSubtitleTracksScreenVisible ||
                isQuickOpScreenVisible
    }

    /**
     * 关闭所有弹窗
     */
    fun closeAllPopups() {
        isChannelScreenVisible = false
        isEpgScreenVisible = false
        isChannelLineScreenVisible = false
        isVideoPlayerControllerScreenVisible = false
        isVideoPlayerDisplayModeScreenVisible = false
        isVideoTracksScreenVisible = false
        isAudioTracksScreenVisible = false
        isSubtitleTracksScreenVisible = false
        isQuickOpScreenVisible = false
    }

    private fun clearPlaybackUrlCandidates() {
        playbackUrlCandidates = emptyList()
        playbackUrlCandidateIndex = 0
        pendingInitialPlaybackSeekMs = null
        activePlaybackWindowStartMs = 0L
    }

    private fun setPlaybackUrlCandidates(candidates: List<String>) {
        playbackUrlCandidates = candidates.distinct()
        playbackUrlCandidateIndex = 0
    }

    private fun currentPlaybackCandidateUrl(): String? {
        return playbackUrlCandidates.getOrNull(playbackUrlCandidateIndex)
    }

    private fun updatePendingInitialPlaybackSeek(playbackUrl: String, targetStartTime: Long, targetStartTimeWithShift: Long) {
        val actualStartTime = PlaybackUtil.extractPlaybackTimeRange(playbackUrl)?.first ?: targetStartTimeWithShift
        activePlaybackWindowStartMs = actualStartTime
        // 计算从URL窗口开始到目标开始时间的偏移量
        // 如果 URL 窗口开始时间早于节目开始时间，偏移量为正（需要向后seek）
        // 如果 URL 窗口开始时间晚于节目开始时间，偏移量为负（需要向前seek，但通常不会这种情况）
        val seekOffset = targetStartTimeWithShift - actualStartTime
        // 只保留正的偏移量（即URL窗口开始时间早于节目开始时间的情况）
        pendingInitialPlaybackSeekMs = seekOffset.takeIf { it >= 0L }
        
        // 记录 seek 信息到日志
        log.i("【回放Seek调试】目标开始时间: ${TIME_FORMATTER.format(Instant.ofEpochMilli(targetStartTime))}")
        log.i("【回放Seek调试】调整后目标时间: ${TIME_FORMATTER.format(Instant.ofEpochMilli(targetStartTimeWithShift))}")
        log.i("【回放Seek调试】URL实际开始时间: ${TIME_FORMATTER.format(Instant.ofEpochMilli(actualStartTime))}")
        log.i("【回放Seek调试】Seek偏移量: ${seekOffset}ms (${seekOffset / 1000 / 60}分钟)")
        log.i("【回放Seek调试】初始Seek位置: ${pendingInitialPlaybackSeekMs}ms")
    }

    private fun retryNextPlaybackCandidate(): Boolean {
        val nextIndex = playbackUrlCandidateIndex + 1
        val nextUrl = playbackUrlCandidates.getOrNull(nextIndex) ?: return false
        playbackUrlCandidateIndex = nextIndex
        val targetStartTime = _currentPlaybackEpgProgramme?.startAt ?: videoPlayerState.playbackStartTime
        
        // 使用原始线路，确保正确处理时区偏移
        val originalLine = _currentChannel.lineList[_currentChannelLineIdx]
        
        if (targetStartTime > 0L) {
            // 区分节目回放和时移模式：
            // - 节目回放：如果直播源设置了 tvgShift，则应用偏移
            // - 时移模式：不应用时区偏移，因为使用的是系统时间
            val isProgrammePlayback = _currentPlaybackEpgProgramme != null
            val tvgShift = originalLine.tvgShift
            val targetStartTimeWithShift = if (isProgrammePlayback && tvgShift != null) {
                PlaybackUtil.applyTimezoneShift(targetStartTime, tvgShift)
            } else {
                targetStartTime
            }
            updatePendingInitialPlaybackSeek(nextUrl, targetStartTime, targetStartTimeWithShift)
        }
        videoPlayerState.prepare(originalLine.copy(url = nextUrl))
        Snackbar.show("正在尝试其他回看格式")
        return true
    }

    private fun playCurrentPlaybackCandidate(originalLine: ChannelLine, startTime: Long, endTime: Long, isProgrammePlayback: Boolean = true): ChannelLine {
        // 区分节目回放和时移两种场景：
        // - 节目回放：startTime/endTime 是 EPG 节目时间
        //   如果直播源设置了 tvgShift，则应用偏移；否则直接使用节目单时间
        // - 时移模式：startTime/endTime 是系统时间，不需要应用 tvgShift
        
        // 获取时区偏移：只有直播源明确设置了 tvgShift 时才应用偏移
        // 如果没有设置，直接使用节目单的原始时间
        val tvgShift = originalLine.tvgShift
        
        val adjustedStartTime = if (isProgrammePlayback && tvgShift != null) {
            PlaybackUtil.applyTimezoneShift(startTime, tvgShift)
        } else {
            startTime
        }
        val adjustedEndTime = if (isProgrammePlayback && tvgShift != null) {
            PlaybackUtil.applyTimezoneShift(endTime, tvgShift)
        } else {
            endTime
        }

        val playbackUrl = currentPlaybackCandidateUrl()
            // 使用本地时间而非UTC时间生成回放URL
            // 原因：大多数国内/本地回放源使用本地时间，避免时区偏移导致的时间显示不一致
            ?: PlaybackUtil.generatePlaybackUrl(originalLine, adjustedStartTime, adjustedEndTime, false)

        // 使用日志系统记录调试信息（使用 INFO 级别，可在设置/日志中查看）
        val shiftInfo = if (tvgShift != null) "应用偏移: $tvgShift" else "无偏移(使用节目单原始时间)"
        log.i("【回放时间调试】tvgShift=$tvgShift ($shiftInfo)")
        log.i("【回放时间调试】原始开始时间: ${TIME_FORMATTER.format(Instant.ofEpochMilli(startTime))}")
        log.i("【回放时间调试】调整后开始时间: ${TIME_FORMATTER.format(Instant.ofEpochMilli(adjustedStartTime))}")
        log.i("【回放时间调试】原始结束时间: ${TIME_FORMATTER.format(Instant.ofEpochMilli(endTime))}")
        log.i("【回放时间调试】调整后结束时间: ${TIME_FORMATTER.format(Instant.ofEpochMilli(adjustedEndTime))}")
        log.i("【回放时间调试】生成的回放URL: $playbackUrl")

        // 从URL提取时间并记录
        val extractedTimeRange = PlaybackUtil.extractPlaybackTimeRange(playbackUrl)
        if (extractedTimeRange != null) {
            log.i("【回放时间调试】从URL提取的开始时间: ${TIME_FORMATTER.format(Instant.ofEpochMilli(extractedTimeRange.first))}")
            log.i("【回放时间调试】从URL提取的结束时间: ${TIME_FORMATTER.format(Instant.ofEpochMilli(extractedTimeRange.second))}")
        } else {
            log.i("【回放时间调试】无法从URL提取时间范围")
        }
        
        val playbackLine = originalLine.copy(url = playbackUrl)

        // 使用带时区偏移的时间更新 seek 位置
        updatePendingInitialPlaybackSeek(playbackUrl, startTime, adjustedStartTime)
        // 传入原始时间到 videoPlayerState
        // - 回放模式：原始节目时间，与 _currentPlaybackEpgProgramme 一致
        // - 时移模式：原始系统时间
        videoPlayerState.enterPlaybackMode(startTime, endTime)
        videoPlayerState.prepare(playbackLine)
        return playbackLine
    }

    init {
        val channelGroupList = channelGroupListProvider()

        changeCurrentChannel(settingsViewModel.iptvChannelLastPlay.isEmptyOrElse {
            channelGroupList.channelFirstOrNull() ?: Channel.EMPTY
        })

        videoPlayerState.onReady {
            settingsViewModel.iptvChannelLinePlayableUrlList += currentChannelLine.url
            settingsViewModel.iptvChannelLinePlayableHostList += currentChannelLine.url.urlHost()
            pendingInitialPlaybackSeekMs?.let { seekMs ->
                if (videoPlayerState.isPlaybackMode) {
                    log.d("回看初始定位: seek=${seekMs}ms")
                    videoPlayerState.seekTo(seekMs)
                }
                pendingInitialPlaybackSeekMs = null
            }
        }

        videoPlayerState.onError {
            if (videoPlayerState.isPlaybackMode) {
                if (retryNextPlaybackCandidate()) {
                    return@onError
                }
                val isProgrammePlayback = _currentPlaybackEpgProgramme != null
                log.d("回看播放失败，回退到直播模式")
                _currentPlaybackEpgProgramme = null
                clearPlaybackUrlCandidates()
                
                videoPlayerState.stop()
                videoPlayerState.exitPlaybackMode()
                
                coroutineScope.launch {
                    delay(500)
                    changeCurrentChannel(_currentChannel, _currentChannelLineIdx, forceReload = true)
                }
                
                Snackbar.show(
                    if (isProgrammePlayback) "节目已过期或超出回看范围，已切换到直播"
                    else "时移播放失败，已切换到直播"
                )
                return@onError
            }

            // 标记当前线路为不可播放
            settingsViewModel.iptvChannelLinePlayableUrlList -= currentChannelLine.url
            settingsViewModel.iptvChannelLinePlayableHostList -= currentChannelLine.url.urlHost()

            // 快速切换到下一个可用线路
            val nextLineIdx = findNextPlayableLineIdx(_currentChannel.lineList, _currentChannelLineIdx)
            if (nextLineIdx != null && nextLineIdx != _currentChannelLineIdx) {
                log.d("线路切换：从线路${_currentChannelLineIdx + 1}切换到线路${nextLineIdx + 1}")
                changeCurrentChannel(_currentChannel, nextLineIdx)
            }
        }

        videoPlayerState.onIsBuffering { isBuffering ->
            if (isBuffering) {
                _isTempChannelScreenVisible = true
            } else {
                _tempChannelScreenHideJob?.cancel()
                _tempChannelScreenHideJob = coroutineScope.launch {
                    val name = _currentChannel.name
                    val lineIdx = _currentChannelLineIdx
                    delay(Constants.UI_TEMP_CHANNEL_SCREEN_SHOW_DURATION)
                    if (name == _currentChannel.name && lineIdx == _currentChannelLineIdx) {
                        _isTempChannelScreenVisible = false
                    }
                }
            }
        }
    }

    /**
     * 查找下一个可播放的线路索引
     * 优先选择历史验证可播放的线路，其次选择http/https线路
     */
    private fun findNextPlayableLineIdx(lineList: ChannelLineList, currentIdx: Int): Int? {
        if (lineList.size <= 1) return null

        // 从下一个线路开始查找
        val startIdx = (currentIdx + 1) % lineList.size

        // 第一优先级：查找历史验证可播放的URL
        for (i in 0 until lineList.size - 1) {
            val idx = (startIdx + i) % lineList.size
            if (idx == currentIdx) continue
            if (settingsViewModel.iptvChannelLinePlayableUrlList.contains(lineList[idx].url)) {
                return idx
            }
        }

        // 第二优先级：查找历史验证可播放的Host
        for (i in 0 until lineList.size - 1) {
            val idx = (startIdx + i) % lineList.size
            if (idx == currentIdx) continue
            if (settingsViewModel.iptvChannelLinePlayableHostList.contains(lineList[idx].url.urlHost())) {
                return idx
            }
        }

        // 第三优先级：查找http/https线路
        for (i in 0 until lineList.size - 1) {
            val idx = (startIdx + i) % lineList.size
            if (idx == currentIdx) continue
            val url = lineList[idx].url
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return idx
            }
        }

        // 返回下一个线路（如果没有其他选择）
        return if (lineList.size > 1) (currentIdx + 1) % lineList.size else null
    }

    private fun getPrevFavoriteChannel(): Channel? {
        if (!settingsViewModel.iptvChannelFavoriteListVisible) return null

        val channelGroupList = channelGroupListProvider()
        val favoriteChannelList = favoriteChannelListProvider()

        if (_currentChannel !in favoriteChannelList) return null

        val currentIdx = favoriteChannelList.indexOf(_currentChannel)

        return favoriteChannelList.getOrElse(currentIdx - 1) {
            if (settingsViewModel.iptvChannelChangeListLoop) favoriteChannelList.lastOrNull()
            else channelGroupList.channelLastOrNull()
        }
    }

    private fun getNextFavoriteChannel(): Channel? {
        if (!settingsViewModel.iptvChannelFavoriteListVisible) return null

        val channelGroupList = channelGroupListProvider()
        val favoriteChannelList = favoriteChannelListProvider()

        if (_currentChannel !in favoriteChannelList) return null

        val currentIdx = favoriteChannelList.indexOf(_currentChannel)

        return favoriteChannelList.getOrElse(currentIdx + 1) {
            if (settingsViewModel.iptvChannelChangeListLoop) favoriteChannelList.firstOrNull()
            else channelGroupList.channelFirstOrNull()
        }
    }

    private fun getPrevChannel(): Channel {
        return getPrevFavoriteChannel() ?: run {
            val channelGroupList = channelGroupListProvider()
            return if (settingsViewModel.iptvChannelChangeListLoop) {
                val group =
                    channelGroupList.getOrElse(channelGroupList.channelGroupIdx(_currentChannel)) { channelGroupList.first() }
                val currentIdx = group.channelList.indexOf(_currentChannel)
                group.channelList.getOrElse(currentIdx - 1) { group.channelList.last() }
            } else {
                val currentIdx = channelGroupList.channelIdx(_currentChannel)
                channelGroupList.channelList.getOrElse(currentIdx - 1) {
                    channelGroupList.channelLastOrNull() ?: Channel()
                }
            }
        }
    }

    private fun getNextChannel(): Channel {
        return getNextFavoriteChannel() ?: run {
            val channelGroupList = channelGroupListProvider()
            return if (settingsViewModel.iptvChannelChangeListLoop) {
                val group =
                    channelGroupList.getOrElse(channelGroupList.channelGroupIdx(_currentChannel)) { channelGroupList.first() }
                val currentIdx = group.channelList.indexOf(_currentChannel)
                group.channelList.getOrElse(currentIdx + 1) { group.channelList.first() }
            } else {
                val currentIdx = channelGroupList.channelIdx(_currentChannel)
                channelGroupList.channelList.getOrElse(currentIdx + 1) {
                    channelGroupList.channelFirstOrNull() ?: Channel()
                }
            }
        }
    }

    fun getLineIdx(lineList: ChannelLineList, lineIdx: Int? = null): Int {
        // 如果指定了线路索引，直接使用
        if (lineIdx != null) {
            return (lineIdx + lineList.size) % lineList.size
        }

        // 智能线路选择：优先选择历史验证可播放的线路
        // 第一优先级：完全匹配的URL在可播放列表中
        val playableUrlIdx = lineList.indexOfFirst { line ->
            settingsViewModel.iptvChannelLinePlayableUrlList.contains(line.url)
        }
        if (playableUrlIdx >= 0) return playableUrlIdx

        // 第二优先级：相同Host的线路在可播放列表中
        val playableHostIdx = lineList.indexOfFirst { line ->
            settingsViewModel.iptvChannelLinePlayableHostList.contains(line.url.urlHost())
        }
        if (playableHostIdx >= 0) return playableHostIdx

        // 第三优先级：优先选择响应速度快的协议（http/https优先于其他）
        val httpIdx = lineList.indexOfFirst { line ->
            line.url.startsWith("http://") || line.url.startsWith("https://")
        }
        if (httpIdx >= 0) return httpIdx

        // 默认返回第一个线路
        return 0
    }

    fun hasPlaybackSupport(
        channel: Channel = _currentChannel,
        lineIdx: Int? = _currentChannelLineIdx,
    ): Boolean {
        val currentLineIdx = getLineIdx(channel.lineList, lineIdx)
        return channel.lineList.getOrNull(currentLineIdx)?.let(PlaybackUtil::isPlaybackAdvertised) == true
    }

    fun currentTimelineRange(): Pair<Long, Long> {
        if (videoPlayerState.isPlaybackMode) {
            // 回放模式：优先使用节目信息的时间范围
            _currentPlaybackEpgProgramme?.let { playbackProgramme ->
                return playbackProgramme.startAt to playbackProgramme.endAt
            }

            // 时移模式或没有节目信息的回放：使用 videoPlayerState 存储的时间范围
            val startTime = videoPlayerState.playbackStartTime
            val endTime = videoPlayerState.playbackEndTime
            if (startTime > 0 && endTime >= startTime) {
                return startTime to endTime
            }
        }

        // 直播模式：使用当前节目的时间范围
        val programme = epgListProvider().recentProgramme(_currentChannel)?.now
        if (programme != null) {
            val now = System.currentTimeMillis()
            return programme.startAt to min(programme.endAt, now)
        }

        // 后备：使用播放器时长
        val baseTime = -TimeZone.getDefault().getOffset(0L).toLong()
        return baseTime to (baseTime + videoPlayerState.duration.coerceAtLeast(0L))
    }

    /**
     * 获取当前时间轴位置（Unix时间戳）
     *
     * 处理逻辑：
     * 1. 回放/时移模式：播放器返回的是相对于节目开始的偏移量，需要加上节目开始时间
     * 2. 直播模式：根据播放器返回的位置类型（绝对时间戳或相对偏移）计算当前位置
     */
    fun currentTimelinePosition(): Long {
        val (startTime, endTime) = currentTimelineRange()
        val playerPosition = videoPlayerState.currentPosition.coerceAtLeast(0L)

        // 回放模式或时移模式（isPlaybackMode 包含时移模式）
        if (videoPlayerState.isPlaybackMode) {
            // 播放器返回的是相对于窗口开始的偏移量
            // 使用从URL提取的窗口开始时间（如果可用）或节目开始时间
            // 注意：activePlaybackWindowStartMs 和 startTime 的时间基准可能不同
            // - activePlaybackWindowStartMs: 从URL解析，已应用时区偏移（节目回放）或系统时间（时移）
            // - startTime: 原始节目时间（节目回放）或系统时间（时移）
            // 
            // 重要：返回的时间戳必须与 EPG 节目时间使用相同的时间基准（原始时间）
            // 因为 UI 层使用这个值与 EpgProgramme.startAt/endAt 进行比较
            val isProgrammePlayback = _currentPlaybackEpgProgramme != null
            val timeBase = if (isProgrammePlayback) {
                // 节目回放：startTime 是原始节目时间
                startTime
            } else {
                // 时移模式：startTime 是系统时间
                startTime
            }
            // activePlaybackWindowStartMs 的时间基准：
            // - 节目回放：已应用时区偏移
            // - 时移模式：系统时间
            // 需要转换到与 startTime 相同的时间基准
            val baseTime = if (isProgrammePlayback && activePlaybackWindowStartMs > 0L) {
                // 节目回放：将 URL 时间（已应用时区偏移）转换回原始时间
                // 原理：applyTimezoneShift(T, shift) = T + shiftMs，所以反向计算需要减去偏移量
                // 使用负偏移调用 applyTimezoneShift：T + (-shiftMs) = T - shiftMs
                // 只有直播源设置了 tvgShift 时才进行反向转换
                val tvgShift = currentChannelLine.tvgShift
                if (tvgShift != null) {
                    PlaybackUtil.applyTimezoneShift(activePlaybackWindowStartMs, -tvgShift)
                } else {
                    activePlaybackWindowStartMs
                }
            } else {
                activePlaybackWindowStartMs.takeIf { it > 0L } ?: timeBase
            }
            // 计算结果时间戳（原始时间基准）
            val resultTime = baseTime + playerPosition
            return resultTime.coerceIn(timeBase, endTime)
        }

        // 直播模式：判断播放器返回的是绝对时间戳还是相对偏移
        // 使用2000年1月1日作为分界
        val liveEnd = min(endTime, System.currentTimeMillis())
        return if (playerPosition > PlaybackUtil.UNIX_TIMESTAMP_THRESHOLD_MS) {
            // 绝对时间戳：直接使用
            playerPosition.coerceIn(startTime, liveEnd)
        } else {
            // 相对偏移：从节目开始时间计算
            (startTime + playerPosition).coerceIn(startTime, liveEnd)
        }
    }

    fun changeCurrentChannel(
        channel: Channel,
        lineIdx: Int? = null,
        playbackEpgProgramme: EpgProgramme? = null,
        forceReload: Boolean = false,
    ) {
        settingsViewModel.iptvChannelLastPlay = channel

        if (!forceReload &&
            channel == _currentChannel &&
            lineIdx == _currentChannelLineIdx &&
            playbackEpgProgramme == _currentPlaybackEpgProgramme
        ) return

        if (channel == _currentChannel && lineIdx != _currentChannelLineIdx) {
            settingsViewModel.iptvChannelLinePlayableUrlList -= currentChannelLine.url
            settingsViewModel.iptvChannelLinePlayableHostList -= currentChannelLine.url.urlHost()
        }

        _isTempChannelScreenVisible = true

        _currentChannel = channel
        _currentChannelLineIdx = getLineIdx(_currentChannel.lineList, lineIdx)

        _currentPlaybackEpgProgramme = playbackEpgProgramme

        // 关键修复：始终使用原始线路（从 channel.lineList 中获取，而不是可能已修改过的 currentChannelLine）
        val originalLine = _currentChannel.lineList[_currentChannelLineIdx]

        val line = if (_currentPlaybackEpgProgramme != null) {
            val playbackProgramme = _currentPlaybackEpgProgramme!!
            setPlaybackUrlCandidates(PlaybackUtil.buildPlaybackUrls(originalLine, playbackProgramme))
            val playbackLine = playCurrentPlaybackCandidate(
                originalLine,
                playbackProgramme.startAt,
                playbackProgramme.endAt
            )
            log.d("生成回放URL: channel=${_currentChannel.name}, programme=${playbackProgramme.title}, startAt=${playbackProgramme.startAt}, endAt=${playbackProgramme.endAt}, url=${playbackLine.url}")
            playbackLine
        } else {
            clearPlaybackUrlCandidates()
            videoPlayerState.exitPlaybackMode()
            videoPlayerState.fadeInFromMute(systemVolumeProvider())
            videoPlayerState.prepare(originalLine)
            originalLine
        }

        log.d("播放${_currentChannel.name} 线路${_currentChannelLineIdx + 1}/${_currentChannel.lineList.size}: ${line.url}")
    }

    // 优化：添加频道切换防抖，避免遥控器快速连按导致频繁切换
    // 使用 AtomicLong 确保线程安全
    private val lastChannelChangeTime = AtomicLong(0L)
    private val channelChangeDebounceMs = 300L

    private fun canChangeChannel(): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastTime = lastChannelChangeTime.get()
        return if (currentTime - lastTime >= channelChangeDebounceMs) {
            // 使用 CAS 操作确保原子性更新
            lastChannelChangeTime.compareAndSet(lastTime, currentTime)
        } else {
            false
        }
    }

    fun changeCurrentChannelToPrev() {
        if (canChangeChannel()) {
            changeCurrentChannel(getPrevChannel())
        }
    }

    fun changeCurrentChannelToNext() {
        if (canChangeChannel()) {
            changeCurrentChannel(getNextChannel())
        }
    }

    fun reverseEpgProgrammeOrNot(channel: Channel, programme: EpgProgramme) {
        val reverse = settingsViewModel.epgChannelReserveList.firstOrNull {
            it.test(channel, programme)
        }

        if (reverse != null) {
            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList - reverse)
            Snackbar.show("取消预约：${reverse.channel} - ${reverse.programme}")
        } else {
            val newReserve = EpgProgrammeReserve(
                channel = channel.name,
                programme = programme.title,
                startAt = programme.startAt,
                endAt = programme.endAt,
            )

            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList + newReserve)
            Snackbar.show("已预约：${channel.name} - ${programme.title}")
        }
    }

    fun supportPlayback(
        channel: Channel = _currentChannel,
        lineIdx: Int? = _currentChannelLineIdx,
    ): Boolean {
        val currentLineIdx = getLineIdx(channel.lineList, lineIdx)
        return PlaybackUtil.isPlaybackSupported(channel.lineList[currentLineIdx])
    }

    /**
     * 检查节目是否可以回放（使用当前线路的catchupDays设置）
     */
    fun canPlaybackProgramme(programme: EpgProgramme): Boolean {
        return PlaybackUtil.canPlaybackProgramme(programme, currentChannelLine)
    }

    fun canPlaybackProgramme(
        channel: Channel,
        lineIdx: Int? = null,
        programme: EpgProgramme,
    ): Boolean {
        val currentLineIdx = getLineIdx(channel.lineList, lineIdx)
        return PlaybackUtil.canPlaybackProgramme(programme, channel.lineList[currentLineIdx])
    }

    /**
     * 获取当前线路的最大回看时长（毫秒）
     */
    fun getMaxPlaybackDurationMs(): Long {
        return PlaybackUtil.getMaxPlaybackDurationMs(currentChannelLine)
    }

    /**
     * 获取当前线路的最大回看天数
     */
    fun getMaxPlaybackDays(): Int {
        return PlaybackUtil.getMaxPlaybackDays(currentChannelLine)
    }

    /**
     * 时移功能：从直播模式切换到时移播放
     * @param offsetMs 时移偏移量（毫秒），从当前时间往回偏移
     */
    fun timeshiftTo(offsetMs: Long) {
        if (offsetMs <= 0) return
        if (!supportPlayback()) {
            Snackbar.show("当前线路不支持时移")
            return
        }

        val now = System.currentTimeMillis()
        val startTime = now - offsetMs
        
        // 使用原始线路，确保正确构建时移 URL
        val originalLine = _currentChannel.lineList[_currentChannelLineIdx]

        log.d("时移播放: offset=${offsetMs}ms, startTime=${startTime}")

        _currentPlaybackEpgProgramme = null
        setPlaybackUrlCandidates(PlaybackUtil.buildTimeshiftUrls(originalLine, offsetMs))
        // 时移模式：传入 isProgrammePlayback = false，不应用 tvgShift
        val lineWithTimeshiftUrl = playCurrentPlaybackCandidate(originalLine, startTime, now, isProgrammePlayback = false)
        log.d("生成时移URL: ${lineWithTimeshiftUrl.url}")

        Snackbar.show("已切换到时移模式")
    }

    fun isInTimeShift(): Boolean {
        return videoPlayerState.isPlaybackMode && _currentPlaybackEpgProgramme == null
    }

    /**
     * 在当前节目中seek到指定位置（支持直播时移和回放seek）
     * @param positionMs 目标位置（相对于节目开始时间的偏移量，毫秒）
     */
    fun seekToPosition(positionMs: Long) {
        when {
            // 回放模式：有具体节目信息的回放
            videoPlayerState.isPlaybackMode && !isInTimeShift() -> {
                // 回放模式下的seek处理
                //
                // 时间线说明：
                // - 节目开始时间（原始）：programme.startAt（未应用时区偏移）
                // - 节目开始时间（偏移后）：adjustedStartTime = programme.startAt + tvgShift
                // - URL中的开始时间：playbackWindowStart（从URL解析，已应用时区偏移）
                // - 播放器当前位置：相对于 playbackWindowStart 的偏移量
                //
                // 当用户拖动进度条时，positionMs 是相对于节目开始的偏移量（0 = 节目开始）
                // 但播放器需要的位置是相对于 playbackWindowStart 的偏移量
                // 所以需要计算：actualSeekPosition = positionMs - (playbackWindowStart - adjustedProgrammeStartTime)

                val programmeStartTime = currentTimelineRange().first  // 原始节目开始时间
                // 应用时区偏移到节目开始时间，确保与URL中的时间基准一致
                // 只有直播源设置了 tvgShift 时才应用偏移
                val tvgShift = currentChannelLine.tvgShift
                val adjustedProgrammeStartTime = if (tvgShift != null) {
                    PlaybackUtil.applyTimezoneShift(programmeStartTime, tvgShift)
                } else {
                    programmeStartTime
                }
                val playbackWindowStart = activePlaybackWindowStartMs.takeIf { it > 0L } ?: adjustedProgrammeStartTime

                // 计算URL开始时间与调整后节目开始时间的差值
                val windowOffset = playbackWindowStart - adjustedProgrammeStartTime

                // 将相对于节目开始的偏移转换为相对于窗口开始的偏移
                val actualSeekPosition = (positionMs - windowOffset).coerceAtLeast(0L)

                // 记录 seek 信息到日志
                log.i("【回放Seek调试】用户拖动位置: ${positionMs}ms")
                log.i("【回放Seek调试】节目开始时间: ${TIME_FORMATTER.format(Instant.ofEpochMilli(programmeStartTime))}")
                log.i("【回放Seek调试】调整后节目时间: ${TIME_FORMATTER.format(Instant.ofEpochMilli(adjustedProgrammeStartTime))}")
                log.i("【回放Seek调试】URL窗口开始时间: ${TIME_FORMATTER.format(Instant.ofEpochMilli(playbackWindowStart))}")
                log.i("【回放Seek调试】窗口偏移量: ${windowOffset}ms")
                log.i("【回放Seek调试】实际Seek位置: ${actualSeekPosition}ms")
                
                videoPlayerState.seekTo(actualSeekPosition)
            }

            // 时移模式：没有具体节目信息的回放（直播时移）
            isInTimeShift() -> {
                val now = System.currentTimeMillis()
                val (windowStart, windowEnd) = currentTimelineRange()

                // 时移模式下，positionMs 是相对于当前窗口的偏移量
                // 计算目标时间戳
                val targetTime = windowStart + positionMs
                val offsetMs = now - targetTime

                if (offsetMs > 0) {
                    // 检查是否在有效时移范围内
                    val maxTimeshiftDurationMs = PlaybackUtil.getMaxTimeshiftDurationMs(currentChannelLine)
                    if (offsetMs > maxTimeshiftDurationMs) {
                        log.d("时移超出范围，跳到窗口最开始: offset=$offsetMs")
                        Snackbar.show("已跳到可回看的最早位置")
                        timeshiftTo(maxTimeshiftDurationMs)
                    } else if (offsetMs <= 3_000L) {
                        // 接近当前时间，切换回直播
                        _currentPlaybackEpgProgramme = null
                        clearPlaybackUrlCandidates()
                        videoPlayerState.stop()
                        videoPlayerState.exitPlaybackMode()
                        changeCurrentChannel(_currentChannel, _currentChannelLineIdx, forceReload = true)
                        Snackbar.show("已切换回直播")
                    } else {
                        timeshiftTo(offsetMs)
                    }
                } else {
                    log.d("无法跳到未来位置: offset=$offsetMs")
                }
            }

            // 非回放模式：不应该调用此函数
            else -> {
                log.d("seekToPosition 在非回放模式下被调用，忽略")
            }
        }
    }
}

@Composable
fun rememberMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: VideoPlayerStateNew = rememberVideoPlayerStateNew(),
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    favoriteChannelListProvider: () -> ChannelList = { ChannelList() },
    epgListProvider: () -> EpgList = { EpgList() },
    settingsViewModel: SettingsViewModel = settingsVM,
    systemVolumeProvider: () -> Float = { 1f },
): MainContentState {
    val favoriteChannelListProviderUpdated by rememberUpdatedState(favoriteChannelListProvider)
    val epgListProviderUpdated by rememberUpdatedState(epgListProvider)

    return remember(settingsVM.videoPlayerCore) {
        MainContentState(
            coroutineScope = coroutineScope,
            videoPlayerState = videoPlayerState,
            channelGroupListProvider = channelGroupListProvider,
            favoriteChannelListProvider = favoriteChannelListProviderUpdated,
            epgListProvider = epgListProviderUpdated,
            settingsViewModel = settingsViewModel,
            systemVolumeProvider = systemVolumeProvider,
        )
    }
}
