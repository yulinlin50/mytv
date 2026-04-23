package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import top.yogiczy.mytv.core.util.utils.humanizeLanguage
import top.yogiczy.mytv.tv.ui.utils.Configs
import tv.danmaku.ijk.media.player.IjkMediaMeta
import tv.danmaku.ijk.media.player.misc.ITrackInfo
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo
import java.io.ByteArrayOutputStream

class IjkTrackInfoResolverTest {

    @Test
    fun `unknown audio tracks with duplicate stream index should stay separate`() {
        val first = createAudioTrack(streamIndex = 1, language = null)
        val second = createAudioTrack(streamIndex = 1, language = null)

        val resolved = IjkTrackInfoResolver.resolveAudioTracks(
            trackInfos = arrayOf(first, second),
            selectedAudioStreamIndex = -1,
            sortMode = Configs.AudioTrackSortMode.BITRATE,
        )

        assertEquals(2, resolved.size)
        assertNotEquals(resolved[0].metadata.trackId, resolved[1].metadata.trackId)
    }

    @Test
    fun `fallback ts language should be applied when ijk reports und`() {
        val track = createAudioTrack(streamIndex = 1, language = "und")

        val candidate = IjkTrackInfoResolver.buildAudioTrackCandidate(
            trackInfo = track,
            selectedAudioStreamIndex = 1,
            uiIndex = 0,
            fallbackLanguage = "yue",
        )

        assertEquals("yue", candidate.metadata.language)
        assertEquals("yue".humanizeLanguage(), candidate.metadata.title)
    }

    @Test
    fun `ts pmt parser should read iso639 audio languages in order`() {
        val tsData = buildTsSample(
            patSection = byteArrayOf(
                0x00, 0xB0.toByte(), 0x0D,
                0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
                0x00, 0x01, 0xE1.toByte(), 0x00,
                0x00, 0x00, 0x00, 0x00,
            ),
            pmtSection = byteArrayOf(
                0x02, 0xB0.toByte(), 0x23,
                0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
                0xE1.toByte(), 0x01, 0xF0.toByte(), 0x00,
                0x0F, 0xE1.toByte(), 0x01, 0xF0.toByte(), 0x06,
                0x0A, 0x04, 'y'.code.toByte(), 'u'.code.toByte(), 'e'.code.toByte(), 0x00,
                0x0F, 0xE1.toByte(), 0x02, 0xF0.toByte(), 0x06,
                0x0A, 0x04, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0x00,
                0x00, 0x00, 0x00, 0x00,
            ),
        )

        assertEquals(listOf("yue", "eng"), IjkTsAudioLanguageDetector.parseAudioLanguagesFromTsData(tsData))
    }

    private fun createAudioTrack(streamIndex: Int, language: String?): ITrackInfo {
        val streamMeta = IjkMediaMeta.IjkStreamMeta(streamIndex).apply {
            mType = IjkMediaMeta.IJKM_VAL_TYPE__AUDIO
            mLanguage = language
            mCodecName = "mp4a"
            mChannelLayout = IjkMediaMeta.AV_CH_LAYOUT_STEREO
            mSampleRate = 48_000
        }

        return IjkTrackInfo(streamMeta).apply {
            setTrackType(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO)
        }
    }

    private fun buildTsSample(patSection: ByteArray, pmtSection: ByteArray): ByteArray {
        return ByteArrayOutputStream().apply {
            write(buildPacket(pid = 0x0000, section = patSection))
            write(buildPacket(pid = 0x0100, section = pmtSection))
        }.toByteArray()
    }

    private fun buildPacket(pid: Int, section: ByteArray): ByteArray {
        val packet = ByteArray(188) { 0xFF.toByte() }
        packet[0] = 0x47.toByte()
        packet[1] = (0x40 or ((pid shr 8) and 0x1F)).toByte()
        packet[2] = (pid and 0xFF).toByte()
        packet[3] = 0x10.toByte()
        packet[4] = 0x00.toByte()
        section.copyInto(packet, destinationOffset = 5)
        return packet
    }
}
