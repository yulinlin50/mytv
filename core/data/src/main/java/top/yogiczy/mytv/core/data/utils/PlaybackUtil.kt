package top.yogiczy.mytv.core.data.utils

import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min

/**
 * 回放工具类 - 重构版
 * 参考APTV/KODI实现，提供完善的IPTV回放功能支持
 * 
 * 核心功能：
 * 1. generatePlaybackUrl - 主入口函数，生成最终回看URL
 * 2. replacePlaceholders - 占位符替换
 * 3. mergeCatchupTemplate - 智能合并模板到原始URL
 * 
 * 支持的占位符：
 * - 时间戳（秒）：{utc}、{start}、{timestamp}
 * - 时间戳（毫秒）：{lutc}
 * - 结束时间戳（秒）：{utcend}、{end}、{endtimestamp}
 * - 结束时间戳（毫秒）：{lutcend}
 * - 格式化时间：{Y}{m}{d}{H}{M}{S}（如 20260325080000）
 * - 相对偏移：{offset}（当前时间-节目开始时间的秒数）
 * - 节目时长：{duration}（节目时长秒数）
 */
object PlaybackUtil {

    private const val PLAYSEEK_PARAM = "playseek"
    private const val TIMESHIFT_PARAM = "timeshift"
    private val SUPPORTED_CATCHUP_MODES = setOf("default", "true", "yes", "1", "append", "shift", "flussonic", "xc")
    
    /** 最大回放时长：48小时（毫秒） */
    const val MAX_PLAYBACK_DURATION_MS = 48L * 60 * 60 * 1000
    /** 最大时移时长：2小时（毫秒） */
    const val MAX_TIMESHIFT_DURATION_MS = 2L * 60 * 60 * 1000
    
    /** Unix时间戳分界点：2000年1月1日 00:00:00 UTC（毫秒） */
    const val UNIX_TIMESTAMP_THRESHOLD_MS = 946_684_800_000L
    /** Unix时间戳分界点：2000年1月1日 00:00:00 UTC（秒） */
    const val UNIX_TIMESTAMP_THRESHOLD_S = 946_684_800L

    private val PLAYBACK_URL_PATTERNS = listOf(
        "pltv", "tvod", "catchup", "timeshift", "playback", "rec"
    )

    // 预编译的正则表达式（避免重复编译）
    // 支持14位格式化时间(yyyyMMddHHmmss)或10位Unix时间戳
    private val REGEX_PLAYSEEK_EXTRACT = Regex("$PLAYSEEK_PARAM=(\\d{10,14})-(\\d{10,14})", RegexOption.IGNORE_CASE)
    private val REGEX_CATCHUP_ATTR = Regex("catchup=\"?(\\w+)\"?", RegexOption.IGNORE_CASE)
    private val REGEX_CATCHUP_DAYS_ATTR = Regex("catchup-days=\"?(\\d+)\"?", RegexOption.IGNORE_CASE)
    private val REGEX_TIMESHIFT_ATTR = Regex("timeshift=\"?(\\d+)\"?", RegexOption.IGNORE_CASE)
    private val REGEX_TVOD_ATTR = Regex("tvod=\"?(\\d+)\"?", RegexOption.IGNORE_CASE)
    private val REGEX_CATCHUP_SOURCE_ATTR = Regex("catchup-source=\"?([^\"]+)\"?", RegexOption.IGNORE_CASE)

    // 时间范围提取的正则表达式
    private val REGEX_FLUSSONIC = Regex("""from=(\d+)&to=(\d+)""", RegexOption.IGNORE_CASE)
    private val REGEX_UTC_START_END = Regex("""utcstart=(\d+)&utcend=(\d+)""", RegexOption.IGNORE_CASE)
    private val REGEX_START_END = Regex("""[?&]start=(\d+)&end=(\d+)""", RegexOption.IGNORE_CASE)
    // SHIFT模式：从模板URL中提取占位符替换后的时间戳
    private val REGEX_SHIFT_TIMESTAMP = Regex("""/(\d{10,13})/(\d{10,13})/""", RegexOption.IGNORE_CASE)

    // isPlaybackUrl 使用的正则表达式（预编译）
    private val REGEX_TIMESHIFT_URL = Regex("""(?:[?&]|/)timeshift(?:=|/)""", RegexOption.IGNORE_CASE)
    private val REGEX_CATCHUP_URL = Regex("""[?&]catchup=""", RegexOption.IGNORE_CASE)

    // 公开的时间格式，供其他模块使用（ThreadLocal 保证线程安全）
    val UTC_TIME_FORMAT: SimpleDateFormat
        get() = utcTimeFormatTL.get()!!

    val LOCAL_TIME_FORMAT: SimpleDateFormat
        get() = localTimeFormatTL.get()!!

    private val utcTimeFormatTL = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val localTimeFormatTL = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    // ==================== 核心入口函数 ====================

    /**
     * 生成回放URL - 主入口函数（从节目信息生成）
     *
     * 处理流程：
     * 1. 应用时区偏移（tvgShift）到节目时间
     * 2. 调用内部方法生成URL
     *
     * @param channelLine 频道线路（包含原始URL、catchup-source、tvgShift等）
     * @param programme 节目信息（包含开始/结束时间，原始Unix时间戳）
     * @param useUtcTime 是否使用UTC时间（某些国内源需要本地时间）
     * @param offsetSeconds 开始时间偏移（秒），用于跳过片头
     * @return 最终的回放URL
     */
    fun generatePlaybackUrl(
        channelLine: ChannelLine,
        programme: EpgProgramme,
        useUtcTime: Boolean = true,
        offsetSeconds: Int = 0
    ): String {
        // 应用时区偏移到节目时间
        val shiftedStartTimeMs = applyTimezoneShift(programme.startAt, channelLine.tvgShift)
        val shiftedEndTimeMs = applyTimezoneShift(programme.endAt, channelLine.tvgShift)
        // 加上 offsetSeconds（跳过片头），但确保不超过结束时间
        val offsetMs = (offsetSeconds * 1000L).coerceAtLeast(0)
        val startTimeMs = (shiftedStartTimeMs + offsetMs).coerceAtMost(shiftedEndTimeMs)
        val endTimeMs = shiftedEndTimeMs
        return generatePlaybackUrlInternal(channelLine, startTimeMs, endTimeMs, useUtcTime)
    }

    /**
     * 生成回放URL - 重载函数（直接接收时间戳）
     *
     * ⚠️ 重要：此函数假设传入的时间戳已经应用过时区偏移
     * 如果需要自动应用时区偏移，请使用接收 EpgProgramme 的重载版本
     *
     * @param channelLine 频道线路
     * @param startTimeMs 开始时间戳（毫秒，已应用时区偏移）
     * @param endTimeMs 结束时间戳（毫秒，已应用时区偏移）
     * @param useUtcTime 是否使用UTC时间
     * @return 最终的回放URL
     */
    fun generatePlaybackUrl(
        channelLine: ChannelLine,
        startTimeMs: Long,
        endTimeMs: Long,
        useUtcTime: Boolean = true
    ): String {
        // 传入的时间戳应该已经应用过时区偏移，不再重复应用
        return generatePlaybackUrlInternal(channelLine, startTimeMs, endTimeMs, useUtcTime)
    }

    /**
     * 内部核心方法 - 生成回放URL的公共逻辑
     * 时间参数必须已经应用过时区偏移
     */
    private fun generatePlaybackUrlInternal(
        channelLine: ChannelLine,
        startTimeMs: Long,
        endTimeMs: Long,
        useUtcTime: Boolean
    ): String {
        val originalUrl = channelLine.playableUrl

        // ===== 步骤1: 模板处理 =====
        // 优先使用 catchup-source 模板
        val catchupSource = channelLine.catchupSource
        if (!catchupSource.isNullOrBlank()) {
            return processCatchupSource(originalUrl, catchupSource, startTimeMs, endTimeMs, useUtcTime)
        }

        // 根据 catchup 模式生成URL
        val catchupMode = channelLine.catchup?.let { getPlaybackModeFromString(it) }
        if (catchupMode != null && catchupMode != PlaybackMode.DEFAULT) {
            return generateUrlByMode(originalUrl, catchupMode, startTimeMs, endTimeMs, useUtcTime)
        }

        // ===== 步骤2: 默认回退方案 =====
        return generateDefaultPlaybackUrl(originalUrl, startTimeMs, endTimeMs, useUtcTime)
    }

    // ==================== 占位符替换 ====================

    /**
     * 替换模板中的所有占位符
     * 
     * 同义词处理（兼容不同M3U习惯）：
     * - {utc}、{start}、{timestamp} → 开始时间戳（秒），视为同义词
     * - {utcend}、{end}、{endtimestamp} → 结束时间戳（秒），视为同义词
     * - {lutc}、{lutc-start} → 开始时间戳（毫秒）
     * - {lutcend}、{lutc-end} → 结束时间戳（毫秒）
     * 
     * 重要：传入的时间参数必须已经应用了时区偏移（tvgShift）
     * 
     * @param template 包含占位符的模板字符串
     * @param startTimeMs 开始时间（毫秒，已应用时区偏移）
     * @param endTimeMs 结束时间（毫秒，已应用时区偏移）
     * @param useUtcTime 是否使用UTC时间格式
     * @return 替换后的字符串
     */
    private fun replacePlaceholders(
        template: String,
        startTimeMs: Long,
        endTimeMs: Long,
        useUtcTime: Boolean = true
    ): String {
        val timeFormat = if (useUtcTime) UTC_TIME_FORMAT else LOCAL_TIME_FORMAT
        val now = System.currentTimeMillis()
        
        // 计算相对偏移和时长（基于已应用时区偏移的时间）
        // 注意：当节目尚未开始时，offsetSeconds 为 0，durationSeconds 保持正常计算
        val offsetSeconds = if (now >= startTimeMs) ((now - startTimeMs) / 1000).toInt() else 0
        val durationSeconds = ((endTimeMs - startTimeMs) / 1000).toInt().coerceAtLeast(0)

        // 时间戳（秒）- 10位
        val startTimestamp = startTimeMs / 1000
        val endTimestamp = endTimeMs / 1000

        return template
            // ===== 时间戳（秒）- 10位 =====
            // 同义词：{utc}、{start}、{timestamp} 都表示开始时间
            .replace("{utc}", startTimestamp.toString())
            .replace("{start}", startTimestamp.toString())
            .replace("{timestamp}", startTimestamp.toString())
            .replace("{utc-start}", startTimestamp.toString())
            // 同义词：{utcend}、{end}、{endtimestamp} 都表示结束时间
            .replace("{utcend}", endTimestamp.toString())
            .replace("{end}", endTimestamp.toString())
            .replace("{endtimestamp}", endTimestamp.toString())
            .replace("{utc-end}", endTimestamp.toString())
            
            // ===== 时间戳（毫秒）- 13位 =====
            .replace("{lutc}", startTimeMs.toString())
            .replace("{lutc-start}", startTimeMs.toString())
            .replace("{lutcend}", endTimeMs.toString())
            .replace("{lutc-end}", endTimeMs.toString())
            
            // ===== 格式化时间字符串 =====
            .replace("{YmdHMS}", timeFormat.format(startTimeMs))
            .replace("{start:YmdHMS}", timeFormat.format(startTimeMs))
            .replace("{end:YmdHMS}", timeFormat.format(endTimeMs))
            .replace("{yyyyMMddHHmmss}", timeFormat.format(startTimeMs))
            .replace("{YYYYMMddHHmmss}", timeFormat.format(startTimeMs))
            .replace("{end:yyyyMMddHHmmss}", timeFormat.format(endTimeMs))
            .replace("{end:YYYYMMddHHmmss}", timeFormat.format(endTimeMs))
            // 仅日期格式
            .replace("{yyyyMMdd}", timeFormat.format(startTimeMs).substring(0, 8))
            .replace("{YYYYMMdd}", timeFormat.format(startTimeMs).substring(0, 8))
            .replace("{end:yyyyMMdd}", timeFormat.format(endTimeMs).substring(0, 8))
            .replace("{end:YYYYMMdd}", timeFormat.format(endTimeMs).substring(0, 8))
            
            // ===== APTV/KODI 兼容格式 =====
            // 根据 useUtcTime 参数选择时间格式，与其他占位符保持一致
            .replace("\${start}", timeFormat.format(startTimeMs))
            .replace("\${end}", timeFormat.format(endTimeMs))
            .replace("\${timestamp}", startTimestamp.toString())
            
            // ===== 带括号的时间戳格式 (b=begin/start, e=end) =====
            .replace("\${(b)timestamp}", startTimestamp.toString())
            .replace("\${(e)timestamp}", endTimestamp.toString())
            .replace("\${(b)time}", timeFormat.format(startTimeMs))
            .replace("\${(e)time}", timeFormat.format(endTimeMs))
            .replace("\${(b)utc}", startTimestamp.toString())
            .replace("\${(e)utc}", endTimestamp.toString())
            .replace("\${(b)start}", startTimestamp.toString())
            .replace("\${(e)end}", endTimestamp.toString())
            .replace("\${(b)lutc}", startTimeMs.toString())
            .replace("\${(e)lutc}", endTimeMs.toString())
            // 格式化时间格式
            .replace("\${(b)yyyyMMddHHmmss}", timeFormat.format(startTimeMs))
            .replace("\${(e)yyyyMMddHHmmss}", timeFormat.format(endTimeMs))
            .replace("\${(b)YYYYMMddHHmmss}", timeFormat.format(startTimeMs))
            .replace("\${(e)YYYYMMddHHmmss}", timeFormat.format(endTimeMs))
            .replace("\${(b)YmdHMS}", timeFormat.format(startTimeMs))
            .replace("\${(e)YmdHMS}", timeFormat.format(endTimeMs))
            // 仅日期格式
            .replace("\${(b)YYYYMMdd}", timeFormat.format(startTimeMs).substring(0, 8))
            .replace("\${(e)YYYYMMdd}", timeFormat.format(endTimeMs).substring(0, 8))
            .replace("\${(b)yyyyMMdd}", timeFormat.format(startTimeMs).substring(0, 8))
            .replace("\${(e)yyyyMMdd}", timeFormat.format(endTimeMs).substring(0, 8))
            
            // ===== 相对偏移和时长 =====
            .replace("{offset}", offsetSeconds.toString())
            .replace("{offset_seconds}", offsetSeconds.toString())
            .replace("{duration}", durationSeconds.toString())
            .replace("{duration_seconds}", durationSeconds.toString())
            
            // ===== 单独的时间组件 {Y}{m}{d}{H}{M}{S} =====
            .let { replaceTimeComponents(it, startTimeMs, "") }
            .let { replaceTimeComponents(it, endTimeMs, "end") }
    }

    /**
     * 替换单独的时间组件占位符
     * 支持 {Y}、{m}、{d}、{H}、{M}、{S} 以及 {end:Y}、{end:m} 等
     */
    private fun replaceTimeComponents(template: String, timeMs: Long, prefix: String): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timeMs }

        val components = mapOf(
            'Y' to calendar.get(Calendar.YEAR).toString().padStart(4, '0'),
            'm' to (calendar.get(Calendar.MONTH) + 1).toString().padStart(2, '0'),
            'd' to calendar.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0'),
            'H' to calendar.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0'),
            'M' to calendar.get(Calendar.MINUTE).toString().padStart(2, '0'),
            'S' to calendar.get(Calendar.SECOND).toString().padStart(2, '0'),
        )

        return components.entries.fold(template) { result, (char, value) ->
            val placeholder = if (prefix.isEmpty()) "{$char}" else "{$prefix:$char}"
            result.replace(placeholder, value)
        }
    }

    // ==================== 智能合并逻辑 ====================

    /**
     * 处理 catchup-source 模板
     * 
     * @param originalUrl 原始直播URL
     * @param catchupSource catchup-source 模板
     * @param startTimeMs 开始时间
     * @param endTimeMs 结束时间
     * @param useUtcTime 是否使用UTC时间
     * @return 最终的回放URL
     */
    private fun processCatchupSource(
        originalUrl: String,
        catchupSource: String,
        startTimeMs: Long,
        endTimeMs: Long,
        useUtcTime: Boolean
    ): String {
        // 先替换占位符
        val processedTemplate = replacePlaceholders(catchupSource, startTimeMs, endTimeMs, useUtcTime)
        
        // 智能合并
        return mergeCatchupTemplate(originalUrl, processedTemplate)
    }

    /**
     * 智能合并模板到原始URL
     * 
     * 合并规则：
     * 1. 如果模板是完整URL（以 http/https/rtsp/rtp 开头），直接返回
     * 2. 如果模板以 ? 开头，追加到原始URL
     * 3. 如果模板以 & 开头，追加到原始URL
     * 4. 如果模板以 / 开头，替换原始URL的路径部分
     * 5. 其他情况，根据原始URL是否已有参数决定用 ? 或 & 连接
     * 
     * 安全检查：
     * - 防止拼接出 http://http:// 这种错误链接
     * - 检查模板中是否已包含协议头
     * 
     * @param originalUrl 原始直播URL
     * @param template 处理后的模板（已替换占位符）
     * @return 合并后的完整回放URL
     */
    private fun mergeCatchupTemplate(originalUrl: String, template: String): String {
        // 安全检查：如果模板为空，直接返回原始URL
        if (template.isBlank()) return originalUrl

        // 安全检查：检测模板中是否已包含协议头（防止 http://http:// 错误）
        val hasProtocolInTemplate = template.contains("://") && (
            template.contains("http://") ||
            template.contains("https://") ||
            template.contains("rtsp://") ||
            template.contains("rtp://")
        )

        // 1. 完整URL - 直接返回（模板本身就是完整URL）
        if (template.startsWith("http://") || template.startsWith("https://") ||
            template.startsWith("rtsp://") || template.startsWith("rtp://")) {
            return template
        }

        // 安全检查：如果模板包含协议头但不是以协议头开头，需要特殊处理
        // 例如：template = "http://other.com/path" 但不是以 http:// 开头
        // 这种情况直接返回模板
        if (hasProtocolInTemplate) {
            return template
        }

        // 清理原始URL中已存在的回放参数，避免重复
        val cleanedUrl = removeExistingPlaybackParams(originalUrl)

        // 2. 查询参数 - 追加到原始URL（防止双问号）
        if (template.startsWith("?") || template.startsWith("&")) {
            return if (template.startsWith("?")) {
                // 模板以 ? 开头
                if (cleanedUrl.contains("?")) {
                    // 原始URL已有 ?，将模板的 ? 替换为 &
                    "$cleanedUrl&${template.substring(1)}"
                } else {
                    // 原始URL没有 ?，直接追加
                    "$cleanedUrl$template"
                }
            } else {
                // 模板以 & 开头，直接追加
                "$cleanedUrl$template"
            }
        }

        // 3. 绝对路径 - 替换原始URL的路径
        if (template.startsWith("/")) {
            return try {
                val uri = URI(cleanedUrl)
                val portPart = if (uri.port > 0) ":${uri.port}" else ""
                "${uri.scheme}://${uri.host}$portPart$template"
            } catch (e: Exception) {
                "$cleanedUrl$template"
            }
        }

        // 4. 相对路径或参数 - 智能拼接
        return if (cleanedUrl.contains("?")) {
            "$cleanedUrl&$template"
        } else {
            "$cleanedUrl?$template"
        }
    }

    // ==================== 模式化URL生成 ====================

    /**
     * 根据 catchup 模式生成URL
     */
    private fun generateUrlByMode(
        originalUrl: String,
        mode: PlaybackMode,
        startTimeMs: Long,
        endTimeMs: Long,
        useUtcTime: Boolean
    ): String {
        return when (mode) {
            PlaybackMode.APPEND -> generateAppendModeUrl(originalUrl, startTimeMs, endTimeMs, useUtcTime)
            PlaybackMode.SHIFT -> generateShiftModeUrl(originalUrl, startTimeMs, endTimeMs, useUtcTime)
            PlaybackMode.FLUSSONIC -> generateFlussonicUrl(originalUrl, startTimeMs, endTimeMs)
            PlaybackMode.XC -> generateXcUrl(originalUrl, startTimeMs, endTimeMs, useUtcTime)
            PlaybackMode.DEFAULT -> generateDefaultPlaybackUrl(originalUrl, startTimeMs, endTimeMs, useUtcTime)
        }
    }

    /**
     * APPEND模式：追加参数到URL
     * 格式：?start=20260323080000&end=20260323090000
     */
    private fun generateAppendModeUrl(
        originalUrl: String,
        startTimeMs: Long,
        endTimeMs: Long,
        useUtcTime: Boolean
    ): String {
        val timeFormat = if (useUtcTime) UTC_TIME_FORMAT else LOCAL_TIME_FORMAT
        val param = "start=${timeFormat.format(startTimeMs)}&end=${timeFormat.format(endTimeMs)}"
        val cleanedUrl = removeExistingPlaybackParams(originalUrl)
        return appendUrlParameter(cleanedUrl, param)
    }

    /**
     * SHIFT模式：替换URL中的占位符
     */
    private fun generateShiftModeUrl(
        originalUrl: String,
        startTimeMs: Long,
        endTimeMs: Long,
        useUtcTime: Boolean
    ): String {
        // SHIFT模式直接替换占位符，不需要清理参数
        return replacePlaceholders(originalUrl, startTimeMs, endTimeMs, useUtcTime)
    }

    /**
     * Flussonic模式：使用时间戳
     * 格式：?from=1680000000&to=1680003600
     */
    private fun generateFlussonicUrl(
        originalUrl: String,
        startTimeMs: Long,
        endTimeMs: Long
    ): String {
        val param = "from=${startTimeMs / 1000}&to=${endTimeMs / 1000}"
        val cleanedUrl = removeExistingPlaybackParams(originalUrl)
        return appendUrlParameter(cleanedUrl, param)
    }

    /**
     * XC模式：使用格式化时间
     * 格式：?utcstart=20260323080000&utcend=20260323090000
     */
    private fun generateXcUrl(
        originalUrl: String,
        startTimeMs: Long,
        endTimeMs: Long,
        useUtcTime: Boolean
    ): String {
        val timeFormat = if (useUtcTime) UTC_TIME_FORMAT else LOCAL_TIME_FORMAT
        val param = "utcstart=${timeFormat.format(startTimeMs)}&utcend=${timeFormat.format(endTimeMs)}"
        val cleanedUrl = removeExistingPlaybackParams(originalUrl)
        return appendUrlParameter(cleanedUrl, param)
    }

    /**
     * 默认回放URL生成
     * 
     * 重要：对于 playseek 参数，始终使用 Unix 时间戳（秒级）
     * 因为 Unix 时间戳是与时区无关的，可以避免时区解析问题
     */
    private fun generateDefaultPlaybackUrl(
        originalUrl: String,
        startTimeMs: Long,
        endTimeMs: Long,
        useUtcTime: Boolean
    ): String {
        // 对于 playseek 参数，始终使用 Unix 时间戳（秒级），与时区无关
        val startTime = (startTimeMs / 1000).toString()
        val endTime = (endTimeMs / 1000).toString()

        // 先检查原始URL的类型（在清理之前）
        val urlLower = originalUrl.lowercase()
        val hasPlayseek = urlLower.contains("playseek")
        val hasTvod = urlLower.contains("tvod")
        val hasCatchup = urlLower.contains("catchup")
        val hasTimeshift = urlLower.contains("timeshift")

        // 移除已存在的回放参数，避免重复追加导致服务器使用旧的参数值
        val cleanedUrl = removeExistingPlaybackParams(originalUrl)
        val playbackUrl = convertToPlaybackUrl(cleanedUrl)

        return when {
            hasPlayseek || hasTvod || hasCatchup -> {
                appendUrlParameter(playbackUrl, "$PLAYSEEK_PARAM=$startTime-$endTime")
            }
            hasTimeshift -> {
                appendUrlParameter(playbackUrl, "$TIMESHIFT_PARAM=$startTime")
            }
            else -> {
                appendUrlParameter(playbackUrl, "$PLAYSEEK_PARAM=$startTime-$endTime")
            }
        }
    }

    /**
     * 移除URL中已存在的回放相关参数
     * 避免在生成新的回放URL时重复追加参数
     * 
     * 注意：只移除明确是回放相关的参数，避免误删其他用途的参数
     * - playseek: 华为/海思平台回放参数
     * - timeshift: 时移参数
     * - catchup: 回放类型标识
     * - from/to: Flussonic格式时间戳
     * - utcstart/utcend: XC格式时间
     * - start/end: 仅在值看起来像时间戳时才移除（14位数字或10位时间戳）
     */
    private fun removeExistingPlaybackParams(url: String): String {
        if (url.isBlank()) return url
        val fragmentIndex = url.indexOf('#')
        val baseWithQuery = if (fragmentIndex >= 0) url.substring(0, fragmentIndex) else url
        val fragment = if (fragmentIndex >= 0) url.substring(fragmentIndex) else ""
        val queryIndex = baseWithQuery.indexOf('?')
        if (queryIndex < 0) return url

        val base = baseWithQuery.substring(0, queryIndex)
        val filteredQuery = baseWithQuery.substring(queryIndex + 1)
            .split("&")
            .filter { it.isNotBlank() }
            .filterNot { segment ->
                val key = segment.substringBefore("=").lowercase()
                val value = segment.substringAfter("=", "")
                
                when (key) {
                    // 明确是回放相关的参数，直接移除
                    PLAYSEEK_PARAM, TIMESHIFT_PARAM, "catchup" -> true
                    "from", "to" -> value.matches(Regex("\\d+")) // 只有值是纯数字（时间戳）时才移除
                    "utcstart", "utcend", "utc-start", "utc-end" -> true
                    "lutc-start", "lutc-end" -> true
                    // start/end 参数：只有值看起来像时间戳时才移除，避免误删其他用途的参数
                    "start", "end" -> value.matches(Regex("\\d{14}")) || // 14位格式化时间
                                       value.matches(Regex("\\d{10}")) ||  // 10位时间戳（秒）
                                       value.matches(Regex("\\d{13}"))     // 13位时间戳（毫秒）
                    else -> false
                }
            }
            .joinToString("&")

        return buildString {
            append(base)
            if (filteredQuery.isNotBlank()) {
                append("?")
                append(filteredQuery)
            }
            append(fragment)
        }
    }

    // ==================== 时区处理 ====================

    /**
     * 应用时区偏移到时间戳
     * tvg-shift 单位为小时，正数表示东时区，负数表示西时区
     */
    fun applyTimezoneShift(timeMs: Long, tvgShift: Double?): Long {
        if (tvgShift == null) return timeMs
        val shiftMs = (tvgShift * 60 * 60 * 1000).toLong()
        return timeMs + shiftMs
    }

    fun getTimezoneShiftMs(tvgShift: Double?): Long {
        if (tvgShift == null) return 0L
        return (tvgShift * 60 * 60 * 1000).toLong()
    }

    // ==================== 辅助函数 ====================

    private fun getPlaybackModeFromString(mode: String): PlaybackMode {
        return when (mode.lowercase()) {
            "append" -> PlaybackMode.APPEND
            "shift" -> PlaybackMode.SHIFT
            "flussonic" -> PlaybackMode.FLUSSONIC
            "xc" -> PlaybackMode.XC
            "default", "true", "yes", "1" -> PlaybackMode.DEFAULT
            else -> PlaybackMode.DEFAULT
        }
    }

    fun appendUrlParameter(url: String, parameter: String): String {
        if (url.isBlank()) return url
        return try {
            val uri = URI(url)
            if (uri.query.isNullOrBlank()) "$url?$parameter" else "$url&$parameter"
        } catch (e: Exception) {
            if (url.contains("?")) "$url&$parameter" else "$url?$parameter"
        }
    }

    fun isPlaybackUrl(url: String): Boolean {
        if (url.isBlank()) return false

        val lowerUrl = url.lowercase()
        if (lowerUrl.contains("playseek=")) return true
        if (REGEX_TIMESHIFT_URL.containsMatchIn(url)) return true
        if (REGEX_CATCHUP_URL.containsMatchIn(url)) return true

        return extractPlaybackTimeRange(url) != null
    }

    fun convertToPlaybackUrl(url: String): String {
        if (url.isBlank()) return url
        return url
            .replace("pltv", "tvod", ignoreCase = true)
            .replace("/live/", "/timeshift/", ignoreCase = true)
    }

    // ==================== 兼容旧API ====================

    /**
     * 构建回放URL - 兼容旧API
     * @deprecated 请使用 generatePlaybackUrl
     */
    fun buildPlaybackUrl(
        channelLine: ChannelLine,
        programme: EpgProgramme,
        useUtcTime: Boolean = true,
        offsetSeconds: Int = 0
    ): String {
        return generatePlaybackUrl(channelLine, programme, useUtcTime, offsetSeconds)
    }

    // ==================== 检查函数 ====================

    fun isPlaybackSupported(url: String): Boolean {
        if (url.isBlank()) return false
        return PLAYBACK_URL_PATTERNS.any { url.contains(it, ignoreCase = true) }
    }

    fun isPlaybackSupported(channelLine: ChannelLine): Boolean {
        if (isPlaybackSupported(channelLine.url)) return true
        if (!channelLine.catchup.isNullOrBlank()) {
            val value = channelLine.catchup.lowercase()
            if (value in SUPPORTED_CATCHUP_MODES) return true
        }
        if (!channelLine.catchupSource.isNullOrBlank()) return true
        if (channelLine.catchupDays != null && channelLine.catchupDays > 0) return true
        if (channelLine.timeshift != null && channelLine.timeshift > 0) return true
        return false
    }

    fun isCatchupSupported(channelLine: ChannelLine): Boolean {
        val url = channelLine.url.lowercase()
        if (url.contains("playseek=")) return true
        if (url.contains("catchup")) return true
        if (url.contains("tvod")) return true
        if (!channelLine.catchup.isNullOrBlank()) {
            val value = channelLine.catchup.lowercase()
            if (value in SUPPORTED_CATCHUP_MODES) return true
        }
        if (!channelLine.catchupSource.isNullOrBlank()) return true
        if (channelLine.catchupDays != null && channelLine.catchupDays > 0) return true
        return false
    }

    /**
     * UI should be more conservative than runtime playback attempts. We only show
     * replay affordances when the source explicitly advertises catchup support or
     * the live URL clearly belongs to a known replay-capable family.
     */
    fun isPlaybackAdvertised(channelLine: ChannelLine): Boolean {
        if (hasPlaybackUrlSignature(channelLine.url)) return true
        if (hasRecognizedCatchupMode(channelLine.catchup)) return true
        if (!channelLine.catchupSource.isNullOrBlank()) return true
        return false
    }

    fun canTryPlayback(channelLine: ChannelLine): Boolean {
        if (isPlaybackSupported(channelLine)) return true
        val url = channelLine.url.lowercase()
        return url.startsWith("http://") || url.startsWith("https://")
    }

    fun canPlaybackProgramme(programme: EpgProgramme): Boolean {
        val now = System.currentTimeMillis()
        if (programme.endAt > now) return false
        if (now - programme.endAt > MAX_PLAYBACK_DURATION_MS) return false
        return true
    }

    /**
     * 检查节目是否可以回看（考虑直播源的catchupDays设置）
     * @param programme 节目
     * @param channelLine 频道线路（用于获取catchupDays）
     * @return 是否可以回看
     */
    fun canPlaybackProgramme(programme: EpgProgramme, channelLine: ChannelLine): Boolean {
        if (!isCatchupSupported(channelLine)) return false
        val now = System.currentTimeMillis()
        if (programme.endAt > now) return false

        // 计算最大回看时长：取应用限制和直播源限制的最小值
        val maxPlaybackDurationMs = getMaxPlaybackDurationMs(channelLine)
        if (now - programme.endAt > maxPlaybackDurationMs) return false
        return true
    }

    /**
     * 获取最大回看时长（毫秒）
     * 优先级：优先使用直播源设置的catchupDays，完全遵循直播源设置
     * @param channelLine 频道线路
     * @return 最大回看时长（毫秒）
     */
    fun getMaxPlaybackDurationMs(channelLine: ChannelLine): Long {
        return channelLine.catchupDays?.let { days ->
            // 完全遵循直播源设置的catchupDays，不限制最大天数
            if (days > 0) days * 24L * 60 * 60 * 1000
            else MAX_PLAYBACK_DURATION_MS
        } ?: MAX_PLAYBACK_DURATION_MS
    }

    /**
     * 获取最大回看时长（天）
     * @param channelLine 频道线路
     * @return 最大回看天数
     */
    fun getMaxPlaybackDays(channelLine: ChannelLine): Int {
        // 使用 ceil 向上取整，确保 47 小时显示为 2 天而不是 1 天
        return kotlin.math.ceil(getMaxPlaybackDurationMs(channelLine) / (24 * 60 * 60 * 1000.0)).toInt()
    }

    fun getMaxTimeshiftDurationMs(channelLine: ChannelLine): Long {
        // 优先使用直播源设置的timeshift（小时），完全遵循直播源设置
        return channelLine.timeshift?.takeIf { it > 0 }?.let { hours ->
            hours * 60L * 60 * 1000
        } ?: MAX_TIMESHIFT_DURATION_MS
    }

    fun canTimeshiftProgramme(programme: EpgProgramme): Boolean {
        val now = System.currentTimeMillis()
        if (programme.startAt > now || programme.endAt < now) return false
        if (now - programme.startAt > MAX_TIMESHIFT_DURATION_MS) return false
        return true
    }

    fun canTimeshiftProgramme(programme: EpgProgramme, channelLine: ChannelLine): Boolean {
        val now = System.currentTimeMillis()
        if (programme.startAt > now || programme.endAt < now) return false
        if (now - programme.startAt > getMaxTimeshiftDurationMs(channelLine)) return false
        return true
    }

    // ==================== 时移URL生成 ====================

    fun buildTimeshiftUrl(channelLine: ChannelLine, offsetMs: Long, useUtcTime: Boolean = true): String {
        val limitedOffset = offsetMs.coerceAtMost(getMaxTimeshiftDurationMs(channelLine))
        val now = System.currentTimeMillis()
        val startTime = now - limitedOffset
        // 时移使用系统时间，不需要应用 tvgShift
        // tvgShift 只用于修正 EPG 节目时间
        return generatePlaybackUrl(
            channelLine = channelLine,
            startTimeMs = startTime,
            endTimeMs = now,
            useUtcTime = useUtcTime
        )
    }

    fun buildTimeshiftUrls(channelLine: ChannelLine, offsetMs: Long): List<String> {
        return listOf(
            buildTimeshiftUrl(channelLine, offsetMs, useUtcTime = true),
            buildTimeshiftUrl(channelLine, offsetMs, useUtcTime = false),
        ).distinct()
    }

    // ==================== 多格式URL列表 ====================

    fun buildPlaybackUrls(channelLine: ChannelLine, programme: EpgProgramme): List<String> {
        val urls = mutableListOf<String>()
        // 使用 generatePlaybackUrl 生成多种格式的URL（会自动应用时区偏移）
        // 
        // 变更说明：从 UTC 时间切换到本地时间
        // 原因：
        // 1. 大多数国内/本地回放源使用本地时间而非 UTC 时间
        // 2. 避免时区偏移导致的时间显示不一致问题
        // 3. parseTimeString 函数已增强，可智能识别并选择最合理的时区解析结果
        // 
        // 如果某些源确实需要 UTC 时间，可以考虑：
        // - 在 ChannelLine 中增加时区标识属性
        // - 或者让用户通过设置手动调整
        urls.add(generatePlaybackUrl(channelLine, programme, useUtcTime = false))
        urls.add(generatePlaybackUrl(channelLine, programme, useUtcTime = false, offsetSeconds = 30))

        // 额外生成一个直接的 playseek 格式URL作为后备
        val originalUrl = channelLine.playableUrl
        val cleanedUrl = removeExistingPlaybackParams(originalUrl)
        // 使用 Unix 时间戳（秒级），与时区无关
        val shiftedStartTime = applyTimezoneShift(programme.startAt, channelLine.tvgShift)
        val shiftedEndTime = applyTimezoneShift(programme.endAt, channelLine.tvgShift)
        val startTimestamp = (shiftedStartTime / 1000).toString()
        val endTimestamp = (shiftedEndTime / 1000).toString()
        urls.add(if (cleanedUrl.contains("?")) {
            "$cleanedUrl&$PLAYSEEK_PARAM=$startTimestamp-$endTimestamp"
        } else {
            "$cleanedUrl?$PLAYSEEK_PARAM=$startTimestamp-$endTimestamp"
        })

        return urls.distinct()
    }

    // ==================== 时间范围提取 ====================

    /**
     * 从回放URL中提取时间范围（支持多种格式）
     * @param url 回放URL
     * @return 开始时间和结束时间的时间戳（毫秒），如果解析失败返回null
     */
    fun extractPlaybackTimeRange(url: String): Pair<Long, Long>? {
        if (url.isBlank()) return null

        return try {
            // 1. 尝试提取 playseek 格式的时间范围
            REGEX_PLAYSEEK_EXTRACT.find(url)?.let { match ->
                val startTime = parseTimeString(match.groupValues[1])
                val endTime = parseTimeString(match.groupValues[2])
                if (startTime != null && endTime != null) return startTime to endTime
            }

            // 2. 尝试提取 from/to 时间戳格式（Flussonic）
            REGEX_FLUSSONIC.find(url)?.let { match ->
                val startTime = match.groupValues[1].toLongOrNull()?.times(1000)
                val endTime = match.groupValues[2].toLongOrNull()?.times(1000)
                if (startTime != null && endTime != null) return startTime to endTime
            }

            // 3. 尝试提取 utcstart/utcend 格式
            REGEX_UTC_START_END.find(url)?.let { match ->
                val startTimeStr = match.groupValues[1]
                val endTimeStr = match.groupValues[2]
                val startTime = parseTimeString(startTimeStr)
                val endTime = parseTimeString(endTimeStr)
                if (startTime != null && endTime != null) return startTime to endTime
            }

            // 4. 尝试提取 start/end 格式
            REGEX_START_END.find(url)?.let { match ->
                val startTimeStr = match.groupValues[1]
                val endTimeStr = match.groupValues[2]
                val startTime = parseTimeString(startTimeStr)
                val endTime = parseTimeString(endTimeStr)
                if (startTime != null && endTime != null) return startTime to endTime
            }

            // 5. 尝试提取 SHIFT 模式的时间戳（路径格式）
            REGEX_SHIFT_TIMESTAMP.find(url)?.let { match ->
                val startTime = parseTimeString(match.groupValues[1])
                val endTime = parseTimeString(match.groupValues[2])
                if (startTime != null && endTime != null) return startTime to endTime
            }

            // 6. 尝试从 SHIFT 模式的单个时间戳推断范围（如果URL包含占位符替换后的时间戳）
            // 这种情况通常发生在只有开始时间戳，需要根据节目时长计算结束时间
            if (url.contains("{utc}") || url.contains("{start}") || url.contains("{timestamp}")) {
                // URL 仍然包含未替换的占位符，无法提取具体时间
                // 返回 null，让上层使用节目信息计算时间范围
                return null
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析时间字符串，支持多种格式：
     * - 14位格式化时间：yyyyMMddHHmmss
     * - 10位时间戳（秒级 Unix 时间戳）
     * - 13位时间戳（毫秒级 Unix 时间戳）
     * - 其他长度：根据数值大小智能判断
     *
     * @param timeStr 时间字符串
     * @return 时间戳（毫秒），解析失败返回null
     */
    private fun parseTimeString(timeStr: String): Long? {
        // 1. 首先尝试解析为数字
        val value = timeStr.toLongOrNull() ?: return null
        
        // 2. 处理14位格式化时间（长度为14且不以0或1开头的很可能是格式化时间
        if (timeStr.length == 14 && (timeStr.startsWith("2") || timeStr.startsWith("1"))) {
            // 尝试两种时区解析方式，选择更合理的结果
            val localTime = try {
                LOCAL_TIME_FORMAT.parse(timeStr)?.time
            } catch (e: Exception) {
                null
            }
            
            val utcTime = try {
                UTC_TIME_FORMAT.parse(timeStr)?.time
            } catch (e: Exception) {
                null
            }
            
            val now = System.currentTimeMillis()
            
            // 选择更合理的时间（与当前时间相差不超过2天的）
            val candidates = listOfNotNull(localTime, utcTime).filter { time ->
                Math.abs(time - now) < 2 * 24 * 60 * 60 * 1000L // 2天内
            }
            
            if (candidates.isNotEmpty()) {
                // 如果有多个候选，优先选择本地时间
                return localTime ?: utcTime
            }
        }
        
        // 3. 根据数值范围判断是秒还是毫秒
        return convertToMillis(value)
    }
    
    /**
     * 将数值转换为毫秒时间戳
     * 根据数值大小智能判断是秒还是毫秒
     */
    private fun convertToMillis(value: Long): Long {
        return when {
            // 小于 2000年1月1日（毫秒），认为是秒
            value < UNIX_TIMESTAMP_THRESHOLD_MS -> value * 1000
            // 大于 2000年1月1日（毫秒），认为是毫秒
            else -> value
        }
    }

    // ==================== 枚举定义 ====================

    enum class PlaybackType {
        NONE,
        HUAWEI_PLTV,
        STANDARD_TVOD,
        CATCHUP,
        TIMESHIFT,
    }

    enum class PlaybackMode {
        DEFAULT,
        APPEND,
        SHIFT,
        FLUSSONIC,
        XC,
    }

    // ==================== 工具函数 ====================

    fun getPlaybackType(channelLine: ChannelLine): PlaybackType {
        val url = channelLine.url.lowercase()
        return when {
            url.contains("pltv") -> PlaybackType.HUAWEI_PLTV
            url.contains("tvod") -> PlaybackType.STANDARD_TVOD
            url.contains("catchup") -> PlaybackType.CATCHUP
            url.contains("timeshift") -> PlaybackType.TIMESHIFT
            !channelLine.catchup.isNullOrBlank() -> PlaybackType.CATCHUP
            else -> PlaybackType.NONE
        }
    }

    fun calculatePlaybackProgress(currentPosition: Long, programme: EpgProgramme): Float {
        val duration = programme.endAt - programme.startAt
        if (duration <= 0) return 0f
        val position = currentPosition - programme.startAt
        return (position.toFloat() / duration).coerceIn(0f, 1f)
    }

    fun formatPlaybackTime(timeMs: Long): String {
        val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return format.format(timeMs)
    }

    // ==================== M3U属性解析（兼容） ====================

    fun isPlaybackSupportedFromAttributes(attributes: String): Boolean {
        REGEX_CATCHUP_ATTR.find(attributes)?.let { match ->
            val value = match.groupValues[1].lowercase()
            if (value in SUPPORTED_CATCHUP_MODES) return true
        }
        if (REGEX_CATCHUP_DAYS_ATTR.find(attributes) != null) return true
        if (REGEX_TIMESHIFT_ATTR.find(attributes) != null) return true
        if (REGEX_TVOD_ATTR.find(attributes) != null) return true
        return false
    }

    fun getPlaybackTypeFromAttributes(attributes: String): PlaybackMode {
        return when (REGEX_CATCHUP_ATTR.find(attributes)?.groupValues?.get(1)?.lowercase()) {
            "default" -> PlaybackMode.DEFAULT
            "append" -> PlaybackMode.APPEND
            "shift" -> PlaybackMode.SHIFT
            "flussonic" -> PlaybackMode.FLUSSONIC
            "xc" -> PlaybackMode.XC
            else -> PlaybackMode.DEFAULT
        }
    }

    fun getCatchupSourceTemplate(attributes: String): String? {
        return REGEX_CATCHUP_SOURCE_ATTR.find(attributes)?.groupValues?.get(1)
    }

    private fun hasRecognizedCatchupMode(mode: String?): Boolean {
        return mode?.lowercase() in SUPPORTED_CATCHUP_MODES
    }

    private fun hasPlaybackUrlSignature(url: String): Boolean {
        if (url.isBlank()) return false

        val lowerUrl = url.lowercase()
        if (PLAYBACK_URL_PATTERNS.any { lowerUrl.contains(it) }) return true
        if (lowerUrl.contains("playseek=")) return true
        if (Regex("""(?:[?&]|/)timeshift(?:=|/)""", RegexOption.IGNORE_CASE).containsMatchIn(url)) return true
        if (Regex("""[?&]catchup=""", RegexOption.IGNORE_CASE).containsMatchIn(url)) return true
        if (REGEX_FLUSSONIC.containsMatchIn(url)) return true
        if (REGEX_UTC_START_END.containsMatchIn(url)) return true
        if (REGEX_START_END.containsMatchIn(url)) return true
        return false
    }
}
