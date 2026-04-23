package top.yogiczy.mytv.core.data.utils

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object UnifiedCacheManager {
    
    private const val TAG = "UnifiedCacheManager"
    
    private const val DEFAULT_MAX_SIZE = 2048
    private const val DEFAULT_EXPIRY_MS = 30 * 60 * 1000L
    
    private data class CacheEntry<V>(
        val value: V,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private data class CacheStats(
        val hits: AtomicLong = AtomicLong(0),
        val misses: AtomicLong = AtomicLong(0),
        val evictions: AtomicLong = AtomicLong(0),
        var size: Int = 0
    )
    
    private interface Cache<V> {
        fun get(key: String): V?
        fun put(key: String, value: V)
        fun remove(key: String)
        fun clear()
        fun size(): Int
    }
    
    private class LruCache<V>(
        private val maxSize: Int,
        private val expiryMs: Long
    ) : Cache<V> {
        
        private val lock = Any()
        
        private val cache = object : LinkedHashMap<String, CacheEntry<V>>(
            16, 0.75f, true
        ) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry<V>>?): Boolean {
                return size > maxSize
            }
        }
        
        override fun get(key: String): V? = synchronized(lock) {
            val entry = cache[key] ?: return null
            if (System.currentTimeMillis() - entry.timestamp > expiryMs) {
                cache.remove(key)
                return null
            }
            entry.value
        }
        
        override fun put(key: String, value: V) = synchronized(lock) {
            cache[key] = CacheEntry(value)
        }
        
        override fun remove(key: String): Unit = synchronized(lock) {
            cache.remove(key)
        }
        
        override fun clear() = synchronized(lock) {
            cache.clear()
        }
        
        override fun size(): Int = synchronized(lock) {
            cache.size
        }
    }
    
    private val caches = ConcurrentHashMap<String, Cache<*>>()
    private val configs = ConcurrentHashMap<String, CacheConfig>()
    private val stats = ConcurrentHashMap<String, CacheStats>()
    
    data class CacheConfig(
        val maxSize: Int = DEFAULT_MAX_SIZE,
        val expiryMs: Long = DEFAULT_EXPIRY_MS,
        val enableStats: Boolean = true
    )
    
    fun <V : Any> registerCache(name: String, config: CacheConfig = CacheConfig()) {
        if (caches.containsKey(name)) {
            Log.w(TAG, "Cache '$name' already registered, replacing")
        }
        caches[name] = LruCache<V>(config.maxSize, config.expiryMs)
        configs[name] = config
        stats[name] = CacheStats()
        Log.i(TAG, "Registered cache '$name' with config: $config")
    }
    
    @Suppress("UNCHECKED_CAST")
    fun <V : Any> get(cacheName: String, key: String): V? {
        val cache = caches[cacheName] as? Cache<V> ?: run {
            Log.w(TAG, "Cache '$cacheName' not found or type mismatch")
            return null
        }
        
        val value = cache.get(key)
        val stats = stats[cacheName]
        
        if (value != null) {
            stats?.hits?.incrementAndGet()
        } else {
            stats?.misses?.incrementAndGet()
        }
        
        return value
    }
    
    @Suppress("UNCHECKED_CAST")
    fun <V : Any> put(cacheName: String, key: String, value: V) {
        val cache = caches[cacheName] as? Cache<V> ?: run {
            Log.w(TAG, "Cache '$cacheName' not found or type mismatch")
            return
        }
        
        cache.put(key, value)
        stats[cacheName]?.size = cache.size()
    }
    
    fun <V : Any> getOrPut(cacheName: String, key: String, defaultValue: () -> V): V {
        get<V>(cacheName, key)?.let { return it }
        
        val value = defaultValue()
        put(cacheName, key, value)
        return value
    }
    
    fun remove(cacheName: String, key: String) {
        caches[cacheName]?.remove(key)
    }
    
    fun clearCache(cacheName: String) {
        caches[cacheName]?.clear()
        stats[cacheName]?.let {
            it.hits.set(0)
            it.misses.set(0)
            it.evictions.set(0)
            it.size = 0
        }
        Log.i(TAG, "Cleared cache '$cacheName'")
    }
    
    fun clearAllCaches() {
        caches.keys.forEach { clearCache(it) }
        Log.i(TAG, "Cleared all caches")
    }
    
    fun getCacheStats(cacheName: String): Map<String, Any> {
        val stats = stats[cacheName] ?: return emptyMap()
        val config = configs[cacheName] ?: return emptyMap()
        
        return mapOf(
            "hits" to stats.hits.get(),
            "misses" to stats.misses.get(),
            "evictions" to stats.evictions.get(),
            "size" to stats.size,
            "maxSize" to config.maxSize,
            "hitRate" to if (stats.hits.get() + stats.misses.get() > 0) {
                stats.hits.get().toDouble() / (stats.hits.get() + stats.misses.get())
            } else 0.0
        )
    }
    
    fun getAllCacheStats(): Map<String, Map<String, Any>> {
        return caches.keys.associateWith { getCacheStats(it) }
    }
    
    fun getTotalMemoryUsage(): Long {
        return caches.values.sumOf { cache ->
            cache.size().toLong() * 1024
        }
    }
    
    fun logAllStats() {
        val allStats = getAllCacheStats()
        val sb = StringBuilder("缓存统计:\n")
        
        for ((name, stats) in allStats) {
            sb.append("  $name: size=${stats["size"]}, hitRate=${stats["hitRate"]}, ")
            sb.append("hits=${stats["hits"]}, misses=${stats["misses"]}, evictions=${stats["evictions"]}\n")
        }
        
        Log.i(TAG, sb.toString())
    }
    
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
        registerCache<String>(CacheNames.RECENT_PROGRAMME, CacheConfig(maxSize = 200, expiryMs = 30 * 60 * 1000L))
        registerCache<String>(CacheNames.CHANNEL_MATCH, CacheConfig(maxSize = 4096))
        registerCache<Long>(CacheNames.TIME_PARSE, CacheConfig(maxSize = 10000))
        registerCache<String>(CacheNames.EPG_DATA, CacheConfig(maxSize = 100, expiryMs = 300_000L))
        registerCache<Any>(CacheNames.CHANNEL_INDEX, CacheConfig(maxSize = 50, expiryMs = 600_000L))
        registerCache<String>(CacheNames.NORMALIZE, CacheConfig(maxSize = 2048))
        registerCache<String>(CacheNames.SUBSTRING_MATCH, CacheConfig(maxSize = 500))
        registerCache<String>(CacheNames.CHANNEL_NAME, CacheConfig(maxSize = 5000))
    }
}
