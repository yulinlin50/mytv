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
        )

        @Volatile
        private var indexCacheRef: Pair<EpgList, IndexCache>? = null
        private val indexCacheLock = Any()

        private fun EpgList.getOrCreateIndexCache(): IndexCache {
            indexCacheRef?.let { (list, cache) -> if (list === this) return cache }
            return synchronized(indexCacheLock) {
                indexCacheRef?.let { (list, cache) -> if (list === this) return cache }

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
                val sourceTrieIndexes = sourceGroupedIndex.mapValues { (_, epgs) -> ChannelTrieIndex(epgs) }

                val cache = IndexCache(channelTrieIndex, idIndex, sourceGroupedIndex, sourceTrieIndexes)
                indexCacheRef = this@getOrCreateIndexCache to cache
                log.d("优化索引缓存已重建: 频道=${value.size}")
                cache
            }
        }

        fun EpgList.recentProgramme(channel: Channel): EpgProgrammeRecent? {
            if (isEmpty()) return null
            return match(channel)?.recentProgramme()
        }

        fun EpgList.match(channel: Channel): Epg? {
            if (isEmpty()) return null

            val cacheKey = "${channel.iptvSourceId}:${channel.name}:${channel.epgName}:${channel.epgId}"
            val cachedValue: Epg? = UnifiedCacheManager.get(UnifiedCacheManager.CacheNames.CHANNEL_MATCH, cacheKey)
            if (cachedValue != null) return if (cachedValue == Epg()) null else cachedValue

            val result = performMatch(channel)
            UnifiedCacheManager.put(UnifiedCacheManager.CacheNames.CHANNEL_MATCH, cacheKey, result)
            return if (result == Epg()) null else result
        }

        /**
         * 统一的匹配逻辑：在给定的 EPG 子集和索引中查找频道
         * 匹配顺序：映射表 → EPG ID → 精确匹配 → 规范化匹配 → 子串匹配
         */
        private fun matchInScope(
            channel: Channel,
            epgList: List<Epg>?,
            trieIndex: ChannelTrieIndex,
            idIndex: Map<String, Epg>?
        ): Epg? {
            // 1. 通过映射表匹配
            EpgChannelMapping.findMapping(channel.name)?.let { mapping ->
                mapping.epgId?.let { epgId ->
                    if (epgList != null) {
                        epgList.firstOrNull { epg -> epg.channelList.any { it.equals(epgId, ignoreCase = true) } }
                            ?.let { return it }
                    } else {
                        idIndex?.get(epgId)?.let { return it }
                    }
                }
                if (mapping.epgName.isNotEmpty()) {
                    trieIndex.exactMatch(mapping.epgName)?.let { return it }
                    trieIndex.normalizedMatch(mapping.epgName)?.firstOrNull()?.let { return it }
                }
            }

            // 2. 通过 EPG ID 匹配
            if (!channel.epgId.isNullOrEmpty()) {
                if (epgList != null) {
                    epgList.firstOrNull { epg -> epg.channelList.any { it.equals(channel.epgId, ignoreCase = true) } }
                        ?.let { return it }
                } else {
                    idIndex?.get(channel.epgId)?.let { return it }
                }
            }

            // 3. 通过 Trie 索引匹配（精确 → 规范化 → 子串）
            trieIndex.exactMatch(channel.name)?.let { return it }
            trieIndex.exactMatch(channel.epgName)?.let { return it }
            trieIndex.exactMatch(channel.standardName)?.let { return it }

            trieIndex.normalizedMatch(channel.epgName)?.firstOrNull()?.let { return it }
            trieIndex.normalizedMatch(channel.standardName)?.firstOrNull()?.let { return it }

            trieIndex.substringMatch(channel.epgName).firstOrNull()?.let { return it }
            trieIndex.substringMatch(channel.standardName)?.firstOrNull()?.let { return it }

            return null
        }

        private fun EpgList.performMatch(channel: Channel): Epg {
            val cache = getOrCreateIndexCache()

            // 如果频道有关联的直播源，优先在该源的 EPG 中匹配
            channel.iptvSourceId?.let { sid ->
                val sourceTrieIndex = cache.sourceTrieIndexes[sid] ?: return Epg()
                val sourceEpgList = cache.sourceGroupedIndex[sid]
                return matchInScope(channel, sourceEpgList, sourceTrieIndex, null) ?: Epg()
            }

            // 没有关联直播源时，使用全局匹配
            return matchInScope(channel, null, cache.channelTrieIndex, cache.idIndex) ?: Epg()
        }

        fun EpgList.matchAll(channels: List<Channel>): Map<Channel, Epg?> =
            channels.associateWith { match(it) }

        fun clearCache() {
            UnifiedCacheManager.clearCache(UnifiedCacheManager.CacheNames.CHANNEL_MATCH)
            indexCacheRef = null
            Epg.clearRecentProgrammeCache()
            ChannelTrieIndex.clearCaches()
            EpgChannelMapping.clearIndexes()
        }

        fun example(channelList: ChannelList): EpgList =
            EpgList(channelList.map(Epg.Companion::example))

        private val semaphore = Semaphore(1)
        suspend fun <T> action(action: () -> T): T =
            semaphore.withPermit { withContext(Dispatchers.Default) { action() } }
    }
}
