package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import android.util.LruCache
import kotlinx.serialization.encodeToString
import top.yogiczy.mytv.core.data.utils.Globals

object AudioTrackCache {

    private fun optimalCacheSize(small: Boolean = false): Int {
        val maxMemoryMB = Runtime.getRuntime().maxMemory() / 1024 / 1024
        return when {
            maxMemoryMB >= 512 -> if (small) 200 else 50
            maxMemoryMB >= 256 -> if (small) 100 else 30
            else -> if (small) 50 else 20
        }
    }

    class TrackListCache {
        private val cache = LruCache<String, CacheEntry>(optimalCacheSize())

        data class CacheEntry(
            val tracks: List<PlayerMetadata.AudioTrack>,
            val timestamp: Long = System.currentTimeMillis(),
        )

        companion object {
            private const val TTL_MS = 30_000L
        }

        @Synchronized fun get(url: String): List<PlayerMetadata.AudioTrack>? {
            val entry = cache.get(url) ?: return null
            if (System.currentTimeMillis() - entry.timestamp > TTL_MS) {
                cache.remove(url)
                return null
            }
            return entry.tracks
        }
        @Synchronized fun put(url: String, tracks: List<PlayerMetadata.AudioTrack>) =
            cache.put(url, CacheEntry(tracks))
        @Synchronized fun remove(url: String) = cache.remove(url)
        @Synchronized fun clear() = cache.evictAll()
        @Synchronized fun size(): Int = cache.size()
    }

    class TrackIdCache(maxSize: Int = optimalCacheSize(small = true)) {
        private val cache = LruCache<String, String>(maxSize)
        private val lock = Any()

        fun get(url: String): String? = synchronized(lock) { cache.get(url) }
        fun put(url: String, trackId: String) = synchronized(lock) { cache.put(url, trackId) }
        fun remove(url: String) = synchronized(lock) { cache.remove(url) }
        fun clear() = synchronized(lock) { cache.evictAll() }
        fun size(): Int = synchronized(lock) { cache.size() }

        fun toMap(): Map<String, String> = synchronized(lock) { cache.snapshot().toMap() }

        fun fromMap(map: Map<String, String>) {
            synchronized(lock) {
                cache.evictAll()
                map.forEach { (url, trackId) -> cache.put(url, trackId) }
            }
        }

        fun toJsonString(): String = Globals.json.encodeToString(toMap())

        companion object {
            fun fromJsonString(jsonString: String): TrackIdCache {
                val cache = TrackIdCache()
                runCatching {
                    val map = Globals.json.decodeFromString<Map<String, String>>(jsonString)
                    cache.fromMap(map)
                }
                return cache
            }
        }
    }
}
