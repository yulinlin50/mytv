package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import android.util.LruCache
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import top.yogiczy.mytv.core.data.utils.Globals

class AudioTrackMemoryCache(
    maxSize: Int = 100
) {
    private val cache = LruCache<String, String>(maxSize)
    private val lock = Any()
    
    fun get(url: String): String? {
        return synchronized(lock) {
            cache.get(url)
        }
    }
    
    fun put(url: String, trackId: String) {
        synchronized(lock) {
            cache.put(url, trackId)
        }
    }
    
    fun remove(url: String) {
        synchronized(lock) {
            cache.remove(url)
        }
    }
    
    fun clear() {
        synchronized(lock) {
            cache.evictAll()
        }
    }
    
    fun size(): Int {
        return synchronized(lock) {
            cache.size()
        }
    }
    
    fun toMap(): Map<String, String> {
        return synchronized(lock) {
            cache.snapshot().toMap()
        }
    }
    
    fun fromMap(map: Map<String, String>) {
        synchronized(lock) {
            cache.evictAll()
            map.forEach { (url, trackId) ->
                cache.put(url, trackId)
            }
        }
    }
    
    fun toJsonString(): String {
        return Globals.json.encodeToString(toMap())
    }
    
    companion object {
        fun fromJsonString(jsonString: String): AudioTrackMemoryCache {
            val cache = AudioTrackMemoryCache()
            runCatching {
                val map = Globals.json.decodeFromString<Map<String, String>>(jsonString)
                cache.fromMap(map)
            }
            return cache
        }
    }
}
