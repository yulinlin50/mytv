package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.extractor.DefaultExtractorsFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.utils.PlaybackUtil
import top.yogiczy.mytv.core.util.utils.toHeaders
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@OptIn(UnstableApi::class)
class Media3VideoPlayerNew(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : IVideoPlayer {
    
    override val state = VideoPlayerStateManager(coroutineScope)
    override val cues: StateFlow<List<Cue>> = state.cues
    private val errorHandler = PlayerErrorHandler(state, coroutineScope)
    private val audioTrackMemoryCache = AudioTrackMemoryCache(maxSize = 100)
    
    private var videoPlayer: ExoPlayer? = null
    private var surfaceView: SurfaceView? = null
    private var textureView: TextureView? = null
    
    private var currentChannelLine = ChannelLine()
    private val contentTypeAttempts = ConcurrentHashMap<Int, Boolean>()
    private var forcedContentType: Int? = null
    private var isFormatFallback = false
    private var retryCount = 0
    private var hasAttemptedFormatFallback = false
    
    private val jobs = mutableListOf<Job>()
    private val jobsLock = Any()
    private val isReleased = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    
    private val playbackModeState = AtomicReference(PlaybackModeState())
    private val softDecode = AtomicReference<Boolean?>(null)
    
    private var cachedUri: Uri? = null
    private var cachedUriString: String? = null
    
    private val eventLogger = EventLogger()
    
    private val extractorsFactory = DefaultExtractorsFactory()
        .setTsExtractorTimestampSearchBytes(TS_TIMESTAMP_SEARCH_BYTES)
        .setConstantBitrateSeekingEnabled(true)
    
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
        
        videoPlayer?.stop()
        videoPlayer?.setVideoSurfaceView(null)
        
        synchronized(jobsLock) {
            jobs.forEach { it.cancel() }
            jobs.clear()
        }
        
        videoPlayer?.removeListener(playerListener)
        videoPlayer?.removeAnalyticsListener(metadataListener)
        videoPlayer?.removeAnalyticsListener(eventLogger)
        videoPlayer?.release()
        videoPlayer = null
        
        surfaceView = null
        textureView = null
        
        state.reset()
    }
    
    override fun prepare(line: ChannelLine) {
        if (isReleased.get()) return
        
        currentChannelLine = line
        if (!isFormatFallback) {
            contentTypeAttempts.clear()
            hasAttemptedFormatFallback = false
        }
        isFormatFallback = false
        forcedContentType = null
        retryCount = 0
        
        updatePlaybackModeState(line)
        
        val mediaSource = createMediaSource()
        if (mediaSource != null) {
            videoPlayer?.setMediaSource(mediaSource)
            videoPlayer?.prepare()
            videoPlayer?.play()
        } else {
            state.updateError("无法识别的媒体格式")
        }
    }
    
    override fun play() {
        videoPlayer?.play()
    }
    
    override fun pause() {
        videoPlayer?.pause()
    }
    
    override fun stop() {
        videoPlayer?.stop()
        cancelAllJobs()
    }
    
    override fun seekTo(position: Long) {
        videoPlayer?.seekTo(position)
    }
    
    override fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        videoPlayer?.volume = v
        state.updateVolume(v)
    }
    
    override fun getVolume(): Float {
        return videoPlayer?.volume ?: 1f
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
        if (track?.language == null) {
            updateTrackSelectionParameters(C.TRACK_TYPE_TEXT, disabled = true)
        } else {
            videoPlayer?.trackSelectionParameters = videoPlayer?.trackSelectionParameters
                ?.buildUpon()
                ?.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                ?.setPreferredTextLanguages(track.language)
                ?.build() ?: return
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
    
    override fun setPlaybackMode(isPlayback: Boolean) {
        val currentState = playbackModeState.get()
        if (currentState.isPlayback != isPlayback) {
            playbackModeState.set(currentState.copy(isPlayback = isPlayback))
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
                .setForceHighestSupportedBitrate(false)
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
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
            .apply { playWhenReady = true }
    }
    
    private fun createMediaSource(): MediaSource? {
        val uriString = if (playbackModeState.get().isPlayback) {
            currentChannelLine.url
        } else {
            currentChannelLine.playableUrl
        }
        val uri = getCachedUri(uriString)
        val contentType = detectContentType(uriString)
        val mediaItem = createMediaItem(uri, contentType)
        val dataSourceFactory = getDataSourceFactory()
        
        return when (contentType) {
            C.CONTENT_TYPE_HLS -> createHlsMediaSource(mediaItem, dataSourceFactory)
            C.CONTENT_TYPE_DASH -> createDashMediaSource(mediaItem, dataSourceFactory)
            C.CONTENT_TYPE_RTSP -> RtspMediaSource.Factory().createMediaSource(mediaItem)
            C.CONTENT_TYPE_OTHER -> ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                .createMediaSource(mediaItem)
            else -> {
                errorHandler.handleError(
                    PlayerErrorType.FormatError(
                        errorCode = 10002,
                        message = "Unsupported content type"
                    )
                )
                null
            }
        }
    }
    
    private fun detectContentType(uriString: String): Int {
        forcedContentType?.let { return it }
        
        if (uriString.startsWith("rtp://")) return C.CONTENT_TYPE_RTSP
        
        when (currentChannelLine.manifestType?.lowercase()) {
            "mpd" -> return C.CONTENT_TYPE_DASH
            "m3u8", "hls" -> return C.CONTENT_TYPE_HLS
        }
        
        val urlLower = uriString.lowercase()
        when {
            urlLower.contains(".m3u8") -> return C.CONTENT_TYPE_HLS
            urlLower.contains(".mpd") -> return C.CONTENT_TYPE_DASH
            urlLower.contains(".flv") -> return C.CONTENT_TYPE_OTHER
            urlLower.startsWith("rtmp://") || urlLower.startsWith("rtmps://") -> return C.CONTENT_TYPE_OTHER
        }
        
        val inferredType = Util.inferContentType(getCachedUri(uriString))
        if (inferredType != C.CONTENT_TYPE_OTHER) return inferredType
        
        return C.CONTENT_TYPE_OTHER
    }
    
    private fun createMediaItem(uri: Uri, contentType: Int): MediaItem {
        val isPlayback = playbackModeState.get().isPlayback
        val isHlsLive = contentType == C.CONTENT_TYPE_HLS && !isPlayback
        
        return when {
            isPlayback -> MediaItem.Builder()
                .setUri(uri)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(C.TIME_UNSET)
                        .setMinOffsetMs(C.TIME_UNSET)
                        .setMaxOffsetMs(C.TIME_UNSET)
                        .setMinPlaybackSpeed(1.0f)
                        .setMaxPlaybackSpeed(1.0f)
                        .build()
                )
                .build()
            isHlsLive -> MediaItem.Builder()
                .setUri(uri)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(C.TIME_UNSET)
                        .build()
                )
                .build()
            else -> MediaItem.fromUri(uri)
        }
    }
    
    private fun createHlsMediaSource(
        mediaItem: MediaItem,
        dataSourceFactory: DefaultDataSource.Factory
    ): HlsMediaSource {
        return HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(false)
            .setTimestampAdjusterInitializationTimeoutMs(HLS_TIMESTAMP_TIMEOUT_MS)
            .createMediaSource(mediaItem)
    }
    
    private fun createDashMediaSource(
        mediaItem: MediaItem,
        dataSourceFactory: DefaultDataSource.Factory
    ): DashMediaSource {
        return DashMediaSource.Factory(dataSourceFactory)
            .apply {
                if (currentChannelLine.licenseType == "clearkey" && 
                    currentChannelLine.licenseKey != null) {
                    setupDrm()
                }
            }
            .createMediaSource(mediaItem)
    }
    
    private fun DashMediaSource.Factory.setupDrm() {
        runCatching {
            val (drmKeyId, drmKey) = currentChannelLine.licenseKey!!.split(":")
            val encodedDrmKey = Base64.encodeToString(
                drmKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val encodedDrmKeyId = Base64.encodeToString(
                drmKeyId.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val drmBody = "{\"keys\":[{\"kty\":\"oct\",\"k\":\"$encodedDrmKey\",\"kid\":\"$encodedDrmKeyId\"}],\"type\":\"temporary\"}"
            
            val drmCallback = LocalMediaDrmCallback(drmBody.toByteArray())
            val drmSessionManager = DefaultDrmSessionManager.Builder()
                .setMultiSession(true)
                .setUuidAndExoMediaDrmProvider(
                    C.CLEARKEY_UUID,
                    FrameworkMediaDrm.DEFAULT_PROVIDER
                )
                .build(drmCallback)
            
            setDrmSessionManagerProvider { drmSessionManager }
        }.onFailure {
            errorHandler.handleError(
                PlayerErrorType.DrmError(
                    errorCode = androidx.media3.common.PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED,
                    message = it.message ?: "DRM license error"
                )
            )
        }
    }
    
    private fun getDataSourceFactory(): DefaultDataSource.Factory {
        val baseHeaders = Configs.videoPlayerHeaders.toHeaders()
        val headers = baseHeaders.toMutableMap().apply {
            put("Connection", "keep-alive")
            put("Accept-Encoding", "identity")
        }
        
        val userAgent = baseHeaders["User-Agent"]
            ?: currentChannelLine.httpUserAgent
            ?: Configs.videoPlayerUserAgent
        
        return DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory().apply {
                setUserAgent(userAgent)
                setDefaultRequestProperties(headers)
                setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                setReadTimeoutMs(READ_TIMEOUT_MS)
                setKeepPostFor302Redirects(true)
                setAllowCrossProtocolRedirects(true)
            }
        )
    }
    
    private fun getCachedUri(uriString: String): Uri {
        if (cachedUriString != uriString || cachedUri == null) {
            cachedUriString = uriString
            cachedUri = Uri.parse(uriString)
        }
        return cachedUri ?: Uri.parse(uriString)
    }
    
    private fun updatePlaybackModeState(line: ChannelLine) {
        val urlIsPlayback = PlaybackUtil.isPlaybackUrl(line.url)
        val lineSupportsPlayback = line.hasCatchupSupport() || urlIsPlayback
        
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
            state.updateCues(cueGroup.cues)
        }
        
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            state.updateMetadata(
                state.metadata.value.copy(
                    video = PlayerMetadata.VideoTrack(
                        width = videoSize.width,
                        height = videoSize.height
                    )
                )
            )
        }
        
        override fun onPlayerError(ex: androidx.media3.common.PlaybackException) {
            retryCount++
            when (ex.errorCode) {
                androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> handleBehindLiveWindowError()
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                    if (retryCount < MAX_RETRY_COUNT) retryPlayback()
                    else errorHandler.handleMedia3Error(ex)
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                    if (!hasAttemptedFormatFallback) {
                        hasAttemptedFormatFallback = true
                        handleParsingError(ex)
                    } else if (retryCount < MAX_RETRY_COUNT) {
                        retryPlayback()
                    } else {
                        errorHandler.handleMedia3Error(ex)
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
                    state.updateError(null)
                    state.updateIsBuffering(true)
                }
                Player.STATE_READY -> {
                    state.updateIsBuffering(false)
                    state.updateError(null)
                    updateDuration()
                    updateTracks()
                    startPositionUpdate()
                }
                Player.STATE_ENDED -> {
                    if (!playbackModeState.get().isPlayback && isLiveStream()) retryPlayback()
                }
            }
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            state.updateIsPlaying(isPlaying)
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
        val currentUriString = if (playbackModeState.get().isPlayback) {
            currentChannelLine.url
        } else {
            currentChannelLine.playableUrl
        }
        val currentContentType = detectContentType(currentUriString)
        contentTypeAttempts[currentContentType] = true

        when {
            contentTypeAttempts[C.CONTENT_TYPE_OTHER] != true -> {
                contentTypeAttempts[C.CONTENT_TYPE_OTHER] = true
                forcedContentType = C.CONTENT_TYPE_OTHER
                isFormatFallback = true
                prepare(currentChannelLine)
            }
            contentTypeAttempts[C.CONTENT_TYPE_HLS] != true -> {
                contentTypeAttempts[C.CONTENT_TYPE_HLS] = true
                forcedContentType = C.CONTENT_TYPE_HLS
                isFormatFallback = true
                prepare(currentChannelLine)
            }
            contentTypeAttempts[C.CONTENT_TYPE_DASH] != true -> {
                contentTypeAttempts[C.CONTENT_TYPE_DASH] = true
                forcedContentType = C.CONTENT_TYPE_DASH
                isFormatFallback = true
                prepare(currentChannelLine)
            }
            else -> errorHandler.handleMedia3Error(ex)
        }
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
        if (retryCount >= MAX_RETRY_COUNT) {
            errorHandler.handleError(
                PlayerErrorType.UnknownError(errorCode = 10005, message = "重试次数已用尽")
            )
            return
        }
        coroutineScope.launch {
            delay(1000L * retryCount)
            if (!isReleased.get()) {
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
        state.updateDuration(effectiveDuration)
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
            state.updateMetadata(
                state.metadata.value.copy(
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
        val current = state.metadata.value
        val updated = current.video?.let(update) ?: return
        state.updateMetadata(current.copy(video = updated))
    }
    
    private inline fun updateAudioMetadata(update: (PlayerMetadata.AudioTrack) -> PlayerMetadata.AudioTrack) {
        val current = state.metadata.value
        val updated = current.audio?.let(update) ?: return
        state.updateMetadata(current.copy(audio = updated))
    }
    
    private fun updateTracks() {
        val videoTracks = extractVideoTracks()
        val audioTracks = extractAudioTracks()
        val subtitleTracks = extractSubtitleTracks()
        
        state.updateMetadata(
            state.metadata.value.copy(
                video = videoTracks.firstOrNull { it.isSelected == true } ?: state.metadata.value.video,
                audio = audioTracks.firstOrNull { it.isSelected == true } ?: state.metadata.value.audio,
                subtitle = subtitleTracks.firstOrNull { it.isSelected == true },
                videoTracks = videoTracks,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks
            )
        )
        
        restoreAudioTrack(audioTracks)
    }
    
    private fun extractVideoTracks(): List<PlayerMetadata.VideoTrack> {
        return videoPlayer?.currentTracks?.groups
            ?.filter { it.type == C.TRACK_TYPE_VIDEO }
            ?.flatMap { group ->
                (0 until group.mediaTrackGroup.length).map { trackIndex ->
                    group.mediaTrackGroup.getFormat(trackIndex)
                        .toVideoMetadata()
                        .copy(isSelected = group.isTrackSelected(trackIndex))
                }
            }
            ?.mapIndexed { index, track -> track.copy(index = index) }
            ?: emptyList()
    }
    
    private fun extractAudioTracks(): List<PlayerMetadata.AudioTrack> {
        return videoPlayer?.currentTracks?.groups
            ?.filter { it.type == C.TRACK_TYPE_AUDIO }
            ?.flatMap { group ->
                (0 until group.mediaTrackGroup.length).map { trackIndex ->
                    group.mediaTrackGroup.getFormat(trackIndex)
                        .toAudioMetadata()
                        .copy(isSelected = group.isTrackSelected(trackIndex))
                }
            }
            ?.mapIndexed { index, track -> track.copy(index = index) }
            ?: emptyList()
    }
    
    private fun extractSubtitleTracks(): List<PlayerMetadata.SubtitleTrack> {
        return videoPlayer?.currentTracks?.groups
            ?.filter { it.type == C.TRACK_TYPE_TEXT }
            ?.flatMap { group ->
                (0 until group.mediaTrackGroup.length).mapNotNull { trackIndex ->
                    group.mediaTrackGroup.getFormat(trackIndex)
                        .takeIf { it.roleFlags == C.ROLE_FLAG_SUBTITLE }
                        ?.toSubtitleMetadata()
                        ?.copy(isSelected = group.isTrackSelected(trackIndex))
                }
            }
            ?.mapIndexed { index, track -> track.copy(index = index) }
            ?: emptyList()
    }
    
    private fun restoreAudioTrack(audioTracks: List<PlayerMetadata.AudioTrack>) {
        val trackId = audioTrackMemoryCache.get(currentChannelLine.playableUrl) ?: return
        val trackToSelect = audioTracks.find { it.trackId == trackId }
        if (trackToSelect != null && trackToSelect.isSelected != true) {
            selectAudioTrack(trackToSelect)
        }
    }
    
    private fun startPositionUpdate() {
        cancelAllJobs()
        if (isReleased.get()) return
        
        val job = coroutineScope.launch {
            while (isActive && !isReleased.get()) {
                videoPlayer?.let { state.updateCurrentPosition(calculateCurrentPosition(it)) }
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
        synchronized(jobsLock) { jobs.add(job) }
    }
    
    private fun calculateCurrentPosition(player: ExoPlayer): Long {
        if (playbackModeState.get().isPlayback) return player.currentPosition
        
        val liveOffset = player.currentLiveOffset
        if (liveOffset == C.TIME_UNSET || liveOffset <= 0) return player.currentPosition
        
        val livePosition = System.currentTimeMillis() - liveOffset
        return if (livePosition > 0) livePosition else player.currentPosition
    }
    
    private fun cancelAllJobs() {
        synchronized(jobsLock) {
            jobs.forEach { it.cancel() }
            jobs.clear()
        }
    }
    
    private fun reInitPlayer() {
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
        
        prepare(currentChannelLine)
    }
    
    private fun Format.toVideoMetadata(): PlayerMetadata.VideoTrack {
        return PlayerMetadata.VideoTrack(
            width = width,
            height = height,
            frameRate = frameRate,
            bitrate = bitrate,
            mimeType = sampleMimeType,
            trackId = id ?: "$sampleMimeType-$width-$height-$frameRate-$bitrate"
        )
    }
    
    private fun Format.toAudioMetadata(): PlayerMetadata.AudioTrack {
        return PlayerMetadata.AudioTrack(
            channels = channelCount,
            sampleRate = sampleRate,
            bitrate = bitrate,
            mimeType = sampleMimeType,
            language = language,
            trackId = id ?: "$sampleMimeType-$language-$bitrate"
        )
    }
    
    private fun Format.toSubtitleMetadata(): PlayerMetadata.SubtitleTrack {
        return PlayerMetadata.SubtitleTrack(
            bitrate = bitrate,
            mimeType = sampleMimeType,
            language = language,
            trackId = id ?: "$sampleMimeType-$language-$bitrate"
        )
    }
    
    private data class PlaybackModeState(
        val isPlayback: Boolean = false,
        val startTime: Long = 0L,
        val endTime: Long = 0L,
        val manuallySet: Boolean = false
    )

    companion object {
        private const val BUFFER_MIN_MS = 5000
        private const val BUFFER_MAX_MS = 30000
        private const val BUFFER_PLAYBACK_START_MS = 1000
        private const val BUFFER_PLAYBACK_RESUME_MS = 2000
        private const val CONNECT_TIMEOUT_MS = 10000
        private const val READ_TIMEOUT_MS = 60000
        private const val HLS_TIMESTAMP_TIMEOUT_MS = 30000L
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
        private const val SEEK_INCREMENT_MS = 10000L
        private const val TS_TIMESTAMP_SEARCH_BYTES = 1000 * 1024
        private const val MAX_RETRY_COUNT = 3
    }
}
