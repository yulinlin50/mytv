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
    
    private var cacheSurfaceView: SurfaceView? = null
    private var cacheSurfaceTexture: Surface? = null
    
    private var currentChannelLine = ChannelLine()
    
    private val jobs = mutableListOf<Job>()
    private val jobsLock = Any()
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
        
        synchronized(jobsLock) {
            jobs.forEach { it.cancel() }
            jobs.clear()
        }
        
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
        
        try {
            cacheSurfaceTexture?.release()
        } catch (e: Exception) {
            log.e("Failed to release surface texture", e)
        }
        cacheSurfaceTexture = null
        cacheSurfaceView = null
        
        stateManager.reset()
    }
    
    override fun prepare(line: ChannelLine) {
        if (isReleased.get()) return
        
        player?.reset()
        
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
        synchronized(jobsLock) {
            jobs.forEach { it.cancel() }
            jobs.clear()
        }
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
        cacheSurfaceView = surfaceView
        cacheSurfaceTexture?.let { 
            runCatching { it.release() }
                .onFailure { log.w("Failed to release surface texture", it) }
        }
        cacheSurfaceTexture = null
        
        if (!isReleased.get()) {
            runCatching { player?.setDisplay(surfaceView.holder) }
                .onFailure { log.w("Failed to set display", it) }
        }
    }
    
    override fun setVideoTextureView(textureView: TextureView) {
        cacheSurfaceView = null
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                cacheSurfaceTexture = Surface(surfaceTexture)
                player?.setSurface(cacheSurfaceTexture)
            }
            
            override fun onSurfaceTextureSizeChanged(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int
            ) {}
            
            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                if (!isReleased.get()) {
                    runCatching { player?.setSurface(null) }
                }
                cacheSurfaceTexture?.let { runCatching { it.release() } }
                cacheSurfaceTexture = null
                return true
            }
            
            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
        }
    }
    
    private fun createPlayer(): IjkMediaPlayer {
        return IjkMediaPlayer().apply {
            (FORMAT_OPTIONS + PLAYER_OPTIONS).forEach { (key, category, value) ->
                setOption(category, key, value)
            }
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
        cacheSurfaceView?.let { mp.setDisplay(it.holder) }
        cacheSurfaceTexture?.let { mp.setSurface(it) }
        
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
            
            if (runCatching { player?.selectTrack(candidate.streamIndex) }.isSuccess) {
                audioTrackState.updateAndGet { state ->
                    val updatedCandidates = state.candidates.map { c ->
                        c.copy(metadata = c.metadata.copy(isSelected = c.streamIndex == candidate.streamIndex))
                    }
                    state.copy(candidates = updatedCandidates)
                }
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
        val job = coroutineScope.launch {
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
        synchronized(jobsLock) {
            jobs.forEach { it.cancel() }
            jobs.clear()
            if (!isReleased.get()) {
                jobs.add(job)
            } else {
                job.cancel()
            }
        }
    }
    
    private data class AudioTrackState(
        val candidates: List<AudioTrackCandidate> = emptyList(),
        val userSelectedTrackId: String? = null,
        val restored: Boolean = false
    )
    
    private companion object {
        private val FORMAT_OPTIONS: List<Triple<String, String, Any>> = listOf(
            Triple("dns_cache_clear", IjkMediaPlayer.OPT_CATEGORY_FORMAT, 0L),
            Triple("dns_cache_timeout", IjkMediaPlayer.OPT_CATEGORY_FORMAT, 600L),
            Triple("http-detect-range-support", IjkMediaPlayer.OPT_CATEGORY_FORMAT, 0L),
            Triple("reconnect", IjkMediaPlayer.OPT_CATEGORY_FORMAT, 5L),
            Triple("reconnect_at_eof", IjkMediaPlayer.OPT_CATEGORY_FORMAT, 1L),
            Triple("reconnect_streamed", IjkMediaPlayer.OPT_CATEGORY_FORMAT, 1L),
            Triple("reconnect_delay_max", IjkMediaPlayer.OPT_CATEGORY_FORMAT, 5L),
            Triple("analyzemaxduration", IjkMediaPlayer.OPT_CATEGORY_FORMAT, 200L),
            Triple("analyzeduration", IjkMediaPlayer.OPT_CATEGORY_FORMAT, 1000000L),
            Triple("probesize", IjkMediaPlayer.OPT_CATEGORY_FORMAT, (1024 * 512).toLong()),
            Triple("fflags", IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fastseek+flush_packets"),
            Triple("flush_packets", IjkMediaPlayer.OPT_CATEGORY_FORMAT, 1L),
            Triple("max_delay", IjkMediaPlayer.OPT_CATEGORY_FORMAT, 500000L),
            Triple("max_buffer_size", IjkMediaPlayer.OPT_CATEGORY_FORMAT, (1024 * 1024).toLong()),
            Triple("tcp_nodelay", IjkMediaPlayer.OPT_CATEGORY_FORMAT, 1L),
            Triple("auto_convert", IjkMediaPlayer.OPT_CATEGORY_FORMAT, 1L),
            Triple("metadata", IjkMediaPlayer.OPT_CATEGORY_FORMAT, 1L),
        )
        
        private val PLAYER_OPTIONS: List<Triple<String, String, Any>> = listOf(
            Triple("mediacodec", IjkMediaPlayer.OPT_CATEGORY_PLAYER, 1L),
            Triple("mediacodec-all-videos", IjkMediaPlayer.OPT_CATEGORY_PLAYER, 1L),
            Triple("mediacodec-hevc", IjkMediaPlayer.OPT_CATEGORY_PLAYER, 1L),
            Triple("mediacodec-avc", IjkMediaPlayer.OPT_CATEGORY_PLAYER, 1L),
            Triple("mediacodec-auto-rotate", IjkMediaPlayer.OPT_CATEGORY_PLAYER, 1L),
            Triple("mediacodec-handle-resolution-change", IjkMediaPlayer.OPT_CATEGORY_PLAYER, 1L),
            Triple("opensles", IjkMediaPlayer.OPT_CATEGORY_PLAYER, 0L),
            Triple("framedrop", IjkMediaPlayer.OPT_CATEGORY_PLAYER, 10L),
            Triple("start-on-prepared", IjkMediaPlayer.OPT_CATEGORY_PLAYER, 1L),
            Triple("enable-accurate-seek", IjkMediaPlayer.OPT_CATEGORY_PLAYER, 1L),
            Triple("videotoolbox", IjkMediaPlayer.OPT_CATEGORY_PLAYER, 1L),
            Triple("videotoolbox-max-frame-width", IjkMediaPlayer.OPT_CATEGORY_PLAYER, 1920L),
            Triple("videotoolbox-max-frame-height", IjkMediaPlayer.OPT_CATEGORY_PLAYER, 1080L),
            Triple("max-fps", IjkMediaPlayer.OPT_CATEGORY_PLAYER, 60L),
            Triple("min-frames", IjkMediaPlayer.OPT_CATEGORY_PLAYER, 50L),
            Triple("max-buffer-size", IjkMediaPlayer.OPT_CATEGORY_PLAYER, (1024 * 1024 * 2).toLong()),
            Triple("overlay-format", IjkMediaPlayer.OPT_CATEGORY_PLAYER, android.graphics.PixelFormat.RGBA_8888.toLong().toString()),
        )
    }
}
