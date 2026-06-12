package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.util.utils.toHeaders
import top.yogiczy.mytv.tv.ui.utils.Configs

@OptIn(UnstableApi::class)
internal class MediaSourceFactory(
    private val context: Context,
    private val playbackModeState: () -> Boolean,
    private val onDrmError: (String) -> Unit,
) {
    private var cachedUri: Uri? = null
    private var cachedUriString: String? = null

    private val extractorsFactory = DefaultExtractorsFactory()
        .setTsExtractorTimestampSearchBytes(TS_TIMESTAMP_SEARCH_BYTES)
        .setConstantBitrateSeekingEnabled(true)

    fun create(line: ChannelLine, forcedContentType: Int? = null): MediaSource? {
        val uriString = if (playbackModeState()) line.url else line.playableUrl
        val uri = getCachedUri(uriString)
        val contentType = detectContentType(uriString, line, forcedContentType)
        val mediaItem = createMediaItem(uri, contentType, line)
        val dataSourceFactory = getDataSourceFactory(line)

        return when (contentType) {
            C.CONTENT_TYPE_HLS -> createHlsMediaSource(mediaItem, dataSourceFactory)
            C.CONTENT_TYPE_DASH -> createDashMediaSource(mediaItem, dataSourceFactory, line)
            C.CONTENT_TYPE_RTSP -> RtspMediaSource.Factory().createMediaSource(mediaItem)
            C.CONTENT_TYPE_OTHER -> ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                .createMediaSource(mediaItem)
            else -> null
        }
    }

    private fun detectContentType(uriString: String, line: ChannelLine, forcedContentType: Int?): Int {
        forcedContentType?.let { return it }

        if (uriString.startsWith("rtp://")) return C.CONTENT_TYPE_RTSP

        when (line.manifestType?.lowercase()) {
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

    private fun createMediaItem(uri: Uri, contentType: Int, line: ChannelLine): MediaItem {
        val isPlayback = playbackModeState()
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
        dataSourceFactory: DefaultDataSource.Factory,
        line: ChannelLine,
    ): DashMediaSource {
        return DashMediaSource.Factory(dataSourceFactory)
            .apply {
                if (line.licenseType == "clearkey" && line.licenseKey != null) {
                    setupDrm(line)
                }
            }
            .createMediaSource(mediaItem)
    }

    private fun DashMediaSource.Factory.setupDrm(line: ChannelLine) {
        runCatching {
            val (drmKeyId, drmKey) = line.licenseKey!!.split(":")
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
            onDrmError(it.message ?: "DRM license error")
        }
    }

    private fun getDataSourceFactory(line: ChannelLine): DefaultDataSource.Factory {
        val baseHeaders = Configs.videoPlayerHeaders.toHeaders()
        val headers = baseHeaders.toMutableMap().apply {
            put("Connection", "keep-alive")
            put("Accept-Encoding", "identity")
        }

        val userAgent = baseHeaders["User-Agent"]
            ?: line.httpUserAgent
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

    companion object {
        const val TS_TIMESTAMP_SEARCH_BYTES = 1000 * 1024
        const val HLS_TIMESTAMP_TIMEOUT_MS = 30000L
        const val CONNECT_TIMEOUT_MS = 10000
        const val READ_TIMEOUT_MS = 60000
    }
}