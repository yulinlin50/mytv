package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer

internal object TrackExtractor {
    fun extractVideoTracks(player: ExoPlayer?): List<PlayerMetadata.VideoTrack> {
        val currentTrackId = player?.videoFormat?.id
        return extractTracks(
            player = player,
            trackType = C.TRACK_TYPE_VIDEO,
            currentTrackId = currentTrackId,
            formatToTrack = { it.toVideoMetadata() },
        )
    }

    fun extractAudioTracks(player: ExoPlayer?): List<PlayerMetadata.AudioTrack> {
        val currentTrackId = player?.audioFormat?.id
        return extractTracks(
            player = player,
            trackType = C.TRACK_TYPE_AUDIO,
            currentTrackId = currentTrackId,
            formatToTrack = { it.toAudioMetadata() },
        )
    }

    fun extractSubtitleTracks(player: ExoPlayer?): List<PlayerMetadata.SubtitleTrack> {
        val currentTrackId = player?.currentTracks?.groups
            ?.filter { it.type == C.TRACK_TYPE_TEXT }
            ?.flatMap { group ->
                (0 until group.mediaTrackGroup.length).mapNotNull { trackIndex ->
                    if (group.isTrackSelected(trackIndex)) {
                        group.mediaTrackGroup.getFormat(trackIndex)
                            .takeIf { (it.roleFlags and C.ROLE_FLAG_SUBTITLE) != 0 }
                    } else null
                }
            }
            ?.firstOrNull()?.id

        return extractTracks(
            player = player,
            trackType = C.TRACK_TYPE_TEXT,
            currentTrackId = currentTrackId,
            formatToTrack = { it.toSubtitleMetadata() },
            filterFormat = { (it.roleFlags and C.ROLE_FLAG_SUBTITLE) != 0 },
        )
    }

    private inline fun <T : PlayerMetadata.TrackSelectable> extractTracks(
        player: ExoPlayer?,
        trackType: Int,
        currentTrackId: String?,
        crossinline formatToTrack: (Format) -> T,
        filterFormat: (Format) -> Boolean = { true },
    ): List<T> {
        return player?.currentTracks?.groups
            ?.filter { it.type == trackType }
            ?.flatMap { group ->
                (0 until group.mediaTrackGroup.length).mapNotNull { trackIndex ->
                    val format = group.mediaTrackGroup.getFormat(trackIndex)
                    if (!filterFormat(format)) return@mapNotNull null
                    val isSelected = if (group.length > 1 && group.isTrackSelected(trackIndex)) {
                        currentTrackId != null && format.id == currentTrackId
                    } else {
                        group.isTrackSelected(trackIndex)
                    }
                    formatToTrack(format).let { track ->
                        @Suppress("UNCHECKED_CAST")
                        when (track) {
                            is PlayerMetadata.VideoTrack -> track.copy(isSelected = isSelected) as T
                            is PlayerMetadata.AudioTrack -> track.copy(isSelected = isSelected) as T
                            is PlayerMetadata.SubtitleTrack -> track.copy(isSelected = isSelected) as T
                            else -> track
                        }
                    }
                }
            }
            ?.mapIndexed { index, track ->
                @Suppress("UNCHECKED_CAST")
                when (track) {
                    is PlayerMetadata.VideoTrack -> track.copy(index = index) as T
                    is PlayerMetadata.AudioTrack -> track.copy(index = index) as T
                    is PlayerMetadata.SubtitleTrack -> track.copy(index = index) as T
                    else -> track
                }
            }
            ?: emptyList()
    }
}

internal fun Format.toVideoMetadata(): PlayerMetadata.VideoTrack {
    val codecs = this.codecs ?: ""
    val isDolbyVision = codecs.startsWith("dvh1") || codecs.startsWith("dvhe") ||
            sampleMimeType == "video/dolby-vision"
    return PlayerMetadata.VideoTrack(
        width = width,
        height = height,
        frameRate = frameRate,
        bitrate = bitrate,
        mimeType = sampleMimeType,
        trackId = id ?: "$sampleMimeType-$width-$height-$frameRate-$bitrate",
        isDolbyVision = isDolbyVision
    )
}

internal fun Format.toAudioMetadata(): PlayerMetadata.AudioTrack {
    return PlayerMetadata.AudioTrack(
        channels = channelCount,
        sampleRate = sampleRate,
        bitrate = bitrate,
        mimeType = sampleMimeType,
        language = language,
        trackId = id ?: "$sampleMimeType-$language-$bitrate"
    )
}

internal fun Format.toSubtitleMetadata(): PlayerMetadata.SubtitleTrack {
    return PlayerMetadata.SubtitleTrack(
        bitrate = bitrate,
        mimeType = sampleMimeType,
        language = language,
        trackId = id ?: "$sampleMimeType-$language-$bitrate"
    )
}