package top.yogiczy.mytv.core.data.entities.epg

import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.data.utils.LruMutableCache

object EpgProgrammeRecentCache {
    private val log = Logger.create("EpgProgrammeRecentCache")
    
    private data class CacheEntry(
        val result: EpgProgrammeRecent?,
        val timestamp: Long,
    )
    
    private val cache = LruMutableCache<String, CacheEntry>(500)
    private const val CACHE_DURATION_MS = 30_000L
    
    init {
        cache.expiryTimeMs = CACHE_DURATION_MS
    }
    
    private fun buildCacheKey(channel: Channel): String {
        return "${channel.iptvSourceId}:${channel.name}:${channel.epgName}:${channel.epgId}"
    }
    
    fun get(channel: Channel): EpgProgrammeRecent? {
        val key = buildCacheKey(channel)
        return cache.getTimestamped(key)?.result
    }
    
    fun put(channel: Channel, result: EpgProgrammeRecent?) {
        val key = buildCacheKey(channel)
        cache.putTimestamped(key, CacheEntry(
            result = result,
            timestamp = System.currentTimeMillis()
        ))
    }
    
    fun getOrPut(channel: Channel, provider: () -> EpgProgrammeRecent?): EpgProgrammeRecent? {
        val key = buildCacheKey(channel)
        
        val cachedEntry = cache.getTimestamped(key)
        if (cachedEntry != null && System.currentTimeMillis() - cachedEntry.timestamp < CACHE_DURATION_MS) {
            return cachedEntry.result
        }
        
        val result = provider()
        cache.putTimestamped(key, CacheEntry(
            result = result,
            timestamp = System.currentTimeMillis()
        ))
        return result
    }
    
    fun clear() {
        cache.clearAll()
        log.d("缓存已清空")
    }
    
    fun size(): Int = cache.getTimestampedSize()
}
