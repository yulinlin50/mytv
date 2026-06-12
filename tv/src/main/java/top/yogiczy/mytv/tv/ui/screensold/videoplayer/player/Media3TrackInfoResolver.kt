package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.HlsTrackMetadataEntry
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.AudioCodecCompatibilityChecker
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.AudioTrackCandidate
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.AudioTrackResolverCommon
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.PlayerMetadata
import top.yogiczy.mytv.tv.ui.utils.Configs

@OptIn(UnstableApi::class)
internal object Media3TrackInfoResolver {

    internal fun resolveTrackType(group: Tracks.Group): @C.TrackType Int {
        if (group.mediaTrackGroup.type != C.TRACK_TYPE_UNKNOWN) return group.mediaTrackGroup.type

        var hasText = false
        var hasAudio = false
        var hasVideo = false

        for (trackIndex in 0 until group.mediaTrackGroup.length) {
            when (resolveTrackType(group.mediaTrackGroup.getFormat(trackIndex))) {
                C.TRACK_TYPE_TEXT -> hasText = true
                C.TRACK_TYPE_AUDIO -> hasAudio = true
                C.TRACK_TYPE_VIDEO -> hasVideo = true
            }
        }

        return when {
            hasVideo -> C.TRACK_TYPE_VIDEO
            hasAudio -> C.TRACK_TYPE_AUDIO
            hasText -> C.TRACK_TYPE_TEXT
            else -> C.TRACK_TYPE_UNKNOWN
        }
    }

    internal fun resolveTrackType(format: Format): @C.TrackType Int {
        format.sampleMimeType?.let { mimeType ->
            MimeTypes.getTrackType(mimeType)
                .takeIf { it != C.TRACK_TYPE_UNKNOWN }
                ?.let { return it }
        }

        format.containerMimeType?.let { mimeType ->
            MimeTypes.getTrackType(mimeType)
                .takeIf { it != C.TRACK_TYPE_UNKNOWN }
                ?.let { return it }
        }

        if (format.roleFlags and (C.ROLE_FLAG_SUBTITLE or C.ROLE_FLAG_CAPTION) != 0) {
            return C.TRACK_TYPE_TEXT
        }

        return C.TRACK_TYPE_UNKNOWN
    }

    internal fun buildAudioTrackCandidate(
        format: Format,
        audio: PlayerMetadata.AudioTrack? = null,
    ): AudioTrackCandidate {
        val hlsInfo = format.metadata.toHlsAudioInfo()
        val normalizedLanguage = AudioTrackResolverCommon.normalizeTrackLanguage(format.language) ?: audio?.language
        val trackTitle = AudioTrackResolverCommon.selectAudioTitle(format.label, hlsInfo.name) ?: audio?.title
        val groupId = hlsInfo.groupId?.takeIfMeaningful()
        val audioGroupId = hlsInfo.audioGroupId?.takeIfMeaningful()
        val bitrate = listOfNotNull(
            format.bitrate.takeIfPositive(),
            format.averageBitrate.takeIfPositive(),
            format.peakBitrate.takeIfPositive(),
            hlsInfo.averageBitrate,
            hlsInfo.peakBitrate,
            audio?.bitrate?.takeIfPositive(),
        ).firstOrNull()
        val codecLabel = format.codecs.toAudioCodecLabel(format.sampleMimeType ?: format.containerMimeType)
            ?: audio?.codecLabel

        val stableTrackId = AudioTrackResolverCommon.buildAudioTrackId(
            mimeType = format.sampleMimeType ?: format.containerMimeType,
            language = normalizedLanguage,
            title = trackTitle,
            groupId = groupId ?: audioGroupId,
            codecLabel = codecLabel,
            channels = format.channelCount.takeIfPositive() ?: audio?.channels,
            roleFlags = format.roleFlags,
            sampleRate = format.sampleRate.takeIfPositive() ?: audio?.sampleRate,
            bitrate = bitrate,
            codecs = format.codecs,
            selectionFlags = format.selectionFlags,
        )

        val audioMimeType = format.sampleMimeType ?: format.containerMimeType ?: audio?.mimeType
        val isSupported = AudioCodecCompatibilityChecker.isMimeTypeSupported(audioMimeType)
        val unsupportedReason = AudioCodecCompatibilityChecker.getUnsupportedReason(audioMimeType)

        val metadata = (audio ?: PlayerMetadata.AudioTrack()).copy(
            title = trackTitle,
            roleLabel = listOfNotNull(
                format.roleFlags.toAudioRoleLabel(),
                format.selectionFlags.toSelectionLabel(),
                groupId?.let { "组$it" },
                audioGroupId?.takeUnless { it == groupId }?.let { "音频组$it" },
            ).distinct().joinToString("/").takeIf { it.isNotBlank() } ?: audio?.roleLabel,
            codecLabel = codecLabel,
            channels = format.channelCount.takeIfPositive() ?: audio?.channels,
            sampleRate = format.sampleRate.takeIfPositive() ?: audio?.sampleRate,
            bitrate = bitrate,
            mimeType = audioMimeType,
            language = normalizedLanguage,
            trackId = stableTrackId,
            isSupported = isSupported,
            unsupportedReason = unsupportedReason
        )

        return AudioTrackCandidate(
            metadata = metadata,
            matchKeys = listOfNotNull(
                metadata.trackId,
                format.id?.trim()?.takeIf { it.isNotEmpty() },
            ).filter { it.isNotBlank() }.toSet(),
        )
    }

    internal fun dedupeAndSortAudioTracks(
        tracks: List<AudioTrackCandidate>,
        sortMode: Configs.AudioTrackSortMode,
    ): List<AudioTrackCandidate> {
        return AudioTrackResolverCommon.dedupeAndSortAudioTracks(tracks, sortMode)
    }

    private fun String?.takeIfMeaningful(): String? {
        val trimmed = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return trimmed.takeUnless { it.lowercase() in setOf("und", "unknown", "null") }
    }

    private fun Int.takeIfPositive(): Int? = takeIf { it > 0 }

    private fun String?.toAudioCodecLabel(sampleMimeType: String?): String? {
        return with(AudioTrackResolverCommon) { this@toAudioCodecLabel.toAudioCodecLabel() }
            ?: sampleMimeType.toAudioMimeTypeLabel()
    }

    private fun String?.toAudioMimeTypeLabel(): String? {
        return when (this) {
            MimeTypes.AUDIO_AAC -> "AAC"
            MimeTypes.AUDIO_MPEG_L2, MimeTypes.AUDIO_MPEG_L1, MimeTypes.AUDIO_MPEG -> "MPEG"
            MimeTypes.AUDIO_AC3 -> "AC3"
            MimeTypes.AUDIO_E_AC3 -> "E-AC3"
            MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Atmos"
            MimeTypes.AUDIO_AC4 -> "AC4"
            MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_EXPRESS, MimeTypes.AUDIO_DTS_HD -> "DTS"
            MimeTypes.AUDIO_TRUEHD -> "TrueHD"
            MimeTypes.AUDIO_MPEGH_MHA1, MimeTypes.AUDIO_MPEGH_MHM1 -> "MPEG-H"
            MimeTypes.AUDIO_OPUS -> "Opus"
            MimeTypes.AUDIO_VORBIS -> "Vorbis"
            MimeTypes.AUDIO_FLAC -> "FLAC"
            MimeTypes.AUDIO_ALAC -> "ALAC"
            MimeTypes.AUDIO_RAW -> "PCM"
            else -> this?.substringAfter("/")
        }
    }

    private fun Int.toAudioRoleLabel(): String? {
        val labels = buildList {
            if (this@toAudioRoleLabel and C.ROLE_FLAG_MAIN != 0) add("主音轨")
            if (this@toAudioRoleLabel and C.ROLE_FLAG_ALTERNATE != 0) add("备用")
            if (this@toAudioRoleLabel and C.ROLE_FLAG_SUPPLEMENTARY != 0) add("补充")
            if (this@toAudioRoleLabel and C.ROLE_FLAG_COMMENTARY != 0) add("解说")
            if (this@toAudioRoleLabel and C.ROLE_FLAG_DUB != 0) add("配音")
            if (this@toAudioRoleLabel and C.ROLE_FLAG_DESCRIBES_VIDEO != 0) add("音频描述")
            if (this@toAudioRoleLabel and C.ROLE_FLAG_EASY_TO_READ != 0) add("易读")
            if (this@toAudioRoleLabel and C.ROLE_FLAG_EMERGENCY != 0) add("应急")
        }
        return labels.distinct().takeIf { it.isNotEmpty() }?.joinToString("/")
    }

    private fun Int.toSelectionLabel(): String? {
        val labels = buildList {
            if (this@toSelectionLabel and C.SELECTION_FLAG_DEFAULT != 0) add("默认")
            if (this@toSelectionLabel and C.SELECTION_FLAG_AUTOSELECT != 0) add("自动")
            if (this@toSelectionLabel and C.SELECTION_FLAG_FORCED != 0) add("强制")
        }
        return labels.distinct().takeIf { it.isNotEmpty() }?.joinToString("/")
    }

    private data class HlsAudioInfo(
        val groupId: String? = null,
        val name: String? = null,
        val audioGroupId: String? = null,
        val averageBitrate: Int? = null,
        val peakBitrate: Int? = null,
    )

    private fun androidx.media3.common.Metadata?.toHlsAudioInfo(): HlsAudioInfo {
        if (this == null) return HlsAudioInfo()

        var groupId: String? = null
        var name: String? = null
        var audioGroupId: String? = null
        var averageBitrate: Int? = null
        var peakBitrate: Int? = null

        for (index in 0 until length()) {
            val entry = get(index)
            if (entry !is HlsTrackMetadataEntry) continue

            if (groupId == null) groupId = entry.groupId?.takeIfMeaningful()
            if (name == null) name = entry.name?.takeIfMeaningful()

            entry.variantInfos.forEach { variantInfo ->
                if (audioGroupId == null) audioGroupId = variantInfo.audioGroupId?.takeIfMeaningful()
                if (averageBitrate == null) averageBitrate = variantInfo.averageBitrate.takeIfPositive()
                if (peakBitrate == null) peakBitrate = variantInfo.peakBitrate.takeIfPositive()
            }
        }

        return HlsAudioInfo(
            groupId = groupId,
            name = name,
            audioGroupId = audioGroupId,
            averageBitrate = averageBitrate,
            peakBitrate = peakBitrate,
        )
    }
}
