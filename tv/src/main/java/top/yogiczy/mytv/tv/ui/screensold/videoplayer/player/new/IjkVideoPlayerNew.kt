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
import top.yogiczy.mytv.core.data.utils.PlaybackUtil
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
    private val coroutineScope: CoroutineScope
) : IVideoPlayer {
    
    private val log = Logger.create("IjkVideoPlayerNew")
    
    override val state = VideoPlayerStateManager(coroutineScope)
    override val cues: StateFlow<List<Cue>> = state.cues
    private val errorHandler = PlayerErrorHandler(state, coroutineScope)
    private val audioTrackMemoryCache = AudioTrackMemoryCache(maxSize = 100)
    
    private var player: IjkMediaPlayer? = null
    private val playerLock = Any()
    
    private var cacheSurfaceView: SurfaceView? = null
    private var cacheSurfaceTexture: Surface? = null
    
    private var currentChannelLine = ChannelLine()
    
    private val jobs = mutableListOf<Job>()
    private val jobsLock = Any()
    private val isReleased = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    
    private val playbackModeState = AtomicReference(PlaybackModeState())
    private val volumeState = AtomicReference(1f)
    
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
            runCatching { player?.setSurface(null) }
            runCatching { player?.stop() }
            
            player?.setOnPreparedListener(null)
            player?.setOnVideoSizeChangedListener(null)
            player?.setOnErrorListener(null)
            player?.setOnInfoListener(null)
            
            runCatching { player?.release() }
            player = null
        }
        
        cacheSurfaceTexture?.let { runCatching { it.release() } }
        cacheSurfaceTexture = null
        cacheSurfaceView = null
        
        state.reset()
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
        audioTrackState.set(audioTrackState.get().copy(userSelectedTrackId = trackId))
        
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
        state.updateIsBuffering(true)
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
        if (position < 0) return
        player?.seekTo(position)
    }
    
    override fun setVolume(volume: Float) {
        if (isReleased.get()) return
        val v = volume.coerceIn(0f, 1f)
        volumeState.set(v)
        player?.setVolume(v, v)
        state.updateVolume(v)
    }
    
    override fun getVolume(): Float = volumeState.get()
    
    override fun selectVideoTrack(track: PlayerMetadata.VideoTrack?) {
        // IJKPlayer视频轨道选择支持有限
    }
    
    override fun selectAudioTrack(track: PlayerMetadata.AudioTrack?) {
        if (isReleased.get()) return
        val currentState = audioTrackState.get()
        audioTrackState.set(currentState.copy(userSelectedTrackId = track?.trackId))
        
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
            }
            return
        }
        
        val candidate = currentState.candidates.getOrNull(track.index) ?: return
        
        val currentStreamIndex = player?.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO) ?: -1
        runCatching {
            if (currentStreamIndex >= 0 && currentStreamIndex != candidate.streamIndex) {
                player?.deselectTrack(currentStreamIndex)
            }
            player?.selectTrack(candidate.streamIndex)
        }
        
        val updatedCandidates = currentState.candidates.map { c ->
            c.copy(metadata = c.metadata.copy(isSelected = c.streamIndex == candidate.streamIndex))
        }
        audioTrackState.set(currentState.copy(candidates = updatedCandidates))
        
        val updatedAudioTracks = state.metadata.value.audioTracks.map { 
            it.copy(isSelected = it.index == track.index) 
        }
        val updatedAudio = candidate.metadata.copy(isSelected = true)
        
        state.updateMetadata(
            state.metadata.value.copy(
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
        cacheSurfaceTexture?.let { runCatching { it.release() } }
        cacheSurfaceTexture = null
        
        if (!isReleased.get()) {
            runCatching { player?.setDisplay(surfaceView.holder) }
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
    
    override fun setPlaybackMode(isPlayback: Boolean) {
        val currentState = playbackModeState.get()
        if (currentState.isPlayback != isPlayback) {
            playbackModeState.set(
                currentState.copy(
                    isPlayback = isPlayback,
                    manuallySet = true,
                    startTime = 0L,
                    endTime = 0L
                )
            )
            state.updatePlaybackMode(isPlayback, 0L, 0L)
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
    
    private fun createPlayer(): IjkMediaPlayer {
        return IjkMediaPlayer().apply {
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", 600)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 5)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_at_eof", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_streamed", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_delay_max", 5)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", Configs.videoPlayerLoadTimeout)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzemaxduration", 200L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1000000)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 1024 * 512)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek+flush_packets")
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "max_delay", 500000)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "max_buffer_size", 1024 * 1024)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "tcp_nodelay", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rw_timeout", Configs.videoPlayerLoadTimeout)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "auto_convert", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "metadata", 1)
        }
    }
    
    private fun setPlayerOptions() {
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", 1)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", 1)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-avc", 1)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 10)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "videotoolbox", 1)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "videotoolbox-max-frame-width", 1920)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "videotoolbox-max-frame-height", 1080)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-fps", 60)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", android.graphics.PixelFormat.RGBA_8888.toLong().toString())
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 50)
        player?.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 1024 * 2)
    }
    
    private fun updatePlaybackModeState(line: ChannelLine) {
        val urlIsPlayback = PlaybackUtil.isPlaybackUrl(line.url)
        val lineSupportsPlayback = line.hasCatchupSupport()
        
        val currentState = playbackModeState.get()
        val newIsPlayback = when {
            currentState.manuallySet && currentState.isPlayback && (urlIsPlayback || lineSupportsPlayback) -> true
            currentState.manuallySet && currentState.isPlayback && !urlIsPlayback && !lineSupportsPlayback -> false
            !currentState.manuallySet && urlIsPlayback -> true
            else -> false
        }
        
        val (startTime, endTime) = if (newIsPlayback) {
            PlaybackUtil.extractPlaybackTimeRange(line.url) ?: Pair(0L, 0L)
        } else {
            Pair(0L, 0L)
        }
        
        playbackModeState.set(
            PlaybackModeState(
                isPlayback = newIsPlayback,
                startTime = startTime,
                endTime = endTime,
                manuallySet = currentState.manuallySet
            )
        )
        
        state.updatePlaybackMode(newIsPlayback, startTime, endTime)
    }
    
    private fun loadAudioTrackMemory() {
        coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                val jsonString = Configs.channelAudioTrackMemory
                if (jsonString.isNotBlank()) {
                    val cache = AudioTrackMemoryCache.fromJsonString(jsonString)
                    audioTrackMemoryCache.fromMap(cache.toMap())
                }
            }
        }
    }
    
    private fun saveAudioTrackMemory() {
        coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                Configs.channelAudioTrackMemory = audioTrackMemoryCache.toJsonString()
            }
        }
    }
    
    private val infoListener = IMediaPlayer.OnInfoListener { _, what, _ ->
        if (isReleased.get()) return@OnInfoListener true
        when (what) {
            IMediaPlayer.MEDIA_INFO_BUFFERING_START -> state.updateIsBuffering(true)
            IMediaPlayer.MEDIA_INFO_BUFFERING_END -> state.updateIsBuffering(false)
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
            state.updateMetadata(
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
            state.updateMetadata(
                state.metadata.value.copy(
                    audioTracks = audioTrackState.get().candidates.map { it.metadata }
                )
            )
        }
        
        state.updateError(null)
        state.updateIsBuffering(false)
        state.updateDuration(mp.duration)
        
        startPositionUpdate()
    }
    
    private val videoSizeChangedListener = IMediaPlayer.OnVideoSizeChangedListener { mp, width, height, sarNum, sarDen ->
        if (isReleased.get()) return@OnVideoSizeChangedListener
        state.updateMetadata(
            state.metadata.value.copy(
                video = state.metadata.value.video?.copy(
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
        val trackInfos = runCatching { player?.trackInfo }.getOrNull() ?: return
        val selectedAudioStreamIndex = player?.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO) ?: -1
        
        val candidates = IjkTrackInfoResolver.resolveAudioTracks(
            trackInfos = trackInfos,
            selectedAudioStreamIndex = selectedAudioStreamIndex,
            sortMode = Configs.audioTrackSortMode
        )
        
        audioTrackState.set(audioTrackState.get().copy(candidates = candidates))
    }
    
    private fun restoreAudioTrack() {
        val currentState = audioTrackState.get()
        if (currentState.restored || currentState.candidates.isEmpty()) return
        
        audioTrackState.set(currentState.copy(restored = true))
        
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
                val updatedCandidates = currentState.candidates.map { c ->
                    c.copy(metadata = c.metadata.copy(isSelected = c.streamIndex == candidate.streamIndex))
                }
                audioTrackState.set(currentState.copy(candidates = updatedCandidates))
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
                            state.updateIsPlaying(p.isPlaying)
                            state.updateCurrentPosition(p.currentPosition)
                        }
                    }
                }
                if (retryAudioTracks && state.metadata.value.audioTracks.isEmpty()) {
                    synchronized(playerLock) {
                        if (!isReleased.get()) {
                            val trackInfos = runCatching { player?.trackInfo }.getOrNull()
                            if (trackInfos != null && trackInfos.isNotEmpty()) {
                                parseAudioTracks()
                                restoreAudioTrack()
                                state.updateMetadata(
                                    state.metadata.value.copy(
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
    
    private data class PlaybackModeState(
        val isPlayback: Boolean = false,
        val startTime: Long = 0L,
        val endTime: Long = 0L,
        val manuallySet: Boolean = false
    )
    
    private data class AudioTrackState(
        val candidates: List<IjkTrackInfoResolver.AudioTrackCandidate> = emptyList(),
        val userSelectedTrackId: String? = null,
        val restored: Boolean = false
    )
}
