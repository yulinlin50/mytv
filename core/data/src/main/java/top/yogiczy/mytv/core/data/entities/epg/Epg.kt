package top.yogiczy.mytv.core.data.entities.epg

import kotlinx.serialization.Serializable
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.utils.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
        
        private val recentProgrammeCache = ConcurrentHashMap<String, CacheEntry>()
        private data class CacheEntry(
            val timestamp: Long,
            val result: EpgProgrammeRecent,
            val liveIndex: Int = -1
        )
        private const val CACHE_DURATION_MS = 5_000L
        private const val MAX_CACHE_SIZE = 200
        private val trimLock = Any()
        private val isTrimming = AtomicBoolean(false)

        fun Epg.recentProgramme(): EpgProgrammeRecent {
            if (this == Epg()) return EpgProgrammeRecent()
            if (programmeList.isEmpty()) return EpgProgrammeRecent()

            val cacheKey = channelList.firstOrNull() ?: ""
            val now = System.currentTimeMillis()
            
            recentProgrammeCache[cacheKey]?.let { entry ->
                if (now - entry.timestamp < CACHE_DURATION_MS) {
                    return entry.result
                }
                
                if (entry.liveIndex >= 0 && entry.liveIndex < programmeList.size) {
                    val liveProg = programmeList[entry.liveIndex]
                    if (now >= liveProg.startAt && now < liveProg.endAt) {
                        recentProgrammeCache[cacheKey] = CacheEntry(now, entry.result, entry.liveIndex)
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
                        recentProgrammeCache[cacheKey] = CacheEntry(now, it, liveIndex)
                        trimCacheIfNeeded()
                    }
                }
                else EpgProgrammeRecent()
            }
            log.v("recentProgramme: ${channelList.firstOrNull()}", null, t.duration)

            return t.value
        }
        
        private fun trimCacheIfNeeded() {
            if (recentProgrammeCache.size <= MAX_CACHE_SIZE) return
            
            if (!isTrimming.compareAndSet(false, true)) return
            
            try {
                synchronized(trimLock) {
                    val currentSize = recentProgrammeCache.size
                    if (currentSize <= MAX_CACHE_SIZE) return
                    
                    val targetSize = MAX_CACHE_SIZE / 2
                    val entriesToRemove = currentSize - targetSize
                    
                    val sortedEntries = recentProgrammeCache.entries
                        .sortedBy { it.value.timestamp }
                        .take(entriesToRemove)
                    
                    sortedEntries.forEach { entry ->
                        recentProgrammeCache.remove(entry.key, entry.value)
                    }
                    
                    log.d("缓存清理: $currentSize -> ${recentProgrammeCache.size}")
                }
            } finally {
                isTrimming.set(false)
            }
        }
        
        fun clearRecentProgrammeCache() {
            synchronized(trimLock) {
                recentProgrammeCache.clear()
            }
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