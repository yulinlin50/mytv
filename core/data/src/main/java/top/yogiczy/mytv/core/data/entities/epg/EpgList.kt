package top.yogiczy.mytv.core.data.entities.epg

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.Epg.Companion.recentProgramme
import top.yogiczy.mytv.core.data.utils.ChannelTrieIndex
import top.yogiczy.mytv.core.data.utils.EpgChannelMapping
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.data.utils.UnifiedCacheManager

/**
 * 频道节目单列表
 */
@Serializable
@Immutable
data class EpgList(
    val value: List<Epg> = emptyList(),
) : List<Epg> by value {
    
    companion object {
        private val log = Logger.create("EpgList")
        
        private data class IndexCache(
            val channelTrieIndex: ChannelTrieIndex,
            val idIndex: Map<String, Epg>,
            val sourceGroupedIndex: Map<String?, List<Epg>>,
            val sourceTrieIndexes: Map<String?, ChannelTrieIndex>,
            val createdAt: Long = System.currentTimeMillis()
        )
        
        @Volatile
        private var indexCacheRef: Pair<EpgList, IndexCache>? = null
        private val indexCacheLock = Any()
        
        private fun EpgList.getOrCreateIndexCache(): IndexCache {
            indexCacheRef?.let { (list, cache) ->
                if (list === this) {
                    return cache
                }
            }
            
            return synchronized(indexCacheLock) {
                indexCacheRef?.let { (list, cache) ->
                    if (list === this) {
                        return cache
                    }
                }
                
                val idIndex = mutableMapOf<String, Epg>()
                val sourceGroupedIndex = mutableMapOf<String?, MutableList<Epg>>()
                
                for (epg in value) {
                    for (name in epg.channelList) {
                        idIndex[name] = epg
                        idIndex[name.lowercase()] = epg
                    }
                    sourceGroupedIndex.getOrPut(epg.sourceId) { mutableListOf() }.add(epg)
                }
                
                val channelTrieIndex = ChannelTrieIndex(value)
                
                val sourceTrieIndexes = mutableMapOf<String?, ChannelTrieIndex>()
                for ((sourceId, epgs) in sourceGroupedIndex) {
                    sourceTrieIndexes[sourceId] = ChannelTrieIndex(epgs)
                }
                
                val cache = IndexCache(
                    channelTrieIndex = channelTrieIndex,
                    idIndex = idIndex,
                    sourceGroupedIndex = sourceGroupedIndex,
                    sourceTrieIndexes = sourceTrieIndexes
                )
                
                indexCacheRef = this@getOrCreateIndexCache to cache
                log.d("优化索引缓存已重建: 频道=${value.size}")
                
                cache
            }
        }

        fun EpgList.recentProgramme(channel: Channel): EpgProgrammeRecent? {
            if (isEmpty()) {
                return null
            }

            return match(channel)?.recentProgramme()
        }
        
        private fun EpgList.getChannelTrieIndex(): ChannelTrieIndex {
            return getOrCreateIndexCache().channelTrieIndex
        }
        
        private fun EpgList.getIdIndex(): Map<String, Epg> {
            return getOrCreateIndexCache().idIndex
        }
        
        private fun EpgList.getSourceGroupedIndex(): Map<String?, List<Epg>> {
            return getOrCreateIndexCache().sourceGroupedIndex
        }
        
        private fun EpgList.getSourceTrieIndex(sourceId: String?): ChannelTrieIndex? {
            return getOrCreateIndexCache().sourceTrieIndexes[sourceId]
        }
        
        /**
         * 严格同源频道匹配方法
         * 只有关联的源才能匹配对应的节目单
         * 使用分层匹配策略和源过滤优化性能
         */
        fun EpgList.match(channel: Channel): Epg? {
            if (isEmpty()) {
                return null
            }

            val cacheKey = "${channel.iptvSourceId}:${channel.name}:${channel.epgName}:${channel.epgId}"
            
            val cachedValue: Epg? = UnifiedCacheManager.get(UnifiedCacheManager.CacheNames.CHANNEL_MATCH, cacheKey)
            if (cachedValue != null) {
                return if (cachedValue == Epg()) null else cachedValue
            }
            
            val result = performMatch(channel)
            
            UnifiedCacheManager.put(UnifiedCacheManager.CacheNames.CHANNEL_MATCH, cacheKey, result)
            return if (result == Epg()) null else result
        }
        
        /**
         * 在 EPG 列表中按 ID 匹配
         * @param epgId 要匹配的 EPG ID
         * @param epgList EPG 列表（可为 null，表示从全局索引获取）
         * @return 匹配到的 Epg，未匹配返回 null
         */
        private fun matchByEpgId(epgId: String?, epgList: List<Epg>?): Epg? {
            if (epgId.isNullOrEmpty()) return null
            
            return if (epgList != null) {
                epgList.firstOrNull { epg ->
                    epg.channelList.any { it.equals(epgId, ignoreCase = true) }
                }
            } else {
                getIdIndex()[epgId]
            }
        }
        
        /**
         * 使用 Trie 索引进行分层匹配
         * 匹配顺序：精确匹配 → 规范化匹配 → 子串匹配
         * 
         * @param channel 频道信息
         * @param trieIndex Trie 索引
         * @return 匹配到的 Epg，未匹配返回 null
         */
        private fun matchByTrieIndex(channel: Channel, trieIndex: ChannelTrieIndex): Epg? {
            val channelEpgName = channel.epgName
            val channelStandardName = channel.standardName
            
            // 精确匹配
            trieIndex.exactMatch(channel.name)?.let { return it }
            trieIndex.exactMatch(channelEpgName)?.let { return it }
            trieIndex.exactMatch(channelStandardName)?.let { return it }
            
            // 规范化匹配
            trieIndex.normalizedMatch(channelEpgName)?.firstOrNull()?.let { return it }
            trieIndex.normalizedMatch(channelStandardName)?.firstOrNull()?.let { return it }
            
            // 子串匹配
            trieIndex.substringMatch(channelEpgName).firstOrNull()?.let { return it }
            trieIndex.substringMatch(channelStandardName)?.firstOrNull()?.let { return it }
            
            return null
        }
        
        /**
         * 同源匹配 - 在指定源的 EPG 中匹配频道
         * 
         * @param channel 频道信息
         * @param sourceId 源 ID
         * @return 匹配到的 Epg，未匹配返回 null，源不存在返回 null
         */
        private fun EpgList.matchInSource(channel: Channel, sourceId: String): Epg? {
            val sourceTrieIndex = getSourceTrieIndex(sourceId) ?: return null
            val sourceEpgList = getSourceGroupedIndex()[sourceId]
            
            // 尝试全局映射（在该源范围内）
            EpgChannelMapping.findMapping(channel.name)?.let { mapping ->
                mapping.epgId?.let { epgId ->
                    matchByEpgId(epgId, sourceEpgList)?.let { return it }
                }
                if (mapping.epgName.isNotEmpty()) {
                    sourceTrieIndex.exactMatch(mapping.epgName)?.let { return it }
                    sourceTrieIndex.normalizedMatch(mapping.epgName)?.firstOrNull()?.let { return it }
                }
            }
            
            // 按 EPG ID 匹配
            matchByEpgId(channel.epgId, sourceEpgList)?.let { return it }
            
            // 按 Trie 索引匹配
            matchByTrieIndex(channel, sourceTrieIndex)?.let { return it }
            
            return null
        }
        
        /**
         * 全局匹配 - 在所有 EPG 中匹配频道
         * 
         * @param channel 频道信息
         * @return 匹配到的 Epg，未匹配返回 null
         */
        private fun EpgList.matchGlobally(channel: Channel): Epg? {
            // 尝试全局映射
            EpgChannelMapping.findMapping(channel.name)?.let { mapping ->
                mapping.epgId?.let { epgId ->
                    matchByEpgId(epgId, null)?.let { return it }
                }
                if (mapping.epgName.isNotEmpty()) {
                    getChannelTrieIndex().exactMatch(mapping.epgName)?.let { return it }
                    getChannelTrieIndex().normalizedMatch(mapping.epgName)?.firstOrNull()?.let { return it }
                }
            }
            
            // 按 EPG ID 匹配
            matchByEpgId(channel.epgId, null)?.let { return it }
            
            // 按 Trie 索引匹配
            matchByTrieIndex(channel, getChannelTrieIndex())?.let { return it }
            
            return null
        }
        
        private fun EpgList.performMatch(channel: Channel): Epg {
            val sourceId = channel.iptvSourceId
            
            // 如果频道有关联的直播源，优先在该源的 EPG 中匹配
            sourceId?.let { sid ->
                val result = matchInSource(channel, sid)
                if (result != null) {
                    return result
                }
                // 该源没有匹配到，返回空（不回退到全局匹配）
                return Epg()
            }
            
            // 没有关联直播源时，使用全局匹配
            return matchGlobally(channel) ?: Epg()
        }
        
        /**
         * 批量匹配 - 用于提升性能
         */
        fun EpgList.matchAll(channels: List<Channel>): Map<Channel, Epg?> {
            return channels.associateWith { match(it) }
        }

        fun clearCache() {
            UnifiedCacheManager.clearCache(UnifiedCacheManager.CacheNames.CHANNEL_MATCH)
            indexCacheRef = null
            Epg.clearRecentProgrammeCache()
            ChannelTrieIndex.clearCaches()
            EpgChannelMapping.clearIndexes()
        }

        fun example(channelList: ChannelList): EpgList {
            return EpgList(channelList.map(Epg.Companion::example))
        }

        private val semaphore = Semaphore(1)
        suspend fun <T> action(action: () -> T): T {
            return semaphore.withPermit {
                withContext(Dispatchers.Default) { action() }
            }
        }
    }
}
