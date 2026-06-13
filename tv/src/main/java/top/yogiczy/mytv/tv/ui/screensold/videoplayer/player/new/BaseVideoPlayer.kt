package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.utils.PlaybackUtil
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class PlaybackModeState(
    val isPlayback: Boolean = false,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
)

abstract class BaseVideoPlayer(
    protected val coroutineScope: CoroutineScope
) : IVideoPlayer {

    protected abstract val stateManager: VideoPlayerStateManager
    override val state: IVideoPlayerState get() = stateManager

    protected val playbackModeState = AtomicReference(PlaybackModeState())
    protected val pendingFadeIn = AtomicReference(false)
    protected val targetSystemVolume = AtomicReference(1f)
    protected val isReleased = AtomicBoolean(false)
    protected val isInitialized = AtomicBoolean(false)

    protected val audioTrackMemoryCache = AudioTrackCache.TrackIdCache(maxSize = 100)
    protected val audioTrackListCache = AudioTrackCache.TrackListCache()

    override fun setPlaybackMode(isPlayback: Boolean) {
        val currentState = playbackModeState.get()
        if (currentState.isPlayback != isPlayback) {
            playbackModeState.set(currentState.copy(isPlayback = isPlayback))
            stateManager.updatePlaybackMode(isPlayback, 0L, 0L)
        }
    }

    override fun isPlaybackMode(): Boolean = playbackModeState.get().isPlayback

    override fun getPlaybackTimeRange(): Pair<Long, Long>? {
        val currentState = playbackModeState.get()
        return if (currentState.isPlayback && currentState.startTime > 0 && currentState.endTime > 0) {
            Pair(currentState.startTime, currentState.endTime)
        } else {
            null
        }
    }

    override fun muteImmediate() {
        getVolumeFader().setVolumeImmediate(0f)
        pendingFadeIn.set(true)
    }

    override fun fadeInFromMute(systemVolume: Float) {
        getVolumeFader().setVolumeImmediate(0f)
        targetSystemVolume.set(systemVolume)
        pendingFadeIn.set(true)
    }

    protected fun updatePlaybackModeState(line: ChannelLine) {
        val urlIsPlayback = PlaybackUtil.isPlaybackUrl(line.url)

        val (startTime, endTime) = if (urlIsPlayback) {
            PlaybackUtil.extractPlaybackTimeRange(line.url) ?: Pair(0L, 0L)
        } else {
            Pair(0L, 0L)
        }

        playbackModeState.set(
            PlaybackModeState(
                isPlayback = urlIsPlayback,
                startTime = startTime,
                endTime = endTime,
            )
        )

        stateManager.updatePlaybackMode(urlIsPlayback, startTime, endTime)
    }

    protected fun loadAudioTrackMemory() {
        coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                val jsonString = Configs.channelAudioTrackMemory
                if (jsonString.isNotBlank()) {
                    val cache = AudioTrackCache.TrackIdCache.fromJsonString(jsonString)
                    audioTrackMemoryCache.fromMap(cache.toMap())
                }
            }
        }
    }

    protected fun saveAudioTrackMemory() {
        coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                Configs.channelAudioTrackMemory = audioTrackMemoryCache.toJsonString()
            }
        }
    }

    protected fun handlePendingFadeIn() {
        if (pendingFadeIn.getAndSet(false)) {
            getVolumeFader().syncCurrentVolume(0f)
            getVolumeFader().fadeIn(targetSystemVolume.get())
        }
    }

    protected abstract fun getVolumeFader(): VolumeFader

    override fun toggleLiveSubtitle() {
        // 默认空实现，子类（Media3VideoPlayerNew）可覆写
    }
}
