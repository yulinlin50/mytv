package top.yogiczy.mytv.core.data.entities.epg

import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.data.utils.LruMutableCache

object EpgProgrammeRecentCache {
    private val log = Logger.create("EpgProgrammeRecentCache")
    
    private data class CacheEntry(val value: EpgProgrammeRecent?)
    
    private val cache = LruMutableCache<String, CacheEntry>(500).apply {
        expiryTimeMs = 30_000L
    }
    
    private fun buildCacheKey(channel: Channel): String {
        return "${channel.iptvSourceId}:${channel.name}:${channel.epgName}:${channel.epgId}"
    }
    
    fun get(channel: Channel): EpgProgrammeRecent? {
        val key = buildCacheKey(channel)
        return cache.getTimestamped(key)?.value
    }
    
    fun put(channel: Channel, result: EpgProgrammeRecent?) {
        val key = buildCacheKey(channel)
        cache.putTimestamped(key, CacheEntry(result))
    }
    
    fun getOrPut(channel: Channel, provider: () -> EpgProgrammeRecent?): EpgProgrammeRecent? {
        val key = buildCacheKey(channel)
        return cache.getOrPut(key) { CacheEntry(provider()) }.value
    }
    
    fun clear() {
        cache.clearAll()
        log.d("缓存已清空")
    }
    
    fun size(): Int = cache.getTimestampedSize()
}
