package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import top.yogiczy.mytv.core.util.utils.humanizeAudioChannels
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.AudioCodecCompatibilityChecker
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.AudioTrackCandidate
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.AudioTrackResolverCommon
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.PlayerMetadata
import top.yogiczy.mytv.tv.ui.utils.Configs
import tv.danmaku.ijk.media.player.IjkMediaMeta
import tv.danmaku.ijk.media.player.misc.ITrackInfo
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo

internal object IjkTrackInfoResolver {

    private const val LOCAL_AV3A_LAYOUT_5POINT1POINT4 = 0x2D60FL

    internal fun resolveAudioTracks(
        trackInfos: Array<out ITrackInfo>?,
        selectedAudioStreamIndex: Int,
        sortMode: Configs.AudioTrackSortMode = Configs.audioTrackSortMode,
    ): List<AudioTrackCandidate> {
        if (trackInfos == null) return emptyList()

        val candidates = trackInfos
            .filter { it.trackType == ITrackInfo.MEDIA_TRACK_TYPE_AUDIO }
            .mapIndexed { uiIndex, trackInfo ->
                buildAudioTrackCandidate(trackInfo, selectedAudioStreamIndex, uiIndex)
            }

        return AudioTrackResolverCommon.dedupeAndSortAudioTracks(candidates, sortMode)
    }

    internal fun buildAudioTrackCandidate(
        trackInfo: ITrackInfo,
        selectedAudioStreamIndex: Int,
        uiIndex: Int,
    ): AudioTrackCandidate {
        val streamMeta = extractStreamMeta(trackInfo)
        val streamIndex = streamMeta?.mIndex ?: uiIndex
        val isSelected = streamIndex == selectedAudioStreamIndex

        val normalizedLanguage = AudioTrackResolverCommon.normalizeTrackLanguage(streamMeta?.mLanguage ?: trackInfo.language)
        val trackTitle = AudioTrackResolverCommon.selectAudioTitle(streamMeta?.mLanguage)
            ?: normalizedLanguage?.humanizeLanguage()
        val codecLabel = streamMeta?.mCodecName?.let { AudioTrackResolverCommon.toAudioCodecLabel(it) }
        val channels = streamMeta?.mChannelLayout?.let { getChannelCount(it) }
        val channelsLabel = streamMeta?.mChannelLayout?.let { getChannelLabel(it) }
        val sampleRate = streamMeta?.mSampleRate?.takeIf { it > 0 }
        val bitrate = streamMeta?.mBitrate?.takeIf { it > 0 }?.toInt()

        val stableTrackId = AudioTrackResolverCommon.buildStableAudioTrackId(
            language = normalizedLanguage,
            title = trackTitle,
            codecLabel = codecLabel,
            channels = channels,
            streamIndex = streamIndex,
        )

        val legacyTrackId = AudioTrackResolverCommon.buildLegacyAudioTrackId(
            language = normalizedLanguage,
            title = trackTitle,
            codecLabel = codecLabel,
            channels = channels,
            sampleRate = sampleRate,
            bitrate = bitrate,
            streamIndex = streamIndex,
        )

        val audioMimeType = AudioCodecCompatibilityChecker.codecNameToMimeType(streamMeta?.mCodecName)
        val isSupported = AudioCodecCompatibilityChecker.isMimeTypeSupported(audioMimeType)
        val unsupportedReason = AudioCodecCompatibilityChecker.getUnsupportedReason(audioMimeType)

        val metadata = PlayerMetadata.AudioTrack(
            index = uiIndex,
            isSelected = isSelected,
            title = trackTitle,
            codecLabel = codecLabel,
            channels = channels,
            channelsLabel = channelsLabel,
            sampleRate = sampleRate,
            bitrate = bitrate,
            mimeType = streamMeta?.mCodecName,
            language = normalizedLanguage,
            trackId = stableTrackId,
            isSupported = isSupported,
            unsupportedReason = unsupportedReason
        )

        return AudioTrackCandidate(
            metadata = metadata,
            streamIndex = streamIndex,
            matchKeys = setOfNotNull(
                stableTrackId,
                legacyTrackId,
                streamIndex.toString(),
            ).filter { it.isNotBlank() }.toSet(),
        )
    }

    private fun extractStreamMeta(trackInfo: ITrackInfo): IjkMediaMeta.IjkStreamMeta? {
        return if (trackInfo is IjkTrackInfo) {
            try {
                val field = IjkTrackInfo::class.java.getDeclaredField("mStreamMeta")
                field.isAccessible = true
                field.get(trackInfo) as? IjkMediaMeta.IjkStreamMeta
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    internal fun getChannelCount(channelLayout: Long): Int? {
        if (channelLayout <= 0) return null
        return when (channelLayout) {
            IjkMediaMeta.AV_CH_LAYOUT_MONO -> 1
            IjkMediaMeta.AV_CH_LAYOUT_STEREO,
            IjkMediaMeta.AV_CH_LAYOUT_2POINT1,
            IjkMediaMeta.AV_CH_LAYOUT_STEREO_DOWNMIX -> 2

            IjkMediaMeta.AV_CH_LAYOUT_2_1,
            IjkMediaMeta.AV_CH_LAYOUT_SURROUND -> 3

            IjkMediaMeta.AV_CH_LAYOUT_3POINT1,
            IjkMediaMeta.AV_CH_LAYOUT_4POINT0,
            IjkMediaMeta.AV_CH_LAYOUT_2_2,
            IjkMediaMeta.AV_CH_LAYOUT_QUAD -> 4

            IjkMediaMeta.AV_CH_LAYOUT_4POINT1,
            IjkMediaMeta.AV_CH_LAYOUT_5POINT0 -> 5

            IjkMediaMeta.AV_CH_LAYOUT_HEXAGONAL,
            IjkMediaMeta.AV_CH_LAYOUT_5POINT1,
            IjkMediaMeta.AV_CH_LAYOUT_6POINT0 -> 6

            IjkMediaMeta.AV_CH_LAYOUT_6POINT1,
            IjkMediaMeta.AV_CH_LAYOUT_7POINT0 -> 7

            IjkMediaMeta.AV_CH_LAYOUT_7POINT1,
            IjkMediaMeta.AV_CH_LAYOUT_7POINT1_WIDE,
            IjkMediaMeta.AV_CH_LAYOUT_7POINT1_WIDE_BACK,
            IjkMediaMeta.AV_CH_LAYOUT_OCTAGONAL -> 8

            LOCAL_AV3A_LAYOUT_5POINT1POINT4 -> 10

            else -> java.lang.Long.bitCount(channelLayout).takeIf { it > 0 }
        }
    }

    internal fun getChannelLabel(channelLayout: Long): String? {
        if (channelLayout <= 0) return null
        return when (channelLayout) {
            IjkMediaMeta.AV_CH_LAYOUT_MONO -> "单声道"
            IjkMediaMeta.AV_CH_LAYOUT_STEREO -> "立体声"
            IjkMediaMeta.AV_CH_LAYOUT_2POINT1 -> "2.1 声道"
            IjkMediaMeta.AV_CH_LAYOUT_2_1 -> "立体声"
            IjkMediaMeta.AV_CH_LAYOUT_SURROUND -> "环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_3POINT1 -> "3.1 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_4POINT0 -> "4.0 四声道"
            IjkMediaMeta.AV_CH_LAYOUT_4POINT1 -> "4.1 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_2_2 -> "四声道"
            IjkMediaMeta.AV_CH_LAYOUT_QUAD -> "四声道"
            IjkMediaMeta.AV_CH_LAYOUT_5POINT0 -> "5.0 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_5POINT1 -> "5.1 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_6POINT0 -> "6.0 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_6POINT1 -> "6.1 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_7POINT0 -> "7.0 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_7POINT1 -> "7.1 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_7POINT1_WIDE -> "宽域 7.1 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_7POINT1_WIDE_BACK -> "后置 7.1 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_HEXAGONAL -> "六角环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_OCTAGONAL -> "八角环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_STEREO_DOWNMIX -> "立体声下混音"
            LOCAL_AV3A_LAYOUT_5POINT1POINT4 -> "5.1.4 全景声"
            else -> null
        }
    }
}
