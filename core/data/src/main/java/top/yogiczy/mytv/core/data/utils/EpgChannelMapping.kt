package top.yogiczy.mytv.core.data.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import top.yogiczy.mytv.core.data.R
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class EpgChannelMapping(
    val description: String = "",
    val usage: String = "",
    val mappings: List<MappingItem> = emptyList()
) {
    @Serializable
    data class MappingItem(val channel: String, val epgName: String = "", val epgId: String? = null)

    companion object {
        private val log = Logger.create("EpgChannelMapping")
        private var instance: EpgChannelMapping? = null
        private val buildLock = Any()

        private val exactMatchIndex = ConcurrentHashMap<String, MappingItem>()
        private val normalizedMatchIndex = ConcurrentHashMap<String, MappingItem>()
        private val lowercaseMatchIndex = ConcurrentHashMap<String, MappingItem>()
        @Volatile private var isIndexBuilt = false

        fun load(jsonContent: String): EpgChannelMapping = try {
            instance = Json.decodeFromString(jsonContent)
            buildIndexes()
            log.d("加载 EPG 频道映射配置：${instance?.mappings?.size} 条记录")
            instance!!
        } catch (e: Exception) {
            log.e("加载 EPG 频道映射配置失败", e)
            EpgChannelMapping()
        }

        private fun buildIndexes() = synchronized(buildLock) {
            exactMatchIndex.clear()
            normalizedMatchIndex.clear()
            lowercaseMatchIndex.clear()

            instance?.mappings?.forEach { item ->
                fun indexName(name: String) {
                    exactMatchIndex[name] = item
                    lowercaseMatchIndex[name.lowercase()] = item
                    normalizedMatchIndex[ChannelTrieIndex.normalizeForIndex(name)] = item
                }
                indexName(item.channel)
                if (item.epgName.isNotEmpty()) indexName(item.epgName)
                item.epgId?.let { indexName(it) }
            }

            isIndexBuilt = true
            log.d("索引构建完成: exact=${exactMatchIndex.size}, normalized=${normalizedMatchIndex.size}")
        }

        fun getInstance(): EpgChannelMapping = instance ?: defaultMapping.also { instance = it }

        private val defaultMapping: EpgChannelMapping by lazy {
            runCatching {
                Globals.resources.openRawResource(R.raw.epg_channel_mapping).bufferedReader().use { it.readText() }
            }.getOrElse { "{}" }.let { load(it) }
        }

        fun findMapping(channelName: String): MappingItem? {
            ensureIndexBuilt()
            exactMatchIndex[channelName]?.let { return it }
            lowercaseMatchIndex[channelName.lowercase()]?.let { return it }
            normalizedMatchIndex[ChannelTrieIndex.normalizeForIndex(channelName)]?.let { return it }

            // 模糊匹配：规范化名称的子串包含关系
            val normalized = ChannelTrieIndex.normalizeForIndex(channelName)
            for ((key, item) in normalizedMatchIndex) {
                if (key.contains(normalized) || normalized.contains(key)) return item
            }
            return null
        }

        private fun ensureIndexBuilt() {
            if (!isIndexBuilt) synchronized(buildLock) { if (!isIndexBuilt) buildIndexes() }
        }

        fun findMappingById(epgId: String): MappingItem? {
            ensureIndexBuilt()
            return exactMatchIndex[epgId] ?: lowercaseMatchIndex[epgId.lowercase()]
        }

        fun clearIndexes() = synchronized(buildLock) {
            exactMatchIndex.clear()
            normalizedMatchIndex.clear()
            lowercaseMatchIndex.clear()
            isIndexBuilt = false
        }
    }
}
