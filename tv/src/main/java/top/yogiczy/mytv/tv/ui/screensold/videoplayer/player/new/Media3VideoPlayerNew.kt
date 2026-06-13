package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import android.content.Context
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr.AudioCaptureProcessor
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr.LiveAsrProcessor
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@OptIn(UnstableApi::class)
class Media3VideoPlayerNew(
    private val context: Context,
    coroutineScope: CoroutineScope
) : BaseVideoPlayer(coroutineScope) {
    
    override val stateManager = VideoPlayerStateManager(coroutineScope)
    override val cues: StateFlow<List<Cue>> = stateManager.cues
    private val errorHandler = PlayerErrorHandler(stateManager, coroutineScope)
    
    private val playerLock = Any()
    @Volatile private var videoPlayer: ExoPlayer? = null
    @Volatile private var surfaceView: SurfaceView? = null
    @Volatile private var textureView: TextureView? = null
    
    private var currentChannelLine = ChannelLine()
    private val formatFallbackQueue = LinkedList<Int>()
    private var forcedContentType: Int? = null
    private var positionUpdateJob: Job? = null
    private var retryPlaybackJob: Job? = null
    private var retryPlaybackCount = 0
    
    private val audioCaptureProcessor = AudioCaptureProcessor()
    private var liveAsrProcessor: LiveAsrProcessor? = null
    
    private val mediaSourceFactory = MediaSourceFactory(
        context = context,
        playbackModeState = { playbackModeState.get().isPlayback },
        onDrmError = { msg -> errorHandler.handleError(PlayerErrorType.DRM, msg) }
    )
    
    private val playerJob = SupervisorJob()
    private val playerScope = CoroutineScope(coroutineScope.coroutineContext + playerJob)
    private val softDecode = AtomicReference<Boolean?>(null)
    
    private val volumeFader = VolumeFader(playerScope) { volume ->
        videoPlayer?.volume = volume
        stateManager.updateVolume(volume)
    }
    
    override fun getVolumeFader(): VolumeFader = volumeFader
    
    private val eventLogger = EventLogger()

    init {
        videoPlayer = createPlayer()
        loadAudioTrackMemory()
    }
    
    override fun initialize() {
        if (isReleased.get() || !isInitialized.compareAndSet(false, true)) return
        
        videoPlayer?.addListener(playerListener)
        videoPlayer?.addAnalyticsListener(metadataListener)
        videoPlayer?.addAnalyticsListener(eventLogger)
    }
    
    override fun release() {
        if (isReleased.getAndSet(true)) return
        
        stopLiveSubtitle()
        
        synchronized(playerLock) {
            videoPlayer?.stop()
            videoPlayer?.setVideoSurfaceView(null)
            
            playerJob.cancel()
            
            videoPlayer?.removeListener(playerListener)
            videoPlayer?.removeAnalyticsListener(metadataListener)
            videoPlayer?.removeAnalyticsListener(eventLogger)
            videoPlayer?.release()
            videoPlayer = null
            
            surfaceView = null
            textureView = null
        }
        
        stateManager.reset()
    }
    
    override fun prepare(line: ChannelLine) {
        if (isReleased.get()) return
        
        currentChannelLine = line
        formatFallbackQueue.clear()
        forcedContentType = null
        
        internalPrepare(line)
    }

    private fun internalPrepare(line: ChannelLine) {
        if (isReleased.get()) return
        
        currentChannelLine = line
        // 不清空 formatFallbackQueue — 由 handleParsingError 管理格式降级队列
        
        updatePlaybackModeState(line)
        
        val mediaSource = createMediaSource()
        if (mediaSource != null) {
            videoPlayer?.setMediaSource(mediaSource)
            videoPlayer?.prepare()
            videoPlayer?.play()
        } else {
            stateManager.updateError("无法识别的媒体格式")
        }
    }
    
    override fun play() {
        videoPlayer?.play()
    }
    
    override fun pause() {
        videoPlayer?.pause()
    }
    
    override fun playWithFadeIn(systemVolume: Float) {
        videoPlayer?.volume = 0f
        volumeFader.syncCurrentVolume(0f)
        videoPlayer?.play()
        volumeFader.fadeIn(systemVolume)
    }
    
    override fun pauseWithFadeOut() {
        volumeFader.fadeOut {
            videoPlayer?.pause()
        }
    }
    
    override fun stop() {
        videoPlayer?.stop()
    }
    
    override fun seekTo(position: Long) {
        videoPlayer?.seekTo(position)
    }
    
    override fun setVolume(volume: Float) {
        volumeFader.fadeTo(volume)
    }
    
    override fun getVolume(): Float {
        return videoPlayer?.volume ?: 1f
    }
    
    override fun syncVolume(volume: Float) {
        volumeFader.syncCurrentVolume(volume)
    }
    
    override fun selectVideoTrack(track: PlayerMetadata.VideoTrack?) {
        selectTrackByIndex(C.TRACK_TYPE_VIDEO, track?.index)
    }
    
    override fun selectAudioTrack(track: PlayerMetadata.AudioTrack?) {
        if (track?.trackId != null) {
            audioTrackMemoryCache.put(currentChannelLine.playableUrl, track.trackId)
            saveAudioTrackMemory()
        } else {
            audioTrackMemoryCache.remove(currentChannelLine.playableUrl)
            saveAudioTrackMemory()
        }
        
        selectTrackByIndex(C.TRACK_TYPE_AUDIO, track?.index)
    }
    
    override fun selectSubtitleTrack(track: PlayerMetadata.SubtitleTrack?) {
        selectTrackByIndex(C.TRACK_TYPE_TEXT, track?.index)
    }

    // ==================== 实时字幕 ====================

    private val audioListener: (ByteArray) -> Unit = { data ->
        liveAsrProcessor?.feedAudio(data)
    }

    fun startLiveSubtitle() {
        if (liveAsrProcessor?.isRunning() == true) return
        audioCaptureProcessor.addListener(audioListener)
        audioCaptureProcessor.setActive(true)
        liveAsrProcessor = LiveAsrProcessor(
            context = context,
            scope = coroutineScope,
            onCues = { cues ->
                stateManager.updateCues(cues)
            }
        ).also { it.start() }
    }

    fun stopLiveSubtitle() {
        audioCaptureProcessor.removeListener(audioListener)
        liveAsrProcessor?.stop()
        liveAsrProcessor = null
        audioCaptureProcessor.setActive(false)
    }

    override fun toggleLiveSubtitle() {
        if (liveAsrProcessor?.isRunning() == true) {
            stopLiveSubtitle()
        } else {
            startLiveSubtitle()
        }
    }
    
    private fun selectTrackByIndex(trackType: Int, index: Int?) {
        if (index == null) {
            updateTrackSelectionParameters(trackType, disabled = true)
            return
        }
        
        val (group, trackIndex) = index.fromIndexFindTrack(trackType) ?: return
        videoPlayer?.trackSelectionParameters = videoPlayer?.trackSelectionParameters
            ?.buildUpon()
            ?.setTrackTypeDisabled(trackType, false)
            ?.setOverrideForType(TrackSelectionOverride(group, trackIndex))
            ?.build() ?: return
    }
    
    private fun updateTrackSelectionParameters(trackType: Int, disabled: Boolean) {
        videoPlayer?.trackSelectionParameters = videoPlayer?.trackSelectionParameters
            ?.buildUpon()
            ?.setTrackTypeDisabled(trackType, disabled)
            ?.build() ?: return
    }
    
    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        this.surfaceView = surfaceView
        videoPlayer?.setVideoSurfaceView(surfaceView)
    }
    
    override fun setVideoTextureView(textureView: TextureView) {
        this.textureView = textureView
        videoPlayer?.setVideoTextureView(textureView)
    }

    private fun createPlayer(): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(
                if (softDecode.get() ?: Configs.videoPlayerForceAudioSoftDecode)
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            )
            .setEnableDecoderFallback(true)
        
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)
                .setForceHighestSupportedBitrate(true)
                .setPreferredAudioLanguages("zh", "cmn")
                .setPreferredTextLanguages("zh")
                .setSelectUndeterminedTextLanguage(true)
                .build()
        }
        
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(BUFFER_MIN_MS, BUFFER_MAX_MS, BUFFER_PLAYBACK_START_MS, BUFFER_PLAYBACK_RESUME_MS)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        
        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioProcessors(audioCaptureProcessor)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
            .apply { playWhenReady = true }
    }
    
    private fun createMediaSource(): MediaSource? {
        return mediaSourceFactory.create(currentChannelLine, forcedContentType)
    }
    
    private fun Int.fromIndexFindTrack(type: @C.TrackType Int): Pair<TrackGroup, Int>? {
        val groups = videoPlayer?.currentTracks?.groups
            ?.filter { group -> 
                when (type) {
                    C.TRACK_TYPE_VIDEO -> group.type == C.TRACK_TYPE_VIDEO
                    C.TRACK_TYPE_AUDIO -> group.type == C.TRACK_TYPE_AUDIO
                    C.TRACK_TYPE_TEXT -> group.type == C.TRACK_TYPE_TEXT
                    else -> false
                }
            }
            ?.map { it.mediaTrackGroup }
            ?: return null
        
        var trackCount = 0
        val group = groups.firstOrNull { group ->
            trackCount += group.length
            this < trackCount
        } ?: return null
        
        val trackIndex = this - (trackCount - group.length)
        return Pair(group, trackIndex)
    }
    
    private val playerListener = object : Player.Listener {
        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
            // 实时字幕运行时，跳过内嵌字幕更新，避免覆盖
            if (liveAsrProcessor?.isRunning() != true) {
                stateManager.updateCues(cueGroup.cues)
            }
        }
        
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            stateManager.updateMetadata(
                stateManager.metadata.value.copy(
                    video = PlayerMetadata.VideoTrack(
                        width = videoSize.width,
                        height = videoSize.height
                    )
                )
            )
        }
        
        override fun onPlayerError(ex: androidx.media3.common.PlaybackException) {
            when (ex.errorCode) {
                androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> handleBehindLiveWindowError()
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                    retryPlayback()
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                    if (formatFallbackQueue.isNotEmpty()) {
                        handleParsingError(ex)
                    } else {
                        retryPlayback()
                    }
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> handleParsingError(ex)
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> handleDecoderInitError(ex)
                else -> errorHandler.handleMedia3Error(ex)
            }
        }
        
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    stateManager.updateError(null)
                    stateManager.updateIsBuffering(true)
                }
                Player.STATE_READY -> {
                    stateManager.updateIsBuffering(false)
                    stateManager.updateError(null)
                    errorHandler.resetRetryCount()
                    retryPlaybackCount = 0
                    updateDuration()
                    updateTracks()
                    startPositionUpdate()
                    handlePendingFadeIn()
                }
                Player.STATE_ENDED -> {
                    if (!playbackModeState.get().isPlayback && isLiveStream()) retryPlayback()
                }
            }
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            stateManager.updateIsPlaying(isPlaying)
        }
        
        override fun onTracksChanged(tracks: Tracks) {
            updateTracks()
        }
    }
    
    private fun handleBehindLiveWindowError() {
        if (playbackModeState.get().isPlayback) {
            errorHandler.handlePlaybackBehindLiveWindow()
        } else {
            retryPlayback()
        }
    }
    
    private fun handleParsingError(ex: androidx.media3.common.PlaybackException) {
        if (formatFallbackQueue.isEmpty()) {
            formatFallbackQueue.addAll(
                listOf(C.CONTENT_TYPE_OTHER, C.CONTENT_TYPE_HLS, C.CONTENT_TYPE_DASH)
            )
        }

        val nextType = formatFallbackQueue.poll() ?: run {
            errorHandler.handleMedia3Error(ex)
            return
        }
        forcedContentType = nextType
        internalPrepare(currentChannelLine)
    }
    
    private fun handleDecoderInitError(ex: androidx.media3.common.PlaybackException) {
        if (softDecode.get() != true) {
            softDecode.set(true)
            reInitPlayer()
        } else {
            errorHandler.handleMedia3Error(ex)
        }
    }
    
    private fun retryPlayback() {
        if (retryPlaybackCount >= MAX_RETRY_PLAYBACK) return
        retryPlaybackJob?.cancel()
        retryPlaybackJob = playerScope.launch {
            delay(2000L)
            if (!isReleased.get()) {
                retryPlaybackCount++
                videoPlayer?.seekToDefaultPosition()
                videoPlayer?.prepare()
            }
        }
    }
    
    private fun isLiveStream(): Boolean {
        val duration = videoPlayer?.duration ?: C.TIME_UNSET
        return duration == C.TIME_UNSET || duration <= 0
    }
    
    private fun updateDuration() {
        val duration = videoPlayer?.duration ?: C.TIME_UNSET
        val isPlayback = playbackModeState.get().isPlayback
        
        val effectiveDuration = when {
            isPlayback && duration != C.TIME_UNSET && duration > 0 -> duration
            isPlayback -> {
                val currentState = playbackModeState.get()
                (currentState.endTime - currentState.startTime).takeIf { it > 0 } ?: duration
            }
            else -> duration
        }
        stateManager.updateDuration(effectiveDuration)
    }
    
    private val metadataListener = object : AnalyticsListener {
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
        ) {
            updateVideoMetadata { it.copy(
                width = format.width,
                height = format.height,
                frameRate = format.frameRate,
                bitrate = format.bitrate,
                mimeType = format.sampleMimeType,
                trackId = format.id
            ) }
        }
        
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            updateVideoMetadata { it.copy(decoder = decoderName) }
        }
        
        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
        ) {
            stateManager.updateMetadata(
                stateManager.metadata.value.copy(
                    audio = PlayerMetadata.AudioTrack(
                        channels = format.channelCount,
                        sampleRate = format.sampleRate,
                        bitrate = format.bitrate,
                        mimeType = format.sampleMimeType,
                        language = format.language,
                        trackId = format.id
                    )
                )
            )
        }
        
        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            updateAudioMetadata { it.copy(decoder = decoderName) }
        }
    }
    
    private inline fun updateVideoMetadata(update: (PlayerMetadata.VideoTrack) -> PlayerMetadata.VideoTrack) {
        val current = stateManager.metadata.value
        val updated = current.video?.let(update) ?: return
        stateManager.updateMetadata(current.copy(video = updated))
    }
    
    private inline fun updateAudioMetadata(update: (PlayerMetadata.AudioTrack) -> PlayerMetadata.AudioTrack) {
        val current = stateManager.metadata.value
        val updated = current.audio?.let(update) ?: return
        stateManager.updateMetadata(current.copy(audio = updated))
    }
    
    private fun updateTracks() {
        val videoTracks = TrackExtractor.extractVideoTracks(videoPlayer)
        val audioTracks = TrackExtractor.extractAudioTracks(videoPlayer)
        val subtitleTracks = TrackExtractor.extractSubtitleTracks(videoPlayer)
        
        playerScope.launch(Dispatchers.Default) {
            val cachedAudioTracks = audioTrackListCache.get(currentChannelLine.playableUrl)
            
            val finalAudioTracks = if (cachedAudioTracks != null && cachedAudioTracks.isNotEmpty()) {
                cachedAudioTracks
            } else {
                if (audioTracks.isNotEmpty()) {
                    audioTrackListCache.put(currentChannelLine.playableUrl, audioTracks)
                }
                audioTracks
            }
            
            withContext(Dispatchers.Main) {
                stateManager.updateMetadata(
                    stateManager.metadata.value.copy(
                        video = videoTracks.firstOrNull { it.isSelected == true } ?: stateManager.metadata.value.video,
                        audio = finalAudioTracks.firstOrNull { it.isSelected == true } ?: stateManager.metadata.value.audio,
                        subtitle = subtitleTracks.firstOrNull { it.isSelected == true },
                        videoTracks = videoTracks,
                        audioTracks = finalAudioTracks,
                        subtitleTracks = subtitleTracks
                    )
                )
                
                restoreAudioTrack(finalAudioTracks)
            }
        }
    }
    
    private fun restoreAudioTrack(audioTracks: List<PlayerMetadata.AudioTrack>) {
        val trackId = audioTrackMemoryCache.get(currentChannelLine.playableUrl) ?: return
        val trackToSelect = audioTracks.find { it.trackId == trackId }
        if (trackToSelect != null && trackToSelect.isSelected != true) {
            selectAudioTrack(trackToSelect)
        }
    }
    
    private fun startPositionUpdate() {
        if (isReleased.get()) return
        positionUpdateJob?.cancel()
        positionUpdateJob = playerScope.launch {
            while (isActive && !isReleased.get()) {
                videoPlayer?.let { stateManager.updateCurrentPosition(calculateCurrentPosition(it)) }
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }
    
    private fun calculateCurrentPosition(player: ExoPlayer): Long {
        if (playbackModeState.get().isPlayback) return player.currentPosition
        
        val liveOffset = player.currentLiveOffset
        if (liveOffset == C.TIME_UNSET || liveOffset <= 0) return player.currentPosition
        
        val livePosition = System.currentTimeMillis() - liveOffset
        return if (livePosition > 0) livePosition else player.currentPosition
    }
    
    private fun reInitPlayer() {
        synchronized(playerLock) {
            videoPlayer?.removeListener(playerListener)
            videoPlayer?.removeAnalyticsListener(metadataListener)
            videoPlayer?.removeAnalyticsListener(eventLogger)
            videoPlayer?.stop()
            videoPlayer?.release()
            
            videoPlayer = createPlayer()
            
            videoPlayer?.addListener(playerListener)
            videoPlayer?.addAnalyticsListener(metadataListener)
            videoPlayer?.addAnalyticsListener(eventLogger)
            
            surfaceView?.let { setVideoSurfaceView(it) }
            textureView?.let { setVideoTextureView(it) }
        }
        
        internalPrepare(currentChannelLine)
    }

    companion object {
        private const val BUFFER_MIN_MS = 5000
        private const val BUFFER_MAX_MS = 30000
        private const val BUFFER_PLAYBACK_START_MS = 1000
        private const val BUFFER_PLAYBACK_RESUME_MS = 2000
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
        private const val SEEK_INCREMENT_MS = 10000L
        private const val MAX_RETRY_PLAYBACK = 3
    }
}
