package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import org.junit.Assert.*
import org.junit.Test

class VideoPlayerNewTest {
    
    @Test
    fun testAudioTrackMemoryCache_sizeLimit() {
        val cache = AudioTrackMemoryCache(maxSize = 10)
        
        repeat(20) { i ->
            cache.put("url$i", "track$i")
        }
        
        assertTrue("Cache size should not exceed maxSize", cache.size() <= 10)
        
        assertNull("Oldest entries should be evicted", cache.get("url0"))
        assertNotNull("Newest entries should exist", cache.get("url19"))
    }
    
    @Test
    fun testAudioTrackMemoryCache_basicOperations() {
        val cache = AudioTrackMemoryCache(maxSize = 10)
        
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
    fun testAudioTrackMemoryCache_persistence() {
        val cache1 = AudioTrackMemoryCache(maxSize = 10)
        cache1.put("url1", "track1")
        cache1.put("url2", "track2")
        
        val jsonString = cache1.toJsonString()
        assertNotNull("JSON string should not be null", jsonString)
        assertTrue("JSON string should not be empty", jsonString.isNotEmpty())
        
        val cache2 = AudioTrackMemoryCache.fromJsonString(jsonString)
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
        
        val label = audioTrack.shortLabel
        assertNotNull("Label should not be null", label)
        assertTrue("Label should not be empty", label.isNotEmpty())
    }
    
    @Test
    fun testPlayerErrorType_userFriendlyMessages() {
        val networkError = PlayerErrorType.NetworkError(100, "Network failed")
        assertEquals("Should return user friendly message", 
            "网络连接失败，请检查网络设置", 
            networkError.getUserFriendlyMessage())
        
        val decoderError = PlayerErrorType.DecoderError(200, "Decoder failed")
        assertEquals("Should return user friendly message", 
            "视频解码失败，正在尝试其他方式", 
            decoderError.getUserFriendlyMessage())
        
        val timeoutError = PlayerErrorType.TimeoutError(300, "Timeout")
        assertEquals("Should return user friendly message", 
            "加载超时，请稍后重试", 
            timeoutError.getUserFriendlyMessage())
        
        val formatError = PlayerErrorType.FormatError(400, "Format error")
        assertEquals("Should return user friendly message", 
            "不支持的视频格式", 
            formatError.getUserFriendlyMessage())
        
        val drmError = PlayerErrorType.DrmError(500, "DRM error")
        assertEquals("Should return user friendly message", 
            "版权保护验证失败", 
            drmError.getUserFriendlyMessage())
        
        val playbackError = PlayerErrorType.PlaybackError(600, "Playback error")
        assertEquals("Should return user friendly message", 
            "回放内容已过期", 
            playbackError.getUserFriendlyMessage())
        
        val unknownError = PlayerErrorType.UnknownError(999, "Unknown error")
        assertTrue("Should contain error message", 
            unknownError.getUserFriendlyMessage().contains("Unknown error"))
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
