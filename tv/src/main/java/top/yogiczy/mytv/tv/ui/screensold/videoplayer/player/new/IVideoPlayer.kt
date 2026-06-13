package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.text.Cue
import kotlinx.coroutines.flow.StateFlow
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine

interface IVideoPlayer {
    val state: IVideoPlayerState
    
    fun initialize()
    fun release()
    
    fun prepare(line: ChannelLine)
    fun play()
    fun pause()
    fun playWithFadeIn(systemVolume: Float)
    fun pauseWithFadeOut()
    fun stop()
    fun seekTo(position: Long)
    
    fun setVolume(volume: Float)
    fun getVolume(): Float
    fun syncVolume(volume: Float)
    fun muteImmediate()
    fun fadeInFromMute(systemVolume: Float)
    
    fun selectVideoTrack(track: PlayerMetadata.VideoTrack?)
    fun selectAudioTrack(track: PlayerMetadata.AudioTrack?)
    fun selectSubtitleTrack(track: PlayerMetadata.SubtitleTrack?)
    
    fun setVideoSurfaceView(surfaceView: SurfaceView)
    fun setVideoTextureView(textureView: TextureView)
    
    fun setPlaybackMode(isPlayback: Boolean)
    fun isPlaybackMode(): Boolean
    fun getPlaybackTimeRange(): Pair<Long, Long>?

    fun toggleLiveSubtitle()

    val cues: StateFlow<List<Cue>>
}

interface IVideoPlayerState {
    val isPlaying: StateFlow<Boolean>
    val isBuffering: StateFlow<Boolean>
    val error: StateFlow<String?>
    val duration: StateFlow<Long>
    val currentPosition: StateFlow<Long>
    val metadata: StateFlow<PlayerMetadata>
    val isPlaybackMode: StateFlow<Boolean>
    val playbackTimeRange: StateFlow<Pair<Long, Long>?>
    val volume: StateFlow<Float>
}
