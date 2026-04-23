package top.yogiczy.mytv.core.data.entities.epg

import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.utils.Logger
import java.util.concurrent.ConcurrentHashMap

object EpgProgrammeRecentCache {
    private val log = Logger.create("EpgProgrammeRecentCache")
    
    private data class CacheEntry(
        val result: EpgProgrammeRecent?,
        val timestamp: Long,
    )
    
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_DURATION_MS = 30_000L
    
    private fun buildCacheKey(channel: Channel): String {
        return "${channel.iptvSourceId}:${channel.name}:${channel.epgName}:${channel.epgId}"
    }
    
    fun get(channel: Channel): EpgProgrammeRecent? {
        val key = buildCacheKey(channel)
        val now = System.currentTimeMillis()
        
        return cache.computeIfPresent(key) { _, entry ->
            if (now - entry.timestamp < CACHE_DURATION_MS) entry else null
        }?.result
    }
    
    fun put(channel: Channel, result: EpgProgrammeRecent?) {
        val key = buildCacheKey(channel)
        cache[key] = CacheEntry(
            result = result,
            timestamp = System.currentTimeMillis()
        )
    }
    
    fun getOrPut(channel: Channel, provider: () -> EpgProgrammeRecent?): EpgProgrammeRecent? {
        val key = buildCacheKey(channel)
        
        return cache.compute(key) { _, existingEntry ->
            val now = System.currentTimeMillis()
            if (existingEntry != null && now - existingEntry.timestamp < CACHE_DURATION_MS) {
                existingEntry
            } else {
                CacheEntry(provider(), now)
            }
        }?.result
    }
    
    fun clear() {
        cache.clear()
        log.d("缓存已清空")
    }
    
    fun size(): Int = cache.size
}
