package top.yogiczy.mytv.core.data.utils

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object UnifiedCacheManager {

    private const val TAG = "UnifiedCacheManager"
    private const val DEFAULT_MAX_SIZE = 2048
    private const val DEFAULT_EXPIRY_MS = 30 * 60 * 1000L

    private data class CacheEntry<V>(val value: V, val timestamp: Long = System.currentTimeMillis())

    private class LruCache<V>(
        private val maxSize: Int,
        private val expiryMs: Long
    ) {
        private val lock = Any()
        private val cache = object : LinkedHashMap<String, CacheEntry<V>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry<V>>?) = size > maxSize
        }

        fun get(key: String): V? = synchronized(lock) {
            val entry = cache[key] ?: return null
            if (System.currentTimeMillis() - entry.timestamp > expiryMs) { cache.remove(key); return null }
            entry.value
        }

        fun put(key: String, value: V) = synchronized(lock) { cache[key] = CacheEntry(value) }
        fun remove(key: String) = synchronized(lock) { cache.remove(key) }
        fun clear() = synchronized(lock) { cache.clear() }
        fun size(): Int = synchronized(lock) { cache.size }
    }

    private val caches = ConcurrentHashMap<String, LruCache<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <V : Any> get(cacheName: String, key: String): V? =
        (caches.getOrPut(cacheName) { LruCache<V>(DEFAULT_MAX_SIZE, DEFAULT_EXPIRY_MS) } as LruCache<V>).get(key)

    @Suppress("UNCHECKED_CAST")
    fun <V : Any> put(cacheName: String, key: String, value: V) {
        val cache = caches.getOrPut(cacheName) { LruCache<V>(DEFAULT_MAX_SIZE, DEFAULT_EXPIRY_MS) } as LruCache<V>
        cache.put(key, value)
    }

    @Suppress("UNCHECKED_CAST")
    fun <V : Any> getOrPut(cacheName: String, key: String, defaultValue: () -> V): V {
        val cache = caches.getOrPut(cacheName) { LruCache<V>(DEFAULT_MAX_SIZE, DEFAULT_EXPIRY_MS) } as LruCache<V>
        cache.get(key)?.let { return it }
        val value = defaultValue()
        cache.put(key, value)
        return value
    }

    fun remove(cacheName: String, key: String) { caches[cacheName]?.remove(key) }

    fun clearCache(cacheName: String) {
        caches[cacheName]?.clear()
        Log.i(TAG, "Cleared cache '$cacheName'")
    }

    fun clearAllCaches() { caches.keys.forEach { clearCache(it) } }

    object CacheNames {
        const val RECENT_PROGRAMME = "recent_programme"
        const val CHANNEL_MATCH = "channel_match"
        const val TIME_PARSE = "time_parse"
        const val EPG_DATA = "epg_data"
        const val CHANNEL_INDEX = "channel_index"
        const val NORMALIZE = "normalize"
        const val SUBSTRING_MATCH = "substring_match"
        const val CHANNEL_NAME = "channel_name"
    }
}
