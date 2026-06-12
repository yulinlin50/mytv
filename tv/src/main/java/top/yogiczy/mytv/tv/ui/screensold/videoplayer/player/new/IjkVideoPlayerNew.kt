package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.text.Cue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.util.utils.toHeaders
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.IjkTrackInfoResolver
import top.yogiczy.mytv.tv.ui.utils.Configs
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import tv.danmaku.ijk.media.player.misc.ITrackInfo
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class IjkVideoPlayerNew(
    private val context: Context,
    coroutineScope: CoroutineScope
) : BaseVideoPlayer(coroutineScope) {
    
    private val log = Logger.create("IjkVideoPlayerNew")
    
    override val stateManager = VideoPlayerStateManager(coroutineScope)
    override val cues: StateFlow<List<Cue>> = stateManager.cues
    private val errorHandler = PlayerErrorHandler(stateManager, coroutineScope)
    
    private var player: IjkMediaPlayer? = null
    private val playerLock = Any()
    
    private var cachedSurface: Any? = null  // SurfaceView | Surface
    
    private var currentTextureView: TextureView? = null

    private var currentChannelLine = ChannelLine()
    
    private var positionUpdateJob: Job? = null
    private val volumeState = AtomicReference(1f)
    
    private val volumeFader = VolumeFader(coroutineScope) { volume ->
        volumeState.set(volume)
        player?.setVolume(volume, volume)
        stateManager.updateVolume(volume)
    }
    
    override fun getVolumeFader(): VolumeFader = volumeFader
    
    private val audioTrackState = AtomicReference(AudioTrackState())
    
    init {
        player = createPlayer()
        loadAudioTrackMemory()
    }
    
    override fun initialize() {
        if (isReleased.get() || !isInitialized.compareAndSet(false, true)) return
        
        player?.setOnPreparedListener(preparedListener)
        player?.setOnVideoSizeChangedListener(videoSizeChangedListener)
        player?.setOnErrorListener(errorListener)
        player?.setOnInfoListener(infoListener)
    }
    
    override fun release() {
        if (isReleased.getAndSet(true)) return
        
        positionUpdateJob?.cancel()
        positionUpdateJob = null
        
        synchronized(playerLock) {
            try {
                player?.setSurface(null)
            } catch (e: Exception) {
                log.e("Failed to set surface to null", e)
            }
            
            try {
                player?.stop()
            } catch (e: Exception) {
                log.e("Failed to stop player", e)
            }
            
            player?.setOnPreparedListener(null)
            player?.setOnVideoSizeChangedListener(null)
            player?.setOnErrorListener(null)
            player?.setOnInfoListener(null)
            
            try {
                player?.release()
            } catch (e: Exception) {
                log.e("Failed to release player", e)
            }
            player = null
        }
        
        (cachedSurface as? Surface)?.let { runCatching { it.release() } }
        cachedSurface = null
        currentTextureView = null
        
        stateManager.reset()
    }
    
    override fun prepare(line: ChannelLine) {
        if (isReleased.get()) return
        
        player?.reset()
        applyAllOptions()
        
        // 必须重新设置监听器：虽然 reset() 不会清除监听器，但 release() 会清除，
        // 而 initialize() 只执行一次，所以需要在 prepare() 中确保监听器被设置
        player?.setOnPreparedListener(preparedListener)
        player?.setOnVideoSizeChangedListener(videoSizeChangedListener)
        player?.setOnErrorListener(errorListener)
        player?.setOnInfoListener(infoListener)
        
        currentChannelLine = line
        
        audioTrackState.set(AudioTrackState())
        
        val trackId = audioTrackMemoryCache.get(line.playableUrl)
        audioTrackState.updateAndGet { it.copy(userSelectedTrackId = trackId) }
        
        updatePlaybackModeState(line)
        
        val urlToPlay = if (playbackModeState.get().isPlayback) {
            line.url
        } else {
            line.playableUrl
        }
        
        val baseHeaders = Configs.videoPlayerHeaders.toHeaders()
        val userAgent = baseHeaders["User-Agent"]
            ?: line.httpUserAgent
            ?: Configs.videoPlayerUserAgent
        val headers = if (baseHeaders.containsKey("User-Agent")) {
            baseHeaders
        } else {
            baseHeaders + mapOf("User-Agent" to userAgent)
        }
        player?.setDataSource(urlToPlay, headers)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user-agent", userAgent)
        
        setPlayerOptions()
        stateManager.updateIsBuffering(true)
        player?.prepareAsync()
    }
    
    override fun play() {
        if (isReleased.get()) return
        player?.start()
    }
    
    override fun pause() {
        if (isReleased.get()) return
        player?.pause()
    }
    
    override fun playWithFadeIn(systemVolume: Float) {
        if (isReleased.get()) return
        player?.setVolume(0f, 0f)
        volumeFader.syncCurrentVolume(0f)
        player?.start()
        volumeFader.fadeIn(systemVolume)
    }
    
    override fun pauseWithFadeOut() {
        if (isReleased.get()) return
        volumeFader.fadeOut {
            if (!isReleased.get()) {
                player?.pause()
            }
        }
    }
    
    override fun stop() {
        if (isReleased.get()) return
        player?.stop()
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }
    
    override fun seekTo(position: Long) {
        if (isReleased.get()) return
        player?.seekTo(position)
    }
    
    override fun setVolume(volume: Float) {
        if (isReleased.get()) return
        volumeFader.fadeTo(volume)
    }
    
    override fun getVolume(): Float = volumeState.get()
    
    override fun syncVolume(volume: Float) {
        volumeFader.syncCurrentVolume(volume)
    }
    
    override fun selectVideoTrack(track: PlayerMetadata.VideoTrack?) {
        // IJKPlayer视频轨道选择支持有限
    }
    
    override fun selectAudioTrack(track: PlayerMetadata.AudioTrack?) {
        if (isReleased.get()) return
        
        audioTrackState.updateAndGet { currentState ->
            currentState.copy(userSelectedTrackId = track?.trackId)
        }
        
        if (track?.trackId != null) {
            audioTrackMemoryCache.put(currentChannelLine.playableUrl, track.trackId)
            saveAudioTrackMemory()
        } else {
            audioTrackMemoryCache.remove(currentChannelLine.playableUrl)
            saveAudioTrackMemory()
        }
        
        if (track?.index == null) {
            val currentStreamIndex = player?.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO) ?: -1
            if (currentStreamIndex >= 0) {
                runCatching { player?.deselectTrack(currentStreamIndex) }
                    .onFailure { log.w("Failed to deselect audio track", it) }
            }
            return
        }
        
        val currentState = audioTrackState.get()
        val candidate = currentState.candidates.getOrNull(track.index) ?: return
        
        val currentStreamIndex = player?.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO) ?: -1
        runCatching {
            if (currentStreamIndex >= 0 && currentStreamIndex != candidate.streamIndex) {
                player?.deselectTrack(currentStreamIndex)
            }
            player?.selectTrack(candidate.streamIndex)
        }.onFailure { log.w("Failed to select audio track", it) }
        
        audioTrackState.updateAndGet { state ->
            val updatedCandidates = state.candidates.map { c ->
                c.copy(metadata = c.metadata.copy(isSelected = c.streamIndex == candidate.streamIndex))
            }
            state.copy(candidates = updatedCandidates)
        }
        
        val updatedAudioTracks = stateManager.metadata.value.audioTracks.map { 
            it.copy(isSelected = it.index == track.index) 
        }
        val updatedAudio = candidate.metadata.copy(isSelected = true)
        
        stateManager.updateMetadata(
            stateManager.metadata.value.copy(
                audio = updatedAudio,
                audioTracks = updatedAudioTracks
            )
        )
    }
    
    override fun selectSubtitleTrack(track: PlayerMetadata.SubtitleTrack?) {
        // IJKPlayer字幕支持有限
    }
    
    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        (cachedSurface as? Surface)?.let { runCatching { it.release() } }
        cachedSurface = surfaceView
        if (!isReleased.get()) {
            runCatching { player?.setDisplay(surfaceView.holder) }
        }
    }
    
    override fun setVideoTextureView(textureView: TextureView) {
        currentTextureView?.surfaceTextureListener = null
        (cachedSurface as? Surface)?.let { runCatching { it.release() } }
        cachedSurface = null
        currentTextureView = textureView
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, w: Int, h: Int) {
                Surface(surfaceTexture).also { cachedSurface = it; player?.setSurface(it) }
            }
            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                if (!isReleased.get()) runCatching { player?.setSurface(null) }
                (cachedSurface as? Surface)?.let { runCatching { it.release() } }
                cachedSurface = null
                return true
            }
            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
        }
    }
    
    private fun createPlayer(): IjkMediaPlayer {
        return IjkMediaPlayer().apply {
            applyAllOptions()
        }
    }
    
    private fun applyAllOptions() {
        (FORMAT_OPTIONS + PLAYER_OPTIONS).forEach { opt ->
            if (opt.longValue != null) player?.setOption(opt.category, opt.key, opt.longValue)
            else if (opt.strValue != null) player?.setOption(opt.category, opt.key, opt.strValue)
        }
    }
    
    private fun setPlayerOptions() {
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", Configs.videoPlayerLoadTimeout)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rw_timeout", Configs.videoPlayerLoadTimeout)
    }
    
    private val infoListener = IMediaPlayer.OnInfoListener { _, what, _ ->
        if (isReleased.get()) return@OnInfoListener true
        when (what) {
            IMediaPlayer.MEDIA_INFO_BUFFERING_START -> stateManager.updateIsBuffering(true)
            IMediaPlayer.MEDIA_INFO_BUFFERING_END -> stateManager.updateIsBuffering(false)
        }
        true
    }

    private val preparedListener = IMediaPlayer.OnPreparedListener { mp ->
        if (isReleased.get()) return@OnPreparedListener
        when (val s = cachedSurface) {
            is SurfaceView -> mp.setDisplay(s.holder)
            is Surface -> mp.setSurface(s)
        }
        
        parseAudioTracks()
        restoreAudioTrack()
        
        val info = mp.mediaInfo
        if (info != null) {
            val currentAudioMeta = buildCurrentAudioMetadata(info)
            stateManager.updateMetadata(
                PlayerMetadata(
                    video = PlayerMetadata.VideoTrack(
                        width = info.mMeta.mVideoStream?.mWidth,
                        height = info.mMeta.mVideoStream?.mHeight,
                        frameRate = info.mMeta.mVideoStream?.mFpsNum?.toFloat(),
                        bitrate = info.mMeta.mVideoStream?.mBitrate?.toInt(),
                        mimeType = info.mMeta.mVideoStream?.mCodecName,
                        decoder = info.mVideoDecoderImpl
                    ),
                    audio = currentAudioMeta,
                    audioTracks = audioTrackState.get().candidates.map { it.metadata }
                )
            )
        } else {
            stateManager.updateMetadata(
                stateManager.metadata.value.copy(
                    audioTracks = audioTrackState.get().candidates.map { it.metadata }
                )
            )
        }
        
        stateManager.updateError(null)
        errorHandler.resetRetryCount()
        stateManager.updateIsBuffering(false)
        stateManager.updateDuration(mp.duration)
        
        startPositionUpdate()
        
        handlePendingFadeIn()
    }
    
    private val videoSizeChangedListener = IMediaPlayer.OnVideoSizeChangedListener { mp, width, height, sarNum, sarDen ->
        if (isReleased.get()) return@OnVideoSizeChangedListener
        stateManager.updateMetadata(
            stateManager.metadata.value.copy(
                video = stateManager.metadata.value.video?.copy(
                    width = width,
                    height = height
                )
            )
        )
    }
    
    private val errorListener = IMediaPlayer.OnErrorListener { _, what, extra ->
        if (isReleased.get()) return@OnErrorListener true
        errorHandler.handleIjkError(what, extra)
        true
    }
    
    private fun parseAudioTracks() {
        val cachedTracks = audioTrackListCache.get(currentChannelLine.playableUrl)
        if (cachedTracks != null && cachedTracks.isNotEmpty()) {
            val candidates = cachedTracks.mapIndexed { index, track ->
                AudioTrackCandidate(
                    metadata = track,
                    streamIndex = track.index ?: index
                )
            }
            audioTrackState.updateAndGet { it.copy(candidates = candidates) }
            return
        }
        
        val trackInfos = runCatching { player?.trackInfo }.getOrNull() ?: return
        val selectedAudioStreamIndex = player?.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO) ?: -1
        
        val candidates = IjkTrackInfoResolver.resolveAudioTracks(
            trackInfos = trackInfos,
            selectedAudioStreamIndex = selectedAudioStreamIndex,
            sortMode = Configs.audioTrackSortMode
        )
        
        if (candidates.isNotEmpty()) {
            audioTrackListCache.put(currentChannelLine.playableUrl, candidates.map { it.metadata })
        }
        
        audioTrackState.updateAndGet { it.copy(candidates = candidates) }
    }
    
    private fun restoreAudioTrack() {
        val currentState = audioTrackState.get()
        if (currentState.restored || currentState.candidates.isEmpty()) return
        
        audioTrackState.updateAndGet { it.copy(restored = true) }
        
        val trackIdToRestore = currentState.userSelectedTrackId ?: return
        val candidate = currentState.candidates.find { trackIdToRestore in it.matchKeys } ?: return
        
        if (candidate.metadata.isSelected != true) {
            val currentStreamIndex = player?.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO) ?: -1
            runCatching {
                if (currentStreamIndex >= 0 && currentStreamIndex != candidate.streamIndex) {
                    player?.deselectTrack(currentStreamIndex)
                }
                player?.selectTrack(candidate.streamIndex)
            }

            audioTrackState.updateAndGet { state ->
                val updatedCandidates = state.candidates.map { c ->
                    c.copy(metadata = c.metadata.copy(isSelected = c.streamIndex == candidate.streamIndex))
                }
                state.copy(candidates = updatedCandidates)
            }
        }
    }
    
    private fun buildCurrentAudioMetadata(info: tv.danmaku.ijk.media.player.MediaInfo): PlayerMetadata.AudioTrack {
        val selectedCandidate = audioTrackState.get().candidates.firstOrNull { it.metadata.isSelected == true }
        if (selectedCandidate != null) {
            return selectedCandidate.metadata
        }
        
        return PlayerMetadata.AudioTrack(
            channels = IjkTrackInfoResolver.getChannelCount(info.mMeta.mAudioStream?.mChannelLayout ?: 0),
            channelsLabel = IjkTrackInfoResolver.getChannelLabel(info.mMeta.mAudioStream?.mChannelLayout ?: 0),
            sampleRate = info.mMeta.mAudioStream?.mSampleRate,
            bitrate = info.mMeta.mAudioStream?.mBitrate?.toInt(),
            mimeType = info.mMeta.mAudioStream?.mCodecName,
            decoder = info.mAudioDecoderImpl
        )
    }
    
    private fun startPositionUpdate() {
        if (isReleased.get()) return
        positionUpdateJob?.cancel()
        positionUpdateJob = coroutineScope.launch {
            var retryAudioTracks = true
            while (isActive && !isReleased.get()) {
                synchronized(playerLock) {
                    if (!isReleased.get()) {
                        player?.let { p ->
                            stateManager.updateIsPlaying(p.isPlaying)
                            stateManager.updateCurrentPosition(p.currentPosition)
                        }
                    }
                }
                if (retryAudioTracks && stateManager.metadata.value.audioTracks.isEmpty()) {
                    synchronized(playerLock) {
                        if (!isReleased.get()) {
                            val trackInfos = runCatching { player?.trackInfo }.getOrNull()
                            if (trackInfos != null && trackInfos.isNotEmpty()) {
                                parseAudioTracks()
                                restoreAudioTrack()
                                stateManager.updateMetadata(
                                    stateManager.metadata.value.copy(
                                        audioTracks = audioTrackState.get().candidates.map { it.metadata }
                                    )
                                )
                                retryAudioTracks = false
                            }
                        }
                    }
                }
                delay(500)
            }
        }
    }
    
    private data class AudioTrackState(
        val candidates: List<AudioTrackCandidate> = emptyList(),
        val userSelectedTrackId: String? = null,
        val restored: Boolean = false
    )
    
    private companion object {
        private data class IjkOption(val category: Int, val key: String, val longValue: Long? = null, val strValue: String? = null) {
            constructor(category: Int, key: String, value: Long) : this(category, key, longValue = value)
            constructor(category: Int, key: String, value: String) : this(category, key, strValue = value)
        }

        private val FORMAT_OPTIONS = listOf(
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 0L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", 600L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 5L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_at_eof", 1L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_streamed", 1L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_delay_max", 5L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzemaxduration", 200L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1000000L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", (1024 * 512).toLong()),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek+flush_packets"),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "max_delay", 500000L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "max_buffer_size", (1024 * 1024).toLong()),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "tcp_nodelay", 1L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "auto_convert", 1L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "metadata", 1L),
        )

        private val PLAYER_OPTIONS = listOf(
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", 1L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", 1L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-avc", 1L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 10L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "videotoolbox", 1L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "videotoolbox-max-frame-width", 1920L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "videotoolbox-max-frame-height", 1080L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-fps", 60L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 50L),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", (1024 * 1024 * 2).toLong()),
            IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", android.graphics.PixelFormat.RGBA_8888.toLong().toString()),
        )
    }
}
