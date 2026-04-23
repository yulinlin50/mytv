package top.yogiczy.mytv.core.data.repositories.iptv.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * m3u直播源解析
 * 支持KODI/APTV标准的回看属性解析
 */
class M3uIptvParser : IptvParser {

    companion object {
        // 预编译正则表达式，避免重复编译带来的性能开销
        private val REGEX_TVG_NAME = Regex("tvg-name=\"(.*?)\"")
        private val REGEX_TVG_ID = Regex("tvg-id=\"(.*?)\"")
        private val REGEX_GROUP_TITLE = Regex("group-title=\"(.+?)\"")
        private val REGEX_TVG_LOGO = Regex("tvg-logo=\"(.+?)\"")
        private val REGEX_HTTP_UA = Regex("http-user-agent=\"(.+?)\"")
        private val REGEX_X_TVG_URL = Regex("x-tvg-url=\"(.*?)\"")
        private val REGEX_URL_TVG = Regex("url-tvg=\"(.*?)\"")
        // 时区偏移参数（APTV标准）
        private val REGEX_TVG_SHIFT = Regex("tvg-shift=\"?([+-]?\\d+(?:\\.\\d+)?)\"?", RegexOption.IGNORE_CASE)
        // 回放相关属性（KODI/APTV标准）- 同时用于全局和行级属性解析
        // 支持 catchup="append" 和 catchup-type="append" 两种格式
        private val REGEX_CATCHUP = Regex("catchup(?:-type)?=\"?([^\\s\"]+)\"?", RegexOption.IGNORE_CASE)
        private val REGEX_CATCHUP_SOURCE = Regex("catchup-source=\"?([^\"]+)\"?", RegexOption.IGNORE_CASE)
        private val REGEX_CATCHUP_DAYS = Regex("catchup-days=\"?(\\d+)\"?", RegexOption.IGNORE_CASE)
        private val REGEX_TIMESHIFT = Regex("timeshift=\"?(\\d+)\"?", RegexOption.IGNORE_CASE)
    }

    override fun isSupport(url: String, data: String): Boolean {
        // 去除BOM头后检查，支持大小写不敏感匹配
        val cleanData = data.removePrefix("\uFEFF").trimStart()
        return cleanData.startsWith("#EXTM3U", ignoreCase = true)
    }

    override suspend fun parse(data: String) =
        withContext(Dispatchers.Default) {
            val lines = data.split("\r\n", "\n")
            val channelList = mutableListOf<IptvParser.ChannelItem>()

            // 解析全局回放属性（从#EXTM3U文件头）
            var globalCatchup: String? = null
            var globalCatchupSource: String? = null
            var globalCatchupDays: Int? = null
            var globalTimeshift: Int? = null

            var addedChannels: List<IptvParser.ChannelItem> = listOf()
            lines.forEach { line ->
                if (line.isBlank()) return@forEach

                val cleanLine = line.removePrefix("\uFEFF").trimStart()

                // 解析#EXTM3U文件头的全局回放属性
                if (cleanLine.startsWith("#EXTM3U", ignoreCase = true)) {
                    // catchup(?:-type)? 正则的 groupValues[1] 是实际的值
                    globalCatchup = REGEX_CATCHUP.find(cleanLine)?.groupValues?.get(1)
                    globalCatchupSource = REGEX_CATCHUP_SOURCE.find(cleanLine)?.groupValues?.get(1)
                    globalCatchupDays = REGEX_CATCHUP_DAYS.find(cleanLine)?.groupValues?.get(1)?.toIntOrNull()
                    globalTimeshift = REGEX_TIMESHIFT.find(cleanLine)?.groupValues?.get(1)?.toIntOrNull()
                    return@forEach
                }

                if (cleanLine.startsWith("#EXTINF")) {
                    val name = cleanLine.split(",").last().trim()
                    val epgName =
                        REGEX_TVG_NAME.find(cleanLine)?.groupValues?.get(1)?.trim()
                            ?.ifBlank { name } ?: name
                    val epgId =
                        REGEX_TVG_ID.find(cleanLine)?.groupValues?.get(1)?.trim()
                    val groupNames =
                        REGEX_GROUP_TITLE.find(cleanLine)?.groupValues?.get(1)?.split(";")
                            ?.map { it.trim() }
                            ?: listOf("其他")
                    val logo = REGEX_TVG_LOGO.find(cleanLine)?.groupValues?.get(1)?.trim()
                    val httpUserAgent =
                        REGEX_HTTP_UA.find(cleanLine)?.groupValues?.get(1)?.trim()

                    // 解析时区偏移参数（tvg-shift）
                    val tvgShift = REGEX_TVG_SHIFT.find(cleanLine)?.groupValues?.get(1)?.toDoubleOrNull()

                    // 解析#EXTINF行中的回放属性（优先级高于全局属性）
                    // catchup(?:-type)? 正则的 groupValues[1] 是实际的值
                    val lineCatchup = REGEX_CATCHUP.find(cleanLine)?.groupValues?.get(1)
                    val lineCatchupSource = REGEX_CATCHUP_SOURCE.find(cleanLine)?.groupValues?.get(1)
                    val lineCatchupDays = REGEX_CATCHUP_DAYS.find(cleanLine)?.groupValues?.get(1)?.toIntOrNull()
                    val lineTimeshift = REGEX_TIMESHIFT.find(cleanLine)?.groupValues?.get(1)?.toIntOrNull()

                    // 合并全局属性和行属性（行属性优先）
                    val finalCatchup = lineCatchup ?: globalCatchup
                    val finalCatchupSource = lineCatchupSource ?: globalCatchupSource
                    val finalCatchupDays = lineCatchupDays ?: globalCatchupDays
                    val finalTimeshift = lineTimeshift ?: globalTimeshift

                    addedChannels = groupNames.map { groupName ->
                        IptvParser.ChannelItem(
                            name = name,
                            epgName = epgName,
                            epgId = epgId,
                            groupName = groupName,
                            url = "",
                            logo = logo,
                            httpUserAgent = httpUserAgent,
                            catchup = finalCatchup,
                            catchupSource = finalCatchupSource,
                            catchupDays = finalCatchupDays,
                            timeshift = finalTimeshift,
                            tvgShift = tvgShift,
                        )
                    }
                } else if (cleanLine.startsWith("#KODIPROP:inputstream.adaptive.manifest_type")) {
                    addedChannels =
                        addedChannels.map { it.copy(manifestType = cleanLine.split("=").last()) }
                } else if (cleanLine.startsWith("#KODIPROP:inputstream.adaptive.license_type")) {
                    addedChannels =
                        addedChannels.map { it.copy(licenseType = cleanLine.split("=").last()) }
                } else if (cleanLine.startsWith("#KODIPROP:inputstream.adaptive.license_key")) {
                    addedChannels =
                        addedChannels.map { it.copy(licenseKey = cleanLine.split("=").last()) }
                } else if (cleanLine.startsWith("#")) {
                    // 解析其他#开头的行中的回放属性（KODI/APTV标准）
                    // catchup(?:-type)? 正则的 groupValues[1] 是实际的值
                    val catchup = REGEX_CATCHUP.find(cleanLine)?.groupValues?.get(1)
                    val catchupSource = REGEX_CATCHUP_SOURCE.find(cleanLine)?.groupValues?.get(1)
                    val catchupDays = REGEX_CATCHUP_DAYS.find(cleanLine)?.groupValues?.get(1)?.toIntOrNull()
                    val timeshift = REGEX_TIMESHIFT.find(cleanLine)?.groupValues?.get(1)?.toIntOrNull()
                    val tvgShift = REGEX_TVG_SHIFT.find(cleanLine)?.groupValues?.get(1)?.toDoubleOrNull()

                    if (catchup != null || catchupSource != null || catchupDays != null || timeshift != null) {
                        addedChannels = addedChannels.map {
                            it.copy(
                                catchup = catchup ?: it.catchup,
                                catchupSource = catchupSource ?: it.catchupSource,
                                catchupDays = catchupDays ?: it.catchupDays,
                                timeshift = timeshift ?: it.timeshift,
                                tvgShift = tvgShift ?: it.tvgShift,
                            )
                        }
                    }
                } else if (cleanLine.startsWith("//")) {
                    return@forEach
                } else {
                    channelList.addAll(addedChannels.map { it.copy(url = cleanLine.trim()) })
                }
            }

            channelList
        }

    override suspend fun getEpgUrl(data: String): String? {
        val lines = data.split("\r\n", "\n")
        return lines.firstOrNull { 
            it.removePrefix("\uFEFF").trimStart().startsWith("#EXTM3U", ignoreCase = true) 
        }?.let { defLine ->
            val cleanLine = defLine.removePrefix("\uFEFF").trimStart()
            REGEX_X_TVG_URL.find(cleanLine)?.groupValues?.get(1)
                ?.split(",")
                ?.firstOrNull()
                ?.trim()
                ?: REGEX_URL_TVG.find(cleanLine)?.groupValues?.get(1)
                    ?.split(",")
                    ?.firstOrNull()
                    ?.trim()
        }
    }
}