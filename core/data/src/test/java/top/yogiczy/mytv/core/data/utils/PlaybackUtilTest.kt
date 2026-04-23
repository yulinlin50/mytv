package top.yogiczy.mytv.core.data.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme

class PlaybackUtilTest {

    @Test
    fun `plain live query params should not be treated as playback`() {
        val liveUrl = "https://example.com/live.m3u8?start=1742995200&token=abc&from=web"

        assertFalse(PlaybackUtil.isPlaybackUrl(liveUrl))
    }

    @Test
    fun `paired playback params should still be treated as playback`() {
        val playbackUrl = "https://example.com/timeshift/channel.m3u8?utcstart=20260327080000&utcend=20260327090000"

        assertTrue(PlaybackUtil.isPlaybackUrl(playbackUrl))
    }

    @Test
    fun `generic playback words should not advertise replay button`() {
        val line = ChannelLine(url = "https://example.com/live/playback-service/index.m3u8")

        assertFalse(PlaybackUtil.isPlaybackAdvertised(line))
    }

    @Test
    fun `catchup days alone should not advertise replay button`() {
        val line = ChannelLine(
            url = "https://example.com/live/channel.m3u8",
            catchupDays = 7
        )

        assertFalse(PlaybackUtil.isPlaybackAdvertised(line))
        assertTrue(PlaybackUtil.isPlaybackSupported(line))
    }

    @Test
    fun `recognized catchup metadata should advertise replay button`() {
        val line = ChannelLine(
            url = "https://example.com/live/channel.m3u8",
            catchup = "append"
        )

        assertTrue(PlaybackUtil.isPlaybackAdvertised(line))
        assertTrue(PlaybackUtil.isCatchupSupported(line))
    }

    // ==================== 时区偏移测试 ====================

    @Test
    fun `applyTimezoneShift with positive shift`() {
        val timeMs = 946684800000L // 2000-01-01 00:00:00 UTC
        val shiftHours = 8.0 // 东八区
        val result = PlaybackUtil.applyTimezoneShift(timeMs, shiftHours)

        assertEquals(timeMs + (8 * 60 * 60 * 1000).toLong(), result)
    }

    @Test
    fun `applyTimezoneShift with negative shift`() {
        val timeMs = 946684800000L
        val shiftHours = -5.0 // 西五区
        val result = PlaybackUtil.applyTimezoneShift(timeMs, shiftHours)

        assertEquals(timeMs - (5 * 60 * 60 * 1000).toLong(), result)
    }

    @Test
    fun `applyTimezoneShift with null shift`() {
        val timeMs = 946684800000L
        val result = PlaybackUtil.applyTimezoneShift(timeMs, null)

        assertEquals(timeMs, result)
    }

    // ==================== 时间范围提取测试 ====================

    @Test
    fun `extractPlaybackTimeRange from playseek format`() {
        val url = "https://example.com/channel.m3u8?playseek=20260327080000-20260327090000"
        val result = PlaybackUtil.extractPlaybackTimeRange(url)

        assertNotNull(result)
        assertTrue(result!!.first > 0)
        assertTrue(result.second > result.first)
    }

    @Test
    fun `extractPlaybackTimeRange from flussonic format`() {
        val url = "https://example.com/channel.m3u8?from=1711526400&to=1711530000"
        val result = PlaybackUtil.extractPlaybackTimeRange(url)

        assertNotNull(result)
        assertEquals(1711526400000L, result!!.first)
        assertEquals(1711530000000L, result.second)
    }

    @Test
    fun `extractPlaybackTimeRange from non-playback url returns null`() {
        val url = "https://example.com/live/channel.m3u8"
        val result = PlaybackUtil.extractPlaybackTimeRange(url)

        assertNull(result)
    }

    // ==================== URL 生成测试 ====================

    @Test
    fun `generatePlaybackUrl with playseek format`() {
        val channelLine = ChannelLine(
            url = "https://example.com/channel.m3u8?playseek=placeholder",
            catchup = "default"
        )
        val startTime = 946684800000L // 2000-01-01 00:00:00 UTC = 946684800 seconds
        val endTime = 946688400000L   // 2000-01-01 01:00:00 UTC = 946688400 seconds

        val result = PlaybackUtil.generatePlaybackUrl(channelLine, startTime, endTime, true)

        assertTrue(result.contains("playseek="))
        // 现在使用Unix时间戳（秒级），而不是14位格式化时间
        assertTrue(result.contains("946684800-946688400"))
    }

    @Test
    fun `generatePlaybackUrl with append mode`() {
        val channelLine = ChannelLine(
            url = "https://example.com/channel.m3u8",
            catchup = "append"
        )
        val startTime = 946684800000L
        val endTime = 946688400000L

        val result = PlaybackUtil.generatePlaybackUrl(channelLine, startTime, endTime, true)

        assertTrue(result.contains("start="))
        assertTrue(result.contains("end="))
    }

    // ==================== 常量测试 ====================

    @Test
    fun `unix timestamp threshold is correct`() {
        // 2000-01-01 00:00:00 UTC = 946684800 seconds = 946684800000 milliseconds
        assertEquals(946684800000L, PlaybackUtil.UNIX_TIMESTAMP_THRESHOLD_MS)
        assertEquals(946684800L, PlaybackUtil.UNIX_TIMESTAMP_THRESHOLD_S)
    }

    @Test
    fun `max playback duration is 48 hours`() {
        val expectedMs = 48L * 60 * 60 * 1000
        assertEquals(expectedMs, PlaybackUtil.MAX_PLAYBACK_DURATION_MS)
    }

    @Test
    fun `max timeshift duration is 2 hours`() {
        val expectedMs = 2L * 60 * 60 * 1000
        assertEquals(expectedMs, PlaybackUtil.MAX_TIMESHIFT_DURATION_MS)
    }
}
