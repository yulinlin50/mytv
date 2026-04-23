package top.yogiczy.mytv.core.data.repositories.epg

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import top.yogiczy.mytv.core.data.entities.epg.Epg
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeList
import top.yogiczy.mytv.core.data.utils.ChannelAlias
import top.yogiczy.mytv.core.data.utils.ChineseConverter
import top.yogiczy.mytv.core.data.utils.Loggable
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.time.measureTimedValue
import top.yogiczy.mytv.core.data.utils.LruMutableCache

object EpgParser : Loggable("EpgParser") {
    private val sdfCache = mutableMapOf<String, ThreadLocal<SimpleDateFormat>>()
    private val channelNameCache = LruMutableCache<String, Set<String>>(5000)
    private val timeParseCache = LruMutableCache<String, Long>(10000)
    
    data class ParseStats(
        val totalChannels: Int = 0,
        val totalProgrammes: Int = 0,
        val invalidProgrammes: Int = 0,
        val fixedProgrammes: Int = 0,
        val parseErrors: List<String> = emptyList()
    )
    
    private fun getCachedSdf(format: String): SimpleDateFormat {
        return sdfCache.getOrPut(format) {
            ThreadLocal.withInitial {
                SimpleDateFormat(format, Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Shanghai")
                }
            }
        }.get()!!
    }
    
    private fun parseTime(time: String): Long {
        if (time.isBlank()) return 0
        
        timeParseCache.getTimestamped(time)?.let { return it }
        
        val cleanTime = time.trim()
        
        val formats = listOf(
            "yyyyMMddHHmmss Z",
            "yyyyMMddHHmmssZ",
            "yyyyMMddHHmmss",
            "yyyyMMddHHmm"
        )
        
        for (format in formats) {
            try {
                val sdf = getCachedSdf(format)
                val date = sdf.parse(cleanTime)
                if (date != null) {
                    val result = date.time
                    timeParseCache.putTimestamped(time, result)
                    return result
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        log.w("无法解析时间：$time")
        return 0
    }
    
    private data class ChannelItem(
        val id: String,
        val displayNames: MutableSet<String> = mutableSetOf(),
        var icon: String? = null,
    ) {
        fun addDisplayNames(names: Set<String>) {
            names.forEach { name ->
                if (name.isNotBlank()) {
                    displayNames.add(name)
                }
            }
        }
        
        fun getDisplayNamesList(): List<String> = displayNames.toList()
    }

    private data class ProgrammeItem(
        val channel: String,
        val start: Long,
        val end: Long,
        val title: String,
        val isValid: Boolean = true
    )

    private fun buildChannelNames(originalName: String, includeId: String? = null): Set<String> {
        val cacheKey = if (includeId != null) "$originalName|$includeId" else originalName
        
        channelNameCache.getTimestamped(cacheKey)?.let { return it }
        
        val namesToAdd = mutableSetOf<String>()
        
        if (includeId != null) {
            namesToAdd.add(includeId)
        }
        
        val displayName = ChannelAlias.standardChannelName(originalName)
        namesToAdd.add(originalName)
        namesToAdd.add(displayName)
        
        val simplifiedName = ChineseConverter.toSimplified(originalName)
        if (simplifiedName != originalName) {
            namesToAdd.add(simplifiedName)
        }
        
        val simplifiedDisplayName = ChineseConverter.toSimplified(displayName)
        if (simplifiedDisplayName != displayName && simplifiedDisplayName != simplifiedName) {
            namesToAdd.add(simplifiedDisplayName)
        }
        
        val result = namesToAdd.filter { it.isNotBlank() }.toSet()
        channelNameCache.putTimestamped(cacheKey, result)
        return result
    }

    private suspend fun parse(inputStream: InputStream): Pair<EpgList, ParseStats> = withContext(Dispatchers.IO) {
        inputStream.use { stream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream.reader(Charsets.UTF_8))

            var lastChannel: ChannelItem? = null
            val channelList = mutableListOf<ChannelItem>()
            val existingChannelIds = mutableSetOf<String>()
            val programmesByChannel = mutableMapOf<String, MutableList<ProgrammeItem>>()
            val parseErrors = mutableListOf<String>()
            var invalidProgrammeCount = 0
            var fixedProgrammeCount = 0
            
            // 调试计数器
            var channelStartCount = 0
            var channelEndCount = 0
            var displayNameCount = 0
            var programmeCount = 0

            var eventType = parser.eventType

            parseLoop@ while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "channel" -> {
                                channelStartCount++
                                lastChannel = ChannelItem(parser.getAttributeValue(null, "id"))
                            }

                            "display-name" -> {
                                displayNameCount++
                                if (lastChannel != null) {
                                    try {
                                        // 直接读取 display-name 的文本内容
                                        val text = parser.nextText()
                                        val originalName = text?.trim() ?: ""
                                        if (originalName.isNotBlank()) {
                                            lastChannel.addDisplayNames(
                                                buildChannelNames(
                                                    originalName,
                                                    includeId = if (lastChannel.displayNames.isEmpty()) lastChannel.id else null
                                                )
                                            )
                                        }
                                        // nextText() 会停在 END_TAG(display-name) 上
                                        // 调用 next() 移动到下一个事件
                                        eventType = parser.next()
                                        // 如果是 TEXT 事件（空白文本节点），继续移动
                                        while (eventType == XmlPullParser.TEXT) {
                                            eventType = parser.next()
                                        }
                                        // 检查是否到达文档末尾
                                        if (eventType == XmlPullParser.END_DOCUMENT) {
                                            break@parseLoop
                                        }
                                        // 已经移动到下一个事件，跳过主循环的 next() 调用
                                        continue
                                    } catch (e: Exception) {
                                        parseErrors.add("解析频道名称失败：${e.message}")
                                        // 发生异常时，尝试跳过当前标签
                                        try {
                                            while (parser.eventType != XmlPullParser.END_TAG && parser.eventType != XmlPullParser.END_DOCUMENT) {
                                                parser.next()
                                            }
                                            eventType = parser.eventType
                                        } catch (e2: Exception) {
                                            parseErrors.add("跳过标签失败：${e2.message}")
                                        }
                                    }
                                } else {
                                    // lastChannel 为 null，跳过 display-name 的内容
                                    try {
                                        parser.nextText()
                                        eventType = parser.next()
                                        while (eventType == XmlPullParser.TEXT) {
                                            eventType = parser.next()
                                        }
                                        if (eventType == XmlPullParser.END_DOCUMENT) {
                                            break@parseLoop
                                        }
                                        continue
                                    } catch (e: Exception) {
                                        // 忽略错误
                                    }
                                }
                            }

                            "icon" -> {
                                lastChannel?.let {
                                    lastChannel.icon = parser.getAttributeValue(null, "src")
                                }
                            }

                            "programme" -> {
                                programmeCount++
                                try {
                                    val channel = parser.getAttributeValue(null, "channel")
                                    val start = parser.getAttributeValue(null, "start")
                                    val stop = parser.getAttributeValue(null, "stop")
                                    
                                    if (channel.isNullOrBlank()) {
                                        parseErrors.add("节目缺少 channel 属性")
                                        // 跳过 programme 的内部内容
                                        var depth = 1
                                        while (depth > 0) {
                                            when (parser.next()) {
                                                XmlPullParser.START_TAG -> depth++
                                                XmlPullParser.END_TAG -> depth--
                                                XmlPullParser.END_DOCUMENT -> break
                                            }
                                        }
                                    } else {
                                        var title = ""
                                        var depth = 1
                                        
                                        while (depth > 0) {
                                            when (parser.next()) {
                                                XmlPullParser.START_TAG -> {
                                                    depth++
                                                    if (parser.name == "title" && title.isEmpty()) {
                                                        title = parser.nextText()?.trim() ?: ""
                                                        depth-- // nextText() 停在 END_TAG 上
                                                    }
                                                }
                                                XmlPullParser.END_TAG -> depth--
                                                XmlPullParser.END_DOCUMENT -> break
                                            }
                                        }

                                        val startTime = parseTime(start ?: "")
                                        val endTime = parseTime(stop ?: "")
                                        
                                        if (startTime > 0 || endTime > 0) {
                                            if (startTime > 0 && endTime > 0 && startTime < endTime) {
                                                programmesByChannel.getOrPut(channel) { mutableListOf() }.add(
                                                    ProgrammeItem(channel, startTime, endTime, title, true)
                                                )
                                            } else if (startTime > 0) {
                                                programmesByChannel.getOrPut(channel) { mutableListOf() }.add(
                                                    ProgrammeItem(channel, startTime, startTime + 3600 * 1000, title, true)
                                                )
                                                fixedProgrammeCount++
                                            } else {
                                                invalidProgrammeCount++
                                                parseErrors.add("节目时间无效：channel=$channel, title=$title")
                                            }
                                        } else {
                                            invalidProgrammeCount++
                                        }
                                        
                                        if (!existingChannelIds.contains(channel)) {
                                            channelList.add(
                                                ChannelItem(
                                                    id = channel,
                                                    displayNames = buildChannelNames(channel).toMutableSet()
                                                )
                                            )
                                            existingChannelIds.add(channel)
                                        }
                                    }
                                } catch (e: Exception) {
                                    parseErrors.add("解析节目失败：${e.message}")
                                    invalidProgrammeCount++
                                    // 发生异常时，尝试跳过 programme 标签
                                    try {
                                        while (parser.eventType != XmlPullParser.END_TAG && 
                                               parser.name != "programme" && 
                                               parser.eventType != XmlPullParser.END_DOCUMENT) {
                                            parser.next()
                                        }
                                    } catch (e2: Exception) {
                                        parseErrors.add("跳过节目标签失败：${e2.message}")
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "channel" -> {
                                channelEndCount++
                                lastChannel?.let {
                                    if (it.displayNames.isEmpty()) {
                                        it.addDisplayNames(buildChannelNames(it.id, it.id))
                                    }
                                    if (it.displayNames.isNotEmpty() && !existingChannelIds.contains(it.id)) {
                                        channelList.add(it)
                                        existingChannelIds.add(it.id)
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        // display-name 的文本内容已通过 nextText() 处理
                        // 其他 TEXT 事件（如空白文本节点）忽略即可
                    }
                }
                
                if (eventType != XmlPullParser.END_DOCUMENT) {
                    eventType = parser.next()
                }
            }

            val totalProgrammes = programmesByChannel.values.sumOf { it.size }
            
            // 输出调试信息
            log.i("解析统计: channelStart=$channelStartCount, channelEnd=$channelEndCount, displayName=$displayNameCount, programme=$programmeCount")
            log.i("最终频道数: ${channelList.size}, 节目数: $totalProgrammes")
            
            val epgList = EpgList(channelList.map { channel ->
                Epg(
                    channelList = channel.getDisplayNamesList(),
                    logo = channel.icon,
                    programmeList = EpgProgrammeList(
                        programmesByChannel[channel.id]
                            ?.map { programme ->
                                EpgProgramme(programme.start, programme.end, programme.title)
                            }
                            ?.sortedBy { it.startAt }
                            ?: emptyList()
                    ),
                )
            })
            
            val stats = ParseStats(
                totalChannels = channelList.size,
                totalProgrammes = totalProgrammes,
                invalidProgrammes = invalidProgrammeCount,
                fixedProgrammes = fixedProgrammeCount,
                parseErrors = parseErrors.take(10) // 只保留前 10 个错误
            )
            
            Pair(epgList, stats)
        }
    }

    suspend fun fromXml(inputStream: InputStream): EpgList {
        return runCatching {
            log.d("开始解析节目单 xml...")
            val t = measureTimedValue { parse(inputStream) }
            val (epgList, stats) = t.value
            
            log.i("节目单 xml 解析完成：${stats.totalChannels}个频道，${stats.totalProgrammes}个节目，" +
                  "无效=${stats.invalidProgrammes}, 修复=${stats.fixedProgrammes}", null, t.duration)
            
            if (stats.parseErrors.isNotEmpty()) {
                log.w("解析警告：${stats.parseErrors.joinToString(", ")}")
            }
            
            if (epgList.isEmpty()) {
                throw Exception("获取节目单为空")
            }

            t.value.first
        }
            .onFailure { log.e("节目单 xml 解析失败", it) }
            .getOrThrow()
    }
    
    /**
     * 带统计信息的解析
     */
    suspend fun fromXmlWithStats(inputStream: InputStream): Pair<EpgList, ParseStats> {
        return runCatching {
            log.d("开始解析节目单 xml...")
            val t = measureTimedValue { parse(inputStream) }
            val (epgList, stats) = t.value
            
            log.i("节目单 xml 解析完成：${stats.totalChannels}个频道，${stats.totalProgrammes}个节目，" +
                  "无效=${stats.invalidProgrammes}, 修复=${stats.fixedProgrammes}", null, t.duration)
            
            if (stats.parseErrors.isNotEmpty()) {
                log.w("解析警告：${stats.parseErrors.joinToString(", ")}")
            }
            
            Pair(epgList, stats)
        }
            .onFailure { 
                log.e("节目单 xml 解析失败", it)
                throw it
            }
            .getOrThrow()
    }
}
