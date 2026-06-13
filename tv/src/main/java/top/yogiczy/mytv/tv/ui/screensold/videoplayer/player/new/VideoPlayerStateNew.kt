package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import androidx.lifecycle.compose.LocalLifecycleOwner
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.VideoPlayerDisplayMode
import top.yogiczy.mytv.tv.ui.utils.Configs

@Stable
class VideoPlayerStateNew(
    val instance: IVideoPlayer,
    internal var defaultDisplayModeProvider: () -> VideoPlayerDisplayMode = { VideoPlayerDisplayMode.ORIGINAL }
) {
    private var isReleased = false
    
    var displayMode by mutableStateOf(defaultDisplayModeProvider())
    var aspectRatio by mutableFloatStateOf(16f / 9f)
    var error: String? by mutableStateOf(null)
        internal set
    var isBuffering: Boolean by mutableStateOf(false)
        internal set
    var isPlaying: Boolean by mutableStateOf(false)
        internal set
    var duration: Long by mutableLongStateOf(0L)
        internal set
    var currentPosition: Long by mutableLongStateOf(0L)
        internal set
    
    internal var _volume: Float by mutableFloatStateOf(1f)
    var volume: Float
        get() = _volume
        set(value) { _volume = value; instance.setVolume(value) }
    
    var metadata: PlayerMetadata by mutableStateOf(PlayerMetadata())
        internal set
    var isPlaybackMode: Boolean by mutableStateOf(false)
        internal set
    var playbackStartTime: Long by mutableLongStateOf(0L)
        internal set
    var playbackEndTime: Long by mutableLongStateOf(0L)
        internal set
    
    internal val onReadyListeners = CopyOnWriteArrayList<() -> Unit>()
    internal val onErrorListeners = CopyOnWriteArrayList<() -> Unit>()
    internal val onIsBufferingListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
    
    fun onReady(listener: () -> Unit) { if (!isReleased) onReadyListeners.add(listener) }
    fun onError(listener: () -> Unit) { if (!isReleased) onErrorListeners.add(listener) }
    fun onIsBuffering(listener: (Boolean) -> Unit) { if (!isReleased) onIsBufferingListeners.add(listener) }
    
    fun enterPlaybackMode(startTime: Long, endTime: Long) {
        isPlaybackMode = true; playbackStartTime = startTime; playbackEndTime = endTime
        instance.setPlaybackMode(true)
    }
    
    fun exitPlaybackMode() {
        isPlaybackMode = false; playbackStartTime = 0L; playbackEndTime = 0L
        instance.setPlaybackMode(false)
    }
    
    fun isCurrentMediaItemLive(): Boolean = !isPlaybackMode
    
    fun prepare(line: ChannelLine) { error = null; metadata = PlayerMetadata(); instance.prepare(line) }
    fun play() { instance.play() }
    fun pause() { instance.pause() }
    fun playWithFadeIn(systemVolume: Float) { instance.playWithFadeIn(systemVolume) }
    fun pauseWithFadeOut() { instance.pauseWithFadeOut() }
    fun seekTo(position: Long) { instance.seekTo(position) }
    fun stop() { instance.stop() }
    fun muteImmediate() { instance.muteImmediate() }
    fun fadeInFromMute(systemVolume: Float) { instance.fadeInFromMute(systemVolume) }
    fun selectVideoTrack(track: PlayerMetadata.VideoTrack?) { instance.selectVideoTrack(track) }
    fun selectAudioTrack(track: PlayerMetadata.AudioTrack?) { instance.selectAudioTrack(track) }
    fun selectSubtitleTrack(track: PlayerMetadata.SubtitleTrack?) { instance.selectSubtitleTrack(track) }
    fun toggleLiveSubtitle() { instance.toggleLiveSubtitle() }
    fun setVideoSurfaceView(surfaceView: SurfaceView) { instance.setVideoSurfaceView(surfaceView) }
    fun setVideoTextureView(textureView: TextureView) { instance.setVideoTextureView(textureView) }
    
    fun updateMetadata(transform: (PlayerMetadata) -> PlayerMetadata) {
        val manager = instance.state as? VideoPlayerStateManager ?: return
        manager.updateMetadata(transform(manager.metadata.value))
    }

    fun initialize() {
        instance.initialize()
        VideoPlayerVolumeManager.register(instance)
    }
    
    fun release() {
        VideoPlayerVolumeManager.unregister(instance)
        if (isReleased) return
        isReleased = true
        instance.release()
        onReadyListeners.clear(); onErrorListeners.clear()
        onIsBufferingListeners.clear()
    }
}

@Composable
fun rememberVideoPlayerStateNew(
    defaultDisplayModeProvider: () -> VideoPlayerDisplayMode = { VideoPlayerDisplayMode.ORIGINAL }
): VideoPlayerStateNew {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    val videoPlayerCore = settingsVM.videoPlayerCore
    val state = remember(videoPlayerCore) {
        val player: IVideoPlayer = when (videoPlayerCore) {
            Configs.VideoPlayerCore.IJK -> IjkVideoPlayerNew(context, coroutineScope)
            else -> Media3VideoPlayerNew(context, coroutineScope)
        }
        VideoPlayerStateNew(player, defaultDisplayModeProvider)
    }
    
    DisposableEffect(videoPlayerCore) {
        state.initialize()
        onDispose { state.release() }
    }

    // 用 collectAsState 替代手动 LaunchedEffect + launch，大幅减少样板代码
    LaunchedEffect(state) {
        launch { state.instance.state.isPlaying.collect { state.isPlaying = it } }
        launch {
            state.instance.state.isBuffering.collect {
                state.isBuffering = it
                state.onIsBufferingListeners.forEach { l -> l(it) }
            }
        }
        launch {
            state.instance.state.error.collect {
                state.error = it
                if (it != null) state.onErrorListeners.forEach { l -> l() }
            }
        }
        launch { state.instance.state.duration.collect { state.duration = it } }
        launch { state.instance.state.currentPosition.collect { state.currentPosition = it } }
        launch {
            state.instance.state.metadata.collect { meta ->
                state.metadata = meta
                meta.video?.let { v ->
                    if (v.width != null && v.height != null && v.width > 0 && v.height > 0) {
                        state.aspectRatio = v.width.toFloat() / v.height
                    }
                    state.onReadyListeners.forEach { l -> l() }
                    state.error = null
                    state.displayMode = state.defaultDisplayModeProvider()
                }
            }
        }
        launch { state.instance.state.isPlaybackMode.collect { state.isPlaybackMode = it } }
        launch {
            state.instance.state.playbackTimeRange.collect {
                state.playbackStartTime = it?.first ?: 0L
                state.playbackEndTime = it?.second ?: 0L
            }
        }
        launch { state.instance.state.volume.collect { state._volume = it } }
    }
    
    DisposableEffect(lifecycleOwner, videoPlayerCore) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> state.play()
                Lifecycle.Event.ON_STOP -> { if (!Configs.appPipEnable) state.pause() }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    return state
}
