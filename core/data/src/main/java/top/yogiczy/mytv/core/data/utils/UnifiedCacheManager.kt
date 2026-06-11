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

    data class CacheConfig(val maxSize: Int = DEFAULT_MAX_SIZE, val expiryMs: Long = DEFAULT_EXPIRY_MS)

    private fun <V : Any> ensureCache(name: String, config: CacheConfig = CacheConfig()): LruCache<V> {
        return caches.getOrPut(name) { LruCache<V>(config.maxSize, config.expiryMs) } as LruCache<V>
    }

    @Suppress("UNCHECKED_CAST")
    fun <V : Any> get(cacheName: String, key: String): V? =
        (caches[cacheName] as? LruCache<V>)?.get(key)

    fun <V : Any> put(cacheName: String, key: String, value: V) {
        (caches[cacheName] as? LruCache<V>)?.put(key, value)
    }

    fun <V : Any> getOrPut(cacheName: String, key: String, defaultValue: () -> V): V {
        get<V>(cacheName, key)?.let { return it }
        val value = defaultValue()
        put(cacheName, key, value)
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

    init {
        ensureCache<String>(CacheNames.RECENT_PROGRAMME, CacheConfig(maxSize = 200, expiryMs = 30 * 60 * 1000L))
        ensureCache<String>(CacheNames.CHANNEL_MATCH, CacheConfig(maxSize = 4096))
        ensureCache<Long>(CacheNames.TIME_PARSE, CacheConfig(maxSize = 10000))
        ensureCache<String>(CacheNames.EPG_DATA, CacheConfig(maxSize = 100, expiryMs = 300_000L))
        ensureCache<Any>(CacheNames.CHANNEL_INDEX, CacheConfig(maxSize = 50, expiryMs = 600_000L))
        ensureCache<String>(CacheNames.NORMALIZE, CacheConfig(maxSize = 2048))
        ensureCache<String>(CacheNames.SUBSTRING_MATCH, CacheConfig(maxSize = 500))
        ensureCache<String>(CacheNames.CHANNEL_NAME, CacheConfig(maxSize = 5000))
    }
}
