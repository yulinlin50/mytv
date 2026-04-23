package top.yogiczy.mytv.core.data.utils

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 统一缓存管理器
 * 
 * 提供多命名空间的缓存管理，支持：
 * - LRU 淘汰策略（基于时间戳）
 * - 过期时间控制
 * - 缓存统计（命中率、淘汰次数）
 * - 线程安全操作
 * 
 * 使用示例：
 * ```kotlin
 * // 注册缓存
 * UnifiedCacheManager.registerCache("my_cache", CacheConfig(maxSize = 1000))
 * 
 * // 存取数据
 * UnifiedCacheManager.put("my_cache", "key", value)
 * val data = UnifiedCacheManager.get<MyType>("my_cache", "key")
 * 
 * // 获取或创建
 * val data = UnifiedCacheManager.getOrPut("my_cache", "key") { createValue() }
 * ```
 * 
 * @see CacheNames 预定义的缓存命名空间
 * @see CacheConfig 缓存配置
 */
object UnifiedCacheManager {
    
    private val log = Logger.create("UnifiedCacheManager")
    
    private const val DEFAULT_MAX_SIZE = 2048
    private const val DEFAULT_EXPIRY_MS = 30 * 60 * 1000L
    
    /**
     * 缓存条目
     * @param value 缓存值
     * @param timestamp 创建时间戳
     * @param accessCount 访问次数
     */
    private data class CacheEntry<V>(
        val value: V,
        val timestamp: Long,
        val accessCount: Long = 0
    )
    
    /**
     * 缓存统计信息
     */
    private data class CacheStats(
        val hits: AtomicLong = AtomicLong(0),
        val misses: AtomicLong = AtomicLong(0),
        val evictions: AtomicLong = AtomicLong(0)
    )
    
    private val caches = ConcurrentHashMap<String, ConcurrentHashMap<String, CacheEntry<*>>>()
    private val cacheStats = ConcurrentHashMap<String, CacheStats>()
    private val cacheConfigs = ConcurrentHashMap<String, CacheConfig>()
    
    /**
     * 缓存配置
     * @param maxSize 最大条目数
     * @param expiryMs 过期时间（毫秒）
     * @param trimRatio 清理时保留的比例
     */
    data class CacheConfig(
        val maxSize: Int = DEFAULT_MAX_SIZE,
        val expiryMs: Long = DEFAULT_EXPIRY_MS,
        val trimRatio: Float = 0.5f
    )
    
    /**
     * 注册缓存
     * @param name 缓存名称
     * @param config 缓存配置
     */
    fun registerCache(name: String, config: CacheConfig = CacheConfig()) {
        caches[name] = ConcurrentHashMap()
        cacheStats[name] = CacheStats()
        cacheConfigs[name] = config
        log.d("注册缓存: $name, maxSize=${config.maxSize}, expiryMs=${config.expiryMs}")
    }
    
    /**
     * 存入缓存
     * @param cacheName 缓存名称
     * @param key 键
     * @param value 值
     */
    fun <V : Any> put(cacheName: String, key: String, value: V) {
        val cache = caches[cacheName] ?: return
        val config = cacheConfigs[cacheName] ?: return
        
        if (cache.size >= config.maxSize) {
            trimCache(cacheName, config)
        }
        
        cache[key] = CacheEntry(value, System.currentTimeMillis())
    }
    
    /**
     * 获取缓存
     * @param cacheName 缓存名称
     * @param key 键
     * @return 缓存值，如果不存在或已过期则返回 null
     */
    @Suppress("UNCHECKED_CAST")
    fun <V : Any> get(cacheName: String, key: String): V? {
        val cache = caches[cacheName] ?: return null
        val config = cacheConfigs[cacheName] ?: return null
        val stats = cacheStats[cacheName] ?: return null
        
        val entry = cache[key] as? CacheEntry<V> ?: run {
            stats.misses.incrementAndGet()
            return null
        }
        
        if (System.currentTimeMillis() - entry.timestamp > config.expiryMs) {
            cache.remove(key)
            stats.misses.incrementAndGet()
            return null
        }
        
        stats.hits.incrementAndGet()
        return entry.value
    }
    
    /**
     * 获取或创建缓存
     * @param cacheName 缓存名称
     * @param key 键
     * @param defaultValue 默认值创建函数
     * @return 缓存值或新创建的值
     */
    fun <V : Any> getOrPut(cacheName: String, key: String, defaultValue: () -> V): V {
        get<V>(cacheName, key)?.let { return it }
        
        val value = defaultValue()
        put(cacheName, key, value)
        return value
    }
    
    /**
     * 移除缓存条目
     * @param cacheName 缓存名称
     * @param key 键
     */
    fun remove(cacheName: String, key: String) {
        caches[cacheName]?.remove(key)
    }
    
    /**
     * 清空指定缓存
     * @param cacheName 缓存名称
     */
    fun clearCache(cacheName: String) {
        caches[cacheName]?.clear()
        cacheStats[cacheName]?.let {
            it.hits.set(0)
            it.misses.set(0)
            it.evictions.set(0)
        }
    }
    
    /**
     * 清空所有缓存
     */
    fun clearAllCaches() {
        caches.values.forEach { it.clear() }
        cacheStats.values.forEach {
            it.hits.set(0)
            it.misses.set(0)
            it.evictions.set(0)
        }
        log.i("所有缓存已清理")
    }
    
    /**
     * 清理缓存（LRU 淘汰）
     */
    private fun trimCache(cacheName: String, config: CacheConfig) {
        val cache = caches[cacheName] ?: return
        val stats = cacheStats[cacheName] ?: return
        
        val targetSize = (config.maxSize * config.trimRatio).toInt()
        val entriesToRemove = cache.size - targetSize
        
        if (entriesToRemove <= 0) return
        
        val keysToRemove = cache.entries
            .sortedBy { (it.value as CacheEntry<*>).timestamp }
            .take(entriesToRemove)
            .map { it.key }
        
        var removed = 0
        for (key in keysToRemove) {
            if (cache.remove(key) != null) {
                removed++
            }
        }
        
        if (removed > 0) {
            stats.evictions.addAndGet(removed.toLong())
            log.v("缓存清理: $cacheName, 移除 $removed 条")
        }
    }
    
    /**
     * 获取缓存统计信息
     * @param cacheName 缓存名称
     * @return 统计信息（size, hits, misses, hitRate, evictions）
     */
    fun getCacheStats(cacheName: String): Map<String, Long> {
        val stats = cacheStats[cacheName] ?: return emptyMap()
        val cache = caches[cacheName] ?: return emptyMap()
        
        val hits = stats.hits.get()
        val misses = stats.misses.get()
        val total = hits + misses
        
        return mapOf(
            "size" to cache.size.toLong(),
            "hits" to hits,
            "misses" to misses,
            "hitRate" to if (total > 0) (hits * 100 / total) else 0,
            "evictions" to stats.evictions.get()
        )
    }
    
    /**
     * 获取所有缓存统计信息
     */
    fun getAllStats(): Map<String, Map<String, Long>> {
        return caches.keys.associateWith { getCacheStats(it) }
    }
    
    /**
     * 打印所有缓存统计信息
     */
    fun logAllStats() {
        val allStats = getAllStats()
        val sb = StringBuilder("缓存统计:\n")
        
        for ((name, stats) in allStats) {
            sb.append("  $name: size=${stats["size"]}, hitRate=${stats["hitRate"]}%, ")
            sb.append("hits=${stats["hits"]}, misses=${stats["misses"]}, evictions=${stats["evictions"]}\n")
        }
        
        log.i(sb.toString())
    }
    
    /**
     * 预定义的缓存命名空间
     */
    object CacheNames {
        const val CHANNEL_MATCH = "channel_match"
        const val NORMALIZE = "normalize"
        const val SUBSTRING_MATCH = "substring_match"
        const val RECENT_PROGRAMME = "recent_programme"
        const val CHANNEL_NAME = "channel_name"
        const val TIME_PARSE = "time_parse"
    }
    
    init {
        registerCache(CacheNames.CHANNEL_MATCH, CacheConfig(maxSize = 4096))
        registerCache(CacheNames.NORMALIZE, CacheConfig(maxSize = 2048))
        registerCache(CacheNames.SUBSTRING_MATCH, CacheConfig(maxSize = 500))
        registerCache(CacheNames.RECENT_PROGRAMME, CacheConfig(maxSize = 200, expiryMs = 5_000L))
        registerCache(CacheNames.CHANNEL_NAME, CacheConfig(maxSize = 5000))
        registerCache(CacheNames.TIME_PARSE, CacheConfig(maxSize = 10000))
    }
}
