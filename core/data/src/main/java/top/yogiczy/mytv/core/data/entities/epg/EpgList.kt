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
            val programmeIntervalTrees: Map<String, ProgrammeIntervalTree>,
            val createdAt: Long = System.currentTimeMillis()
        )
        
        @Volatile
        private var indexCache: Pair<Int, IndexCache>? = null
        private val indexCacheLock = Any()
        
        private fun EpgList.computeCacheKey(): Int {
            if (isEmpty()) return 0
            var hash = 1
            for (epg in value) {
                hash = 31 * hash + epg.channelList.hashCode()
                hash = 31 * hash + (epg.sourceId?.hashCode() ?: 0)
                hash = 31 * hash + epg.programmeList.size
            }
            return hash
        }
        
        private fun EpgList.getOrCreateIndexCache(): IndexCache {
            val cacheKey = computeCacheKey()
            
            indexCache?.let { (key, cache) ->
                if (key == cacheKey) {
                    return cache
                }
            }
            
            return synchronized(indexCacheLock) {
                indexCache?.let { (key, cache) ->
                    if (key == cacheKey) {
                        return cache
                    }
                }
                
                val idIndex = mutableMapOf<String, Epg>()
                val sourceGroupedIndex = mutableMapOf<String?, MutableList<Epg>>()
                val programmeIntervalTrees = mutableMapOf<String, ProgrammeIntervalTree>()
                
                for (epg in value) {
                    val primaryName = epg.channelList.firstOrNull()
                    
                    for (name in epg.channelList) {
                        idIndex[name] = epg
                        idIndex[name.lowercase()] = epg
                    }
                    sourceGroupedIndex.getOrPut(epg.sourceId) { mutableListOf() }.add(epg)
                    
                    if (epg.programmeList.isNotEmpty() && primaryName != null) {
                        programmeIntervalTrees[primaryName] = ProgrammeIntervalTree.fromProgrammeList(epg.programmeList)
                    }
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
                    sourceTrieIndexes = sourceTrieIndexes,
                    programmeIntervalTrees = programmeIntervalTrees
                )
                
                indexCache = cacheKey to cache
                log.d("优化索引缓存已重建: 频道=${value.size}, 缓存键=$cacheKey, 区间树=${programmeIntervalTrees.size}")
                
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
        
        private fun EpgList.getProgrammeIntervalTree(channelName: String): ProgrammeIntervalTree? {
            return getOrCreateIndexCache().programmeIntervalTrees[channelName]
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
        
        private fun EpgList.performMatch(channel: Channel): Epg {
            val sourceId = channel.iptvSourceId
            val channelEpgName = channel.epgName
            val channelStandardName = channel.standardName
            val channelEpgId = channel.epgId
            
            // 如果频道有关联的直播源，优先在该源的 EPG 中匹配
            sourceId?.let { sid ->
                val sourceTrieIndex = getSourceTrieIndex(sid)
                if (sourceTrieIndex != null) {
                    // 先尝试全局映射，但只在该源的范围内查找
                    EpgChannelMapping.findMapping(channel.name)?.let { mapping ->
                        mapping.epgId?.let { epgId ->
                            getSourceGroupedIndex()[sid]?.firstOrNull { epg ->
                                epg.channelList.any { it.equals(epgId, ignoreCase = true) }
                            }?.let { return it }
                        }
                        if (mapping.epgName.isNotEmpty()) {
                            sourceTrieIndex.exactMatch(mapping.epgName)?.let { return it }
                            sourceTrieIndex.normalizedMatch(mapping.epgName)?.firstOrNull()?.let { return it }
                        }
                    }
                    
                    // 按源匹配
                    channelEpgId?.let { epgId ->
                        getSourceGroupedIndex()[sid]?.firstOrNull { epg ->
                            epg.channelList.any { it.equals(epgId, ignoreCase = true) }
                        }?.let { return it }
                    }
                    
                    sourceTrieIndex.exactMatch(channel.name)?.let { return it }
                    sourceTrieIndex.exactMatch(channelEpgName)?.let { return it }
                    sourceTrieIndex.exactMatch(channelStandardName)?.let { return it }
                    
                    sourceTrieIndex.normalizedMatch(channelEpgName)?.firstOrNull()?.let { return it }
                    sourceTrieIndex.normalizedMatch(channelStandardName)?.firstOrNull()?.let { return it }
                    
                    sourceTrieIndex.substringMatch(channelEpgName).firstOrNull()?.let { return it }
                    sourceTrieIndex.substringMatch(channelStandardName).firstOrNull()?.let { return it }
                    
                    // 该源没有匹配到，返回空（不回退到全局匹配）
                    return Epg()
                }
            }
            
            // 没有关联直播源时，使用全局匹配
            EpgChannelMapping.findMapping(channel.name)?.let { mapping ->
                mapping.epgId?.let { epgId ->
                    getIdIndex()[epgId]?.let { return it }
                }
                if (mapping.epgName.isNotEmpty()) {
                    getChannelTrieIndex().exactMatch(mapping.epgName)?.let { return it }
                    getChannelTrieIndex().normalizedMatch(mapping.epgName)?.firstOrNull()?.let { return it }
                }
            }
            
            val channelTrieIndex = getChannelTrieIndex()
            val idIndex = getIdIndex()
            
            channelEpgId?.let { epgId ->
                idIndex[epgId]?.let { return it }
            }
            
            channelTrieIndex.exactMatch(channel.name)?.let { return it }
            channelTrieIndex.exactMatch(channelEpgName)?.let { return it }
            channelTrieIndex.exactMatch(channelStandardName)?.let { return it }
            
            channelTrieIndex.normalizedMatch(channelEpgName)?.firstOrNull()?.let { return it }
            channelTrieIndex.normalizedMatch(channelStandardName)?.firstOrNull()?.let { return it }
            
            channelTrieIndex.substringMatch(channelEpgName).firstOrNull()?.let { return it }
            channelTrieIndex.substringMatch(channelStandardName)?.firstOrNull()?.let { return it }
            
            return Epg()
        }
        
        /**
         * 批量匹配 - 用于提升性能
         */
        fun EpgList.matchAll(channels: List<Channel>): Map<Channel, Epg?> {
            return channels.associateWith { match(it) }
        }

        fun clearCache() {
            UnifiedCacheManager.clearCache(UnifiedCacheManager.CacheNames.CHANNEL_MATCH)
            indexCache = null
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
