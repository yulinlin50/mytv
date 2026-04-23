package top.yogiczy.mytv.core.data.entities.epg

import kotlinx.serialization.Serializable
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.data.utils.UnifiedCacheManager
import kotlin.time.measureTimedValue

@Serializable
data class Epg(
    val channelList: List<String> = emptyList(),
    val logo: String? = null,
    val programmeList: EpgProgrammeList = EpgProgrammeList(),
    val sourceId: String? = null,
) {
    companion object {
        private val log = Logger.create("Epg")
        
        private const val CACHE_DURATION_MS = 5_000L
        
        private data class CacheEntry(
            val result: EpgProgrammeRecent,
            val liveIndex: Int = -1,
            val timestamp: Long = System.currentTimeMillis()
        )

        fun Epg.recentProgramme(): EpgProgrammeRecent {
            if (this == Epg()) return EpgProgrammeRecent()
            if (programmeList.isEmpty()) return EpgProgrammeRecent()

            val cacheKey = channelList.firstOrNull() ?: ""
            val now = System.currentTimeMillis()
            
            UnifiedCacheManager.get<CacheEntry>(
                UnifiedCacheManager.CacheNames.RECENT_PROGRAMME, 
                cacheKey
            )?.let { entry ->
                // 检查缓存是否过期（5秒）
                if (now - entry.timestamp < CACHE_DURATION_MS) {
                    return entry.result
                }
                
                // 检查直播节目是否仍在播放
                if (entry.liveIndex >= 0 && entry.liveIndex < programmeList.size) {
                    val liveProg = programmeList[entry.liveIndex]
                    if (now >= liveProg.startAt && now < liveProg.endAt) {
                        // 节目仍在播放，更新时间戳并返回
                        UnifiedCacheManager.put(
                            UnifiedCacheManager.CacheNames.RECENT_PROGRAMME,
                            cacheKey,
                            entry.copy(timestamp = now)
                        )
                        return entry.result
                    }
                }
            }

            val t = measureTimedValue {
                val currentTime = System.currentTimeMillis()
                
                val sortedProgrammes = if (programmeList.size > 1) {
                    var isSorted = true
                    for (i in 0 until programmeList.size - 1) {
                        if (programmeList[i].startAt > programmeList[i + 1].startAt) {
                            isSorted = false
                            break
                        }
                    }
                    if (!isSorted) {
                        log.w("节目列表未排序，正在排序：${channelList.firstOrNull()}")
                        programmeList.sortedBy { it.startAt }
                    } else {
                        programmeList
                    }
                } else {
                    programmeList
                }

                var liveIndex = -1
                var low = 0
                var high = sortedProgrammes.size - 1
                
                while (low <= high) {
                    val mid = (low + high) / 2
                    val prog = sortedProgrammes[mid]
                    
                    when {
                        currentTime < prog.startAt -> high = mid - 1
                        currentTime >= prog.endAt -> low = mid + 1
                        else -> {
                            liveIndex = mid
                            break
                        }
                    }
                }

                if (liveIndex > -1) EpgProgrammeRecent(
                    now = sortedProgrammes[liveIndex],
                    next = sortedProgrammes.getOrNull(liveIndex + 1)
                ).also {
                    if (cacheKey.isNotEmpty()) {
                        UnifiedCacheManager.put(
                            UnifiedCacheManager.CacheNames.RECENT_PROGRAMME,
                            cacheKey,
                            CacheEntry(it, liveIndex, System.currentTimeMillis())
                        )
                    }
                }
                else EpgProgrammeRecent()
            }
            log.v("recentProgramme: ${channelList.firstOrNull()}", null, t.duration)

            return t.value
        }
        
        fun clearRecentProgrammeCache() {
            UnifiedCacheManager.clearCache(UnifiedCacheManager.CacheNames.RECENT_PROGRAMME)
        }

        fun example(channel: Channel): Epg {
            return Epg(
                channelList = listOf(channel.epgName),
                programmeList = EpgProgrammeList(
                    List(100) { index ->
                        val startAt =
                            System.currentTimeMillis() - 3500 * 1000 * 24 * 2 + index * 3600 * 1000
                        EpgProgramme(
                            title = "${channel.epgName}节目${index + 1}",
                            startAt = startAt,
                            endAt = startAt + 3600 * 1000
                        )
                    }
                )
            )
        }

        fun empty(channel: Channel): Epg {
            return Epg(
                channelList = listOf(channel.epgName),
                programmeList = EpgProgrammeList(listOf(EpgProgramme.EMPTY))
            )
        }
    }
}
