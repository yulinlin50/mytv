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
        
        private data class CacheEntry(
            val result: EpgProgrammeRecent,
            val liveIndex: Int = -1
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
                return entry.result
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
                            CacheEntry(it, liveIndex)
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
