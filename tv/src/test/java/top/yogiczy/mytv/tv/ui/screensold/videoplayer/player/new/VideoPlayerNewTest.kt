package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import org.junit.Assert.*
import org.junit.Test

class VideoPlayerNewTest {
    
    @Test
    fun testAudioTrackCache_TrackIdCache_sizeLimit() {
        val cache = AudioTrackCache.TrackIdCache(maxSize = 10)
        
        repeat(20) { i ->
            cache.put("url$i", "track$i")
        }
        
        assertTrue("Cache size should not exceed maxSize", cache.size() <= 10)
        
        assertNull("Oldest entries should be evicted", cache.get("url0"))
        assertNotNull("Newest entries should exist", cache.get("url19"))
    }
    
    @Test
    fun testAudioTrackCache_TrackIdCache_basicOperations() {
        val cache = AudioTrackCache.TrackIdCache(maxSize = 10)
        
        assertNull("Empty cache should return null", cache.get("url1"))
        
        cache.put("url1", "track1")
        assertEquals("Should return correct value", "track1", cache.get("url1"))
        
        cache.remove("url1")
        assertNull("Removed entry should return null", cache.get("url1"))
        
        cache.put("url1", "track1")
        cache.put("url2", "track2")
        cache.clear()
        assertEquals("Cache should be empty after clear", 0, cache.size())
    }
    
    @Test
    fun testAudioTrackCache_TrackIdCache_persistence() {
        val cache1 = AudioTrackCache.TrackIdCache(maxSize = 10)
        cache1.put("url1", "track1")
        cache1.put("url2", "track2")
        
        val jsonString = cache1.toJsonString()
        assertNotNull("JSON string should not be null", jsonString)
        assertTrue("JSON string should not be empty", jsonString.isNotEmpty())
        
        val cache2 = AudioTrackCache.TrackIdCache.fromJsonString(jsonString)
        assertEquals("Cache should be restored correctly", cache1.get("url1"), cache2.get("url1"))
        assertEquals("Cache should be restored correctly", cache1.get("url2"), cache2.get("url2"))
    }
    
    @Test
    fun testPlayerMetadata_equality() {
        val track1 = PlayerMetadata.VideoTrack(
            trackId = "track1",
            width = 1920,
            height = 1080
        )
        
        val track2 = PlayerMetadata.VideoTrack(
            trackId = "track1",
            width = 1280,
            height = 720
        )
        
        val track3 = PlayerMetadata.VideoTrack(
            trackId = "track2",
            width = 1920,
            height = 1080
        )
        
        assertEquals("Tracks with same trackId should be equal", track1, track2)
        assertNotEquals("Tracks with different trackId should not be equal", track1, track3)
    }
    
    @Test
    fun testPlayerMetadata_hashCode() {
        val track1 = PlayerMetadata.VideoTrack(
            trackId = "track1",
            width = 1920,
            height = 1080
        )
        
        val track2 = PlayerMetadata.VideoTrack(
            trackId = "track1",
            width = 1280,
            height = 720
        )
        
        assertEquals("Tracks with same trackId should have same hashCode", track1.hashCode(), track2.hashCode())
    }
    
    @Test
    fun testPlayerMetadata_shortLabel() {
        val videoTrack = PlayerMetadata.VideoTrack(
            width = 1920,
            height = 1080,
            frameRate = 30f,
            bitrate = 5000000,
            mimeType = "video/avc"
        )
        
        val label = videoTrack.shortLabel
        assertTrue("Label should contain resolution", label.contains("1920x1080"))
        assertTrue("Label should contain mime type", label.contains("avc"))
        assertTrue("Label should contain frame rate", label.contains("30fps"))
    }
    
    @Test
    fun testPlayerMetadata_audioTrackLabel() {
        val audioTrack = PlayerMetadata.AudioTrack(
            language = "zh",
            channels = 2,
            bitrate = 128000,
            mimeType = "audio/aac"
        )
        
        val label = audioTrack.formatLabel()
        assertNotNull("Label should not be null", label)
        assertTrue("Label should not be empty", label.isNotEmpty())
    }
    
    @Test
    fun testPlayerErrorType_userFriendlyMessages() {
        assertEquals("Should return user friendly message",
            "网络连接失败，请检查网络设置",
            PlayerErrorType.NETWORK.getUserFriendlyMessage("test"))
        
        assertEquals("Should return user friendly message",
            "视频解码失败，正在尝试其他方式",
            PlayerErrorType.DECODER.getUserFriendlyMessage("test"))
        
        assertEquals("Should return user friendly message",
            "加载超时，请稍后重试",
            PlayerErrorType.TIMEOUT.getUserFriendlyMessage("test"))
        
        assertEquals("Should return user friendly message",
            "不支持的视频格式",
            PlayerErrorType.FORMAT.getUserFriendlyMessage("test"))
        
        assertEquals("Should return user friendly message",
            "版权保护验证失败",
            PlayerErrorType.DRM.getUserFriendlyMessage("test"))
        
        assertEquals("Should return user friendly message",
            "回放内容已过期",
            PlayerErrorType.PLAYBACK.getUserFriendlyMessage("test"))
        
        assertTrue("Should contain error message",
            PlayerErrorType.UNKNOWN.getUserFriendlyMessage("Unknown error").contains("Unknown error"))
    }
    
    @Test
    fun testPlayerMetadata_defaultValues() {
        val metadata = PlayerMetadata()
        
        assertNull("Default video should be null", metadata.video)
        assertNull("Default audio should be null", metadata.audio)
        assertNull("Default subtitle should be null", metadata.subtitle)
        assertTrue("Default videoTracks should be empty", metadata.videoTracks.isEmpty())
        assertTrue("Default audioTracks should be empty", metadata.audioTracks.isEmpty())
        assertTrue("Default subtitleTracks should be empty", metadata.subtitleTracks.isEmpty())
    }
}
