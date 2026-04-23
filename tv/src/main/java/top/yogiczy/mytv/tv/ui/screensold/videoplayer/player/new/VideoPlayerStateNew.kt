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
import java.util.concurrent.CopyOnWriteArrayList
import androidx.lifecycle.compose.LocalLifecycleOwner
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.VideoPlayerDisplayMode
import top.yogiczy.mytv.tv.ui.utils.Configs

private data class Tuple4<T1, T2, T3, T4>(
    val component1: T1,
    val component2: T2,
    val component3: T3,
    val component4: T4
)

private data class Tuple5<T1, T2, T3, T4, T5>(
    val component1: T1,
    val component2: T2,
    val component3: T3,
    val component4: T4,
    val component5: T5
)

private data class Tuple6<T1, T2, T3, T4, T5, T6>(
    val component1: T1,
    val component2: T2,
    val component3: T3,
    val component4: T4,
    val component5: T5,
    val component6: T6
)

private data class Tuple7<T1, T2, T3, T4, T5, T6, T7>(
    val component1: T1,
    val component2: T2,
    val component3: T3,
    val component4: T4,
    val component5: T5,
    val component6: T6,
    val component7: T7
)

private data class Tuple8<T1, T2, T3, T4, T5, T6, T7, T8>(
    val component1: T1,
    val component2: T2,
    val component3: T3,
    val component4: T4,
    val component5: T5,
    val component6: T6,
    val component7: T7,
    val component8: T8
)

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
        set(value) {
            _volume = value
            instance.setVolume(value)
        }
    
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
    private val onInterruptListeners = CopyOnWriteArrayList<() -> Unit>()
    internal val onIsBufferingListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
    
    fun onReady(listener: () -> Unit) {
        if (!isReleased) onReadyListeners.add(listener)
    }
    
    fun onError(listener: () -> Unit) {
        if (!isReleased) onErrorListeners.add(listener)
    }
    
    fun onInterrupt(listener: () -> Unit) {
        if (!isReleased) onInterruptListeners.add(listener)
    }
    
    fun onIsBuffering(listener: (Boolean) -> Unit) {
        if (!isReleased) onIsBufferingListeners.add(listener)
    }
    
    fun enterPlaybackMode(startTime: Long, endTime: Long) {
        isPlaybackMode = true
        playbackStartTime = startTime
        playbackEndTime = endTime
        instance.setPlaybackMode(true)
    }
    
    fun exitPlaybackMode() {
        isPlaybackMode = false
        playbackStartTime = 0L
        playbackEndTime = 0L
        instance.setPlaybackMode(false)
    }
    
    fun isCurrentMediaItemLive(): Boolean = !isPlaybackMode
    
    fun prepare(line: ChannelLine) {
        error = null
        metadata = PlayerMetadata()
        instance.prepare(line)
    }
    
    fun play() {
        instance.play()
    }
    
    fun pause() {
        instance.pause()
    }
    
    fun seekTo(position: Long) {
        instance.seekTo(position)
    }
    
    fun stop() {
        instance.stop()
    }
    
    fun selectVideoTrack(track: PlayerMetadata.VideoTrack?) {
        instance.selectVideoTrack(track)
    }
    
    fun selectAudioTrack(track: PlayerMetadata.AudioTrack?) {
        instance.selectAudioTrack(track)
    }
    
    fun selectSubtitleTrack(track: PlayerMetadata.SubtitleTrack?) {
        instance.selectSubtitleTrack(track)
    }
    
    fun setVideoSurfaceView(surfaceView: SurfaceView) {
        instance.setVideoSurfaceView(surfaceView)
    }
    
    fun setVideoTextureView(textureView: TextureView) {
        instance.setVideoTextureView(textureView)
    }
    
    fun updateMetadata(transform: (PlayerMetadata) -> PlayerMetadata) {
        val manager = instance.state as? VideoPlayerStateManager ?: return
        manager.updateMetadata(transform(manager.metadata.value))
    }

    fun initialize() {
        instance.initialize()
    }
    
    fun release() {
        if (isReleased) return
        isReleased = true
        
        onReadyListeners.clear()
        onErrorListeners.clear()
        onInterruptListeners.clear()
        onIsBufferingListeners.clear()
        
        instance.release()
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
            Configs.VideoPlayerCore.MEDIA3 -> Media3VideoPlayerNew(context, coroutineScope)
            Configs.VideoPlayerCore.IJK -> IjkVideoPlayerNew(context, coroutineScope)
            else -> Media3VideoPlayerNew(context, coroutineScope)
        }
        
        VideoPlayerStateNew(player, defaultDisplayModeProvider)
    }
    
    DisposableEffect(videoPlayerCore) {
        state.initialize()
        onDispose { state.release() }
    }

    LaunchedEffect(state) {
        state.instance.state.isPlaying
            .combine(state.instance.state.isBuffering) { isPlaying, isBuffering -> 
                Pair(isPlaying, isBuffering) 
            }
            .combine(state.instance.state.error) { (isPlaying, isBuffering), error -> 
                Triple(isPlaying, isBuffering, error) 
            }
            .combine(state.instance.state.duration) { (isPlaying, isBuffering, error), duration ->
                Tuple4(isPlaying, isBuffering, error, duration)
            }
            .combine(state.instance.state.currentPosition) { (isPlaying, isBuffering, error, duration), currentPosition ->
                Tuple5(isPlaying, isBuffering, error, duration, currentPosition)
            }
            .combine(state.instance.state.metadata) { (isPlaying, isBuffering, error, duration, currentPosition), metadata ->
                Tuple6(isPlaying, isBuffering, error, duration, currentPosition, metadata)
            }
            .combine(state.instance.state.isPlaybackMode) { (isPlaying, isBuffering, error, duration, currentPosition, metadata), isPlaybackMode ->
                Tuple7(isPlaying, isBuffering, error, duration, currentPosition, metadata, isPlaybackMode)
            }
            .combine(state.instance.state.playbackTimeRange) { (isPlaying, isBuffering, error, duration, currentPosition, metadata, isPlaybackMode), playbackTimeRange ->
                Tuple8(isPlaying, isBuffering, error, duration, currentPosition, metadata, isPlaybackMode, playbackTimeRange)
            }
            .combine(state.instance.state.volume) { (isPlaying, isBuffering, error, duration, currentPosition, metadata, isPlaybackMode, playbackTimeRange), volume ->
                StateBundle(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    error = error,
                    duration = duration,
                    currentPosition = currentPosition,
                    metadata = metadata,
                    isPlaybackMode = isPlaybackMode,
                    playbackTimeRange = playbackTimeRange,
                    volume = volume
                )
            }.collect { bundle ->
                state.isPlaying = bundle.isPlaying
                state.isBuffering = bundle.isBuffering
                state.onIsBufferingListeners.forEach { it(bundle.isBuffering) }
                
                state.error = bundle.error
                if (bundle.error != null) {
                    state.onErrorListeners.forEach { it.invoke() }
                }
                
                state.duration = bundle.duration
                state.currentPosition = bundle.currentPosition
                
                state.metadata = bundle.metadata
                if (bundle.metadata.video?.width != null && bundle.metadata.video.height != null) {
                    if (bundle.metadata.video.width > 0 && bundle.metadata.video.height > 0) {
                        state.aspectRatio = bundle.metadata.video.width.toFloat() / bundle.metadata.video.height
                    }
                }
                if (bundle.metadata.video != null) {
                    state.onReadyListeners.forEach { it.invoke() }
                    state.error = null
                    state.displayMode = state.defaultDisplayModeProvider()
                }
                
                state.isPlaybackMode = bundle.isPlaybackMode
                if (bundle.playbackTimeRange != null) {
                    state.playbackStartTime = bundle.playbackTimeRange.first
                    state.playbackEndTime = bundle.playbackTimeRange.second
                } else {
                    state.playbackStartTime = 0L
                    state.playbackEndTime = 0L
                }
                
                state._volume = bundle.volume
            }
    }
    
    data class StateBundle(
        val isPlaying: Boolean,
        val isBuffering: Boolean,
        val error: String?,
        val duration: Long,
        val currentPosition: Long,
        val metadata: PlayerMetadata,
        val isPlaybackMode: Boolean,
        val playbackTimeRange: Pair<Long, Long>?,
        val volume: Float
    )
    
    DisposableEffect(lifecycleOwner, videoPlayerCore) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.play()
            else if (event == Lifecycle.Event.ON_STOP) {
                if (!Configs.appPipEnable) state.pause()
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    return state
}
