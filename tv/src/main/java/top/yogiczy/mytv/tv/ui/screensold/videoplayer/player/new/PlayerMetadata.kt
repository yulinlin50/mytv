package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import top.yogiczy.mytv.core.util.utils.humanizeAudioChannels
import top.yogiczy.mytv.core.util.utils.humanizeBitrate
import top.yogiczy.mytv.core.util.utils.humanizeLanguage
import kotlin.math.roundToInt

data class PlayerMetadata(
    val video: VideoTrack? = null,
    val audio: AudioTrack? = null,
    val subtitle: SubtitleTrack? = null,
    val videoTracks: List<VideoTrack> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val subtitleTracks: List<SubtitleTrack> = emptyList()
) {
    interface TrackSelectable {
        val trackIndex: Int?
        val trackIsSelected: Boolean?
        val trackLabel: String
        val trackIsSupported: Boolean get() = true
    }

    data class VideoTrack(
        val index: Int? = null,
        val isSelected: Boolean? = null,
        val width: Int? = null,
        val height: Int? = null,
        val color: String? = null,
        val frameRate: Float? = null,
        val bitrate: Int? = null,
        val mimeType: String? = null,
        val decoder: String? = null,
        val trackId: String? = null
    ) : TrackSelectable {
        override val trackIndex: Int? get() = index
        override val trackIsSelected: Boolean? get() = isSelected
        override val trackLabel: String get() = shortLabel
        override fun equals(other: Any?): Boolean {
            if (other !is VideoTrack) return false
            if (trackId != null && other.trackId != null) return trackId == other.trackId
            return width == other.width && height == other.height && 
                   frameRate == other.frameRate && bitrate == other.bitrate && mimeType == other.mimeType
        }

        override fun hashCode(): Int = trackId?.hashCode() ?: (width ?: 0)

        val shortLabel: String
            get() = listOfNotNull(
                "${width}x$height",
                mimeType?.substringAfter("/")?.takeIf { it.all { c -> c.code <= 0x7F } },
                frameRate?.takeIf { it > 0 }?.let { "${it.roundToInt()}fps" },
                bitrate?.takeIf { it > 0 }?.humanizeBitrate()
            ).joinToString(", ")
    }

    data class AudioTrack(
        val index: Int? = null,
        val isSelected: Boolean? = null,
        val title: String? = null,
        val roleLabel: String? = null,
        val codecLabel: String? = null,
        val channels: Int? = null,
        val channelsLabel: String? = null,
        val sampleRate: Int? = null,
        val bitrate: Int? = null,
        val mimeType: String? = null,
        val language: String? = null,
        val decoder: String? = null,
        val trackId: String? = null,
        val isSupported: Boolean = true,
        val unsupportedReason: String? = null
    ) : TrackSelectable {
        override val trackIndex: Int? get() = index
        override val trackIsSelected: Boolean? get() = isSelected
        override val trackLabel: String get() = shortLabel
        override val trackIsSupported: Boolean get() = isSupported
        override fun equals(other: Any?): Boolean {
            if (other !is AudioTrack) return false
            if (trackId != null && other.trackId != null) return trackId == other.trackId
            return index == other.index
        }

        override fun hashCode(): Int = trackId?.hashCode() ?: (index ?: 0)

        val shortLabel: String
            get() {
                val parts = mutableListOf<String>()
                
                val cleanTitle = AudioTrackResolverCommon.sanitizeAudioTitle(title)
                    ?.takeIf { it != language?.trim()?.lowercase() }
                if (cleanTitle != null) parts.add(cleanTitle)
                
                AudioTrackResolverCommon.normalizeTrackLanguage(language)
                    ?.humanizeLanguage()
                    ?.takeIf { it != parts.firstOrNull() }
                    ?.let { parts.add(it) }
                
                if (!roleLabel.isNullOrBlank()) parts.add(roleLabel)
                (channelsLabel ?: channels?.humanizeAudioChannels())?.takeIf { !it.isNullOrBlank() }?.let { parts.add(it) }
                (codecLabel ?: mimeType?.substringAfter("/")?.takeIf { it.all { c -> c.code <= 0x7F } })
                    ?.takeIf { !it.isNullOrBlank() }?.let { parts.add(it) }
                bitrate?.takeIf { it > 0 }?.humanizeBitrate()?.let { parts.add(it) }
                
                return parts.distinct().joinToString(" · ")
            }
    }

    data class SubtitleTrack(
        val index: Int? = null,
        val isSelected: Boolean? = null,
        val bitrate: Int? = null,
        val mimeType: String? = null,
        val language: String? = null,
        val trackId: String? = null
    ) : TrackSelectable {
        override val trackIndex: Int? get() = index
        override val trackIsSelected: Boolean? get() = isSelected
        override val trackLabel: String get() = shortLabel
        override fun equals(other: Any?): Boolean {
            if (other !is SubtitleTrack) return false
            if (trackId != null && other.trackId != null) return trackId == other.trackId
            return bitrate == other.bitrate && mimeType == other.mimeType && language == other.language
        }

        override fun hashCode(): Int = trackId?.hashCode() ?: (language?.hashCode() ?: 0)

        val shortLabel: String
            get() = listOfNotNull(language?.humanizeLanguage()).joinToString(", ")
    }
}
