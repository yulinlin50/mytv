package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.HlsTrackMetadataEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.util.utils.humanizeLanguage
import top.yogiczy.mytv.tv.ui.utils.Configs

@OptIn(UnstableApi::class)
class Media3TrackInfoResolverTest {

    @Test
    fun `muxed variant with video dimensions should stay video`() {
        val format = Format.Builder()
            .setCodecs("avc1.640029,mp4a.40.2")
            .setWidth(1920)
            .setHeight(1080)
            .build()

        assertEquals(C.TRACK_TYPE_VIDEO, Media3TrackInfoResolver.resolveTrackType(format))
    }

    @Test
    fun `audio candidate should prefer readable hls metadata`() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setLanguage("en-US")
            .setLabel("en-US")
            .setMetadata(
                Metadata(
                    HlsTrackMetadataEntry(
                        "audio-stereo",
                        "English",
                        listOf(
                            HlsTrackMetadataEntry.VariantInfo(
                                128000,
                                192000,
                                null,
                                "audio-stereo",
                                null,
                                null,
                            )
                        ),
                    )
                )
            )
            .build()

        val candidate = Media3TrackInfoResolver.buildAudioTrackCandidate(format)

        assertEquals("English", candidate.metadata.title)
        assertEquals(128000, candidate.metadata.bitrate)
        assertTrue(candidate.metadata.shortLabel.contains("English"))
        assertFalse(candidate.metadata.shortLabel.contains("en-US"))
    }

    @Test
    fun `duplicate logical tracks should merge and keep selection`() {
        val trackId = "audio-audio_aac-en-english-aac-2"
        val tracks = listOf(
            Media3TrackInfoResolver.AudioTrackCandidate(
                metadata = VideoPlayer.Metadata.Audio(
                    index = 0,
                    isSelected = false,
                    title = "English",
                    language = "en",
                    codecLabel = "AAC",
                    channels = 2,
                    bitrate = 128000,
                    trackId = trackId,
                ),
                matchKeys = setOf(trackId, "legacy-a"),
            ),
            Media3TrackInfoResolver.AudioTrackCandidate(
                metadata = VideoPlayer.Metadata.Audio(
                    index = 1,
                    isSelected = true,
                    title = "English",
                    language = "en",
                    codecLabel = "AAC",
                    channels = 2,
                    bitrate = 192000,
                    trackId = trackId,
                ),
                matchKeys = setOf(trackId, "legacy-b"),
            ),
        )

        val result = Media3TrackInfoResolver.dedupeAndSortAudioTracks(
            tracks,
            Configs.AudioTrackSortMode.BITRATE,
        )

        assertEquals(1, result.size)
        assertTrue(result.single().metadata.isSelected == true)
        assertEquals(setOf(trackId, "legacy-a", "legacy-b"), result.single().matchKeys)
    }

    @Test
    fun `language tags should be humanized consistently`() {
        assertEquals("简体中文", "zh-Hans-CN".humanizeLanguage())
        assertEquals("英语", "eng".humanizeLanguage())
        assertEquals("粤语", "zh-yue".humanizeLanguage())
    }
}
