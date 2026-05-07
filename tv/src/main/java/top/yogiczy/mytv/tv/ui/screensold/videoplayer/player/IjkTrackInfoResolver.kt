package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import top.yogiczy.mytv.core.util.utils.humanizeAudioChannels
import top.yogiczy.mytv.core.util.utils.humanizeBitrate
import top.yogiczy.mytv.core.util.utils.humanizeLanguage
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.AudioCodecCompatibilityChecker
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.PlayerMetadata
import top.yogiczy.mytv.tv.ui.utils.Configs
import tv.danmaku.ijk.media.player.IjkMediaMeta
import tv.danmaku.ijk.media.player.misc.ITrackInfo
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo

internal object IjkTrackInfoResolver {

    private const val LOCAL_AV3A_LAYOUT_5POINT1POINT4 = 0x2D60FL

    internal data class AudioTrackCandidate(
        val metadata: PlayerMetadata.AudioTrack,
        val streamIndex: Int,
        val matchKeys: Set<String> = emptySet(),
    )

    // ========== 音轨标题过滤规则 ==========
    // 通用无意义标题（完全匹配）
    private val genericAudioTitles = setOf(
        "audio", "audio track", "default", "track", "null",
    )
    
    // 无效值（用于语言和标题过滤）
    private val invalidTrackValues = setOf("und", "unknown", "null")
    
    // 带编号的通用标题模式（如 "Audio 1", "Track 2"）
    private val numberedGenericPattern = Regex(
        """^(audio|track|音轨|声道)\s*\d+$""",
        RegexOption.IGNORE_CASE
    )
    
    // 语言标签正则（ISO 639-1/2 + 可选区域）
    private val languageTagRegex = Regex("^[a-z]{2,3}(?:[-_][a-z0-9]{2,8})*$", RegexOption.IGNORE_CASE)
    
    // 轨道ID清理正则
    private val trackIdSanitizer = Regex("""[\p{Punct}\s]+""")

    internal fun resolveAudioTracks(
        trackInfos: Array<out ITrackInfo>?,
        selectedAudioStreamIndex: Int,
        sortMode: Configs.AudioTrackSortMode = Configs.audioTrackSortMode,
    ): List<AudioTrackCandidate> {
        if (trackInfos == null) return emptyList()

        val candidates = trackInfos
            .filter { it.trackType == ITrackInfo.MEDIA_TRACK_TYPE_AUDIO }
            .mapIndexed { uiIndex, trackInfo ->
                buildAudioTrackCandidate(trackInfo, selectedAudioStreamIndex, uiIndex)
            }

        return dedupeAndSortAudioTracks(candidates, sortMode)
    }

    internal fun buildAudioTrackCandidate(
        trackInfo: ITrackInfo,
        selectedAudioStreamIndex: Int,
        uiIndex: Int,
    ): AudioTrackCandidate {
        val streamMeta = extractStreamMeta(trackInfo)
        val streamIndex = streamMeta?.mIndex ?: uiIndex
        val isSelected = streamIndex == selectedAudioStreamIndex

        val normalizedLanguage = normalizeTrackLanguage(streamMeta?.mLanguage ?: trackInfo.language)
        val trackTitle = selectAudioTitle(streamMeta?.mLanguage)
            ?: normalizedLanguage?.humanizeLanguage()
        val codecLabel = streamMeta?.mCodecName?.toAudioCodecLabel()
        val channels = streamMeta?.mChannelLayout?.let { getChannelCount(it) }
        val channelsLabel = streamMeta?.mChannelLayout?.let { getChannelLabel(it) }
        val sampleRate = streamMeta?.mSampleRate?.takeIf { it > 0 }
        val bitrate = streamMeta?.mBitrate?.takeIf { it > 0 }?.toInt()

        val stableTrackId = buildStableAudioTrackId(
            language = normalizedLanguage,
            title = trackTitle,
            codecLabel = codecLabel,
            channels = channels,
            sampleRate = sampleRate,
            streamIndex = streamIndex,
        )

        val legacyTrackId = buildLegacyAudioTrackId(
            language = normalizedLanguage,
            title = trackTitle,
            codecLabel = codecLabel,
            channels = channels,
            sampleRate = sampleRate,
            bitrate = bitrate,
            streamIndex = streamIndex,
        )

        val audioMimeType = AudioCodecCompatibilityChecker.codecNameToMimeType(streamMeta?.mCodecName)
        val isSupported = AudioCodecCompatibilityChecker.isMimeTypeSupported(audioMimeType)
        val unsupportedReason = AudioCodecCompatibilityChecker.getUnsupportedReason(audioMimeType)

        val metadata = PlayerMetadata.AudioTrack(
            index = uiIndex,
            isSelected = isSelected,
            title = trackTitle,
            codecLabel = codecLabel,
            channels = channels,
            channelsLabel = channelsLabel,
            sampleRate = sampleRate,
            bitrate = bitrate,
            mimeType = streamMeta?.mCodecName,
            language = normalizedLanguage,
            trackId = stableTrackId,
            isSupported = isSupported,
            unsupportedReason = unsupportedReason
        )

        return AudioTrackCandidate(
            metadata = metadata,
            streamIndex = streamIndex,
            matchKeys = setOfNotNull(
                stableTrackId,
                legacyTrackId,
                streamIndex.toString(),
            ).filter { it.isNotBlank() }.toSet(),
        )
    }

    internal fun dedupeAndSortAudioTracks(
        tracks: List<AudioTrackCandidate>,
        sortMode: Configs.AudioTrackSortMode,
    ): List<AudioTrackCandidate> {
        return tracks
            .groupBy { it.metadata.trackId.orEmpty().ifBlank { "audio-${it.metadata.index ?: -1}" } }
            .values
            .map { duplicates ->
                val preferred = duplicates.maxWithOrNull(
                    compareBy<AudioTrackCandidate>(
                        { if (it.metadata.isSelected == true) 1 else 0 },
                        { it.metadata.richnessScore() },
                        { it.metadata.bitrate ?: 0 },
                        { it.metadata.channels ?: 0 },
                        { -(it.metadata.index ?: Int.MAX_VALUE) },
                    )
                ) ?: duplicates.first()

                preferred.copy(
                    matchKeys = duplicates.flatMapTo(linkedSetOf<String>()) { it.matchKeys }
                )
            }
            .sortedWith(audioTrackComparator(sortMode))
    }

    private fun audioTrackComparator(sortMode: Configs.AudioTrackSortMode): Comparator<AudioTrackCandidate> {
        val baseComparator = when (sortMode) {
            Configs.AudioTrackSortMode.CHANNELS -> compareByDescending<AudioTrackCandidate> {
                it.metadata.channels ?: 0
            }.thenByDescending {
                it.metadata.bitrate ?: 0
            }.thenBy {
                it.languageSortKey()
            }.thenBy {
                it.titleSortKey()
            }.thenBy {
                it.metadata.index ?: Int.MAX_VALUE
            }

            Configs.AudioTrackSortMode.BITRATE -> compareByDescending<AudioTrackCandidate> {
                it.metadata.bitrate ?: 0
            }.thenByDescending {
                it.metadata.channels ?: 0
            }.thenBy {
                it.languageSortKey()
            }.thenBy {
                it.titleSortKey()
            }.thenBy {
                it.metadata.index ?: Int.MAX_VALUE
            }

            Configs.AudioTrackSortMode.LANGUAGE -> compareBy<AudioTrackCandidate>(
                { it.languageSortKey() },
                { it.titleSortKey() },
                { it.metadata.codecLabel?.lowercase() ?: "\uFFFF" },
            ).thenByDescending {
                it.metadata.channels ?: 0
            }.thenByDescending {
                it.metadata.bitrate ?: 0
            }.thenBy {
                it.metadata.index ?: Int.MAX_VALUE
            }
        }

        return compareBy<AudioTrackCandidate>(
            { if (it.metadata.isSelected == true) 0 else 1 },
            { it.languagePriorityScore() },
        ).then(baseComparator)
    }

    private fun AudioTrackCandidate.languageSortKey(): String {
        return metadata.language?.humanizeLanguage()?.lowercase()
            ?: metadata.title?.lowercase()
            ?: "\uFFFF"
    }

    private fun AudioTrackCandidate.titleSortKey(): String {
        return metadata.title?.lowercase()
            ?: metadata.codecLabel?.lowercase()
            ?: "\uFFFF"
    }

    /**
     * 语言优先级评分（值越小优先级越高）
     * 中文相关语言优先，其次是英文，其他语言按字母排序
     */
    private fun AudioTrackCandidate.languagePriorityScore(): Int {
        val lang = metadata.language?.lowercase() ?: return 100
        
        return when {
            lang.startsWith("zh") -> 0
            lang == "chi" -> 0
            lang == "cmn" -> 0
            lang == "en" -> 1
            lang == "eng" -> 1
            lang == "und" -> 99
            else -> 50
        }
    }

    private fun PlayerMetadata.AudioTrack.richnessScore(): Int {
        var score = 0
        if (!title.isNullOrBlank()) score += 4
        if (!language.isNullOrBlank()) score += 3
        if (!codecLabel.isNullOrBlank()) score += 2
        if ((channels ?: 0) > 0) score += 1
        if ((bitrate ?: 0) > 0) score += 1
        return score
    }

    private fun extractStreamMeta(trackInfo: ITrackInfo): IjkMediaMeta.IjkStreamMeta? {
        return if (trackInfo is IjkTrackInfo) {
            try {
                val field = IjkTrackInfo::class.java.getDeclaredField("mStreamMeta")
                field.isAccessible = true
                field.get(trackInfo) as? IjkMediaMeta.IjkStreamMeta
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    private fun selectAudioTitle(vararg candidates: String?): String? {
        return candidates
            .mapNotNull(::sanitizeAudioTitle)
            .distinctBy { it.lowercase() }
            .firstOrNull()
    }

    /**
     * 音轨标题清理和过滤
     *
     * 过滤规则：
     * 1. 空值或纯空格
     * 2. 通用无意义标题（如"audio", "track"）
     * 3. 无效值（如"und", "unknown"）
     * 4. 纯语言代码（如 "zh", "en"），但保留带区域信息的标签（如"zh-CN"）
     * 5. 带编号的通用标题（如 "Audio 1", "Track 2"）
     */
    private fun sanitizeAudioTitle(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        
        // 过滤通用无意义标题
        if (trimmed.lowercase() in genericAudioTitles) return null
        
        // 过滤无效值
        if (trimmed.lowercase() in invalidTrackValues) return null
        
        // 过滤带编号的通用标题（如 "Audio 1", "Track 2"）
        if (numberedGenericPattern.matches(trimmed)) return null
        
        // 过滤纯语言代码（长度<= 3），保留带区域信息的标签
        if (languageTagRegex.matches(trimmed) && trimmed.length <= 3) return null
        
        return trimmed
    }

    private fun normalizeTrackLanguage(language: String?): String? {
        val normalized = language?.trim()?.replace('_', '-')?.takeIf { it.isNotEmpty() } ?: return null
        return normalized.takeUnless { it.lowercase() in invalidTrackValues }
    }

    private fun buildStableAudioTrackId(
        language: String?,
        title: String?,
        codecLabel: String?,
        channels: Int?,
        sampleRate: Int?,
        streamIndex: Int,
    ): String {
        val hasDistinctInfo = !language.isNullOrBlank() || !title.isNullOrBlank()
        
        val parts = if (hasDistinctInfo) {
            listOfNotNull(
                "audio",
                language?.toTrackIdPart(),
                title?.toTrackIdPart(),
                codecLabel?.toTrackIdPart(),
                channels?.takeIf { it > 0 }?.toString(),
            )
        } else {
            listOfNotNull(
                "audio",
                codecLabel?.toTrackIdPart(),
                channels?.takeIf { it > 0 }?.toString(),
                "stream$streamIndex",
            )
        }
        return parts.joinToString("-").ifBlank { "audio-stream$streamIndex" }
    }

    private fun buildLegacyAudioTrackId(
        language: String?,
        title: String?,
        codecLabel: String?,
        channels: Int?,
        sampleRate: Int?,
        bitrate: Int?,
        streamIndex: Int?,
    ): String? {
        return listOfNotNull(
            language,
            title,
            codecLabel,
            channels?.takeIf { it > 0 }?.toString(),
            sampleRate?.takeIf { it > 0 }?.toString(),
            bitrate?.takeIf { it > 0 }?.toString(),
            streamIndex?.toString(),
        ).joinToString("-").takeIf { it.isNotBlank() }
    }

    private fun String?.toTrackIdPart(): String? {
        return this
            ?.trim()
            ?.lowercase()
            ?.replace(trackIdSanitizer, "_")
            ?.trim('_')
            ?.takeIf { it.isNotEmpty() }
    }

    private fun String?.toAudioCodecLabel(): String? {
        val normalized = this?.lowercase()?.substringBefore(".")?.trim()
        return when {
            normalized.isNullOrBlank() -> null
            normalized == "mp4a" || normalized == "aac" -> "AAC"
            normalized == "ac-3" || normalized == "ac3" || normalized == "dac3" -> "AC3"
            normalized == "ec-3" || normalized == "eac3" || normalized == "dec3" -> "E-AC3"
            normalized == "ac-4" || normalized == "ac4" || normalized == "dac4" -> "AC4"
            normalized == "opus" -> "Opus"
            normalized == "vorbis" -> "Vorbis"
            normalized == "flac" -> "FLAC"
            normalized == "alac" -> "ALAC"
            normalized.startsWith("dtse") || normalized.startsWith("dtsc") || normalized == "dts" -> "DTS"
            normalized.startsWith("dtsx") -> "DTS:X"
            normalized.startsWith("mhm1") || normalized.startsWith("mhm2") -> "MPEG-H"
            normalized == "mp3" || normalized == "mpg123" -> "MP3"
            normalized == "truehd" -> "TrueHD"
            else -> {
                val upper = this.uppercase()
                if (upper.all { it.code <= 0x7F }) upper else null
            }
        }
    }

    internal fun getChannelCount(channelLayout: Long): Int? {
        if (channelLayout <= 0) return null
        return when (channelLayout) {
            IjkMediaMeta.AV_CH_LAYOUT_MONO -> 1
            IjkMediaMeta.AV_CH_LAYOUT_STEREO,
            IjkMediaMeta.AV_CH_LAYOUT_2POINT1,
            IjkMediaMeta.AV_CH_LAYOUT_STEREO_DOWNMIX -> 2

            IjkMediaMeta.AV_CH_LAYOUT_2_1,
            IjkMediaMeta.AV_CH_LAYOUT_SURROUND -> 3

            IjkMediaMeta.AV_CH_LAYOUT_3POINT1,
            IjkMediaMeta.AV_CH_LAYOUT_4POINT0,
            IjkMediaMeta.AV_CH_LAYOUT_2_2,
            IjkMediaMeta.AV_CH_LAYOUT_QUAD -> 4

            IjkMediaMeta.AV_CH_LAYOUT_4POINT1,
            IjkMediaMeta.AV_CH_LAYOUT_5POINT0 -> 5

            IjkMediaMeta.AV_CH_LAYOUT_HEXAGONAL,
            IjkMediaMeta.AV_CH_LAYOUT_5POINT1,
            IjkMediaMeta.AV_CH_LAYOUT_6POINT0 -> 6

            IjkMediaMeta.AV_CH_LAYOUT_6POINT1,
            IjkMediaMeta.AV_CH_LAYOUT_7POINT0 -> 7

            IjkMediaMeta.AV_CH_LAYOUT_7POINT1,
            IjkMediaMeta.AV_CH_LAYOUT_7POINT1_WIDE,
            IjkMediaMeta.AV_CH_LAYOUT_7POINT1_WIDE_BACK,
            IjkMediaMeta.AV_CH_LAYOUT_OCTAGONAL -> 8

            LOCAL_AV3A_LAYOUT_5POINT1POINT4 -> 10

            else -> java.lang.Long.bitCount(channelLayout).takeIf { it > 0 }
        }
    }

    internal fun getChannelLabel(channelLayout: Long): String? {
        if (channelLayout <= 0) return null
        return when (channelLayout) {
            IjkMediaMeta.AV_CH_LAYOUT_MONO -> "单声道"
            IjkMediaMeta.AV_CH_LAYOUT_STEREO -> "立体声"
            IjkMediaMeta.AV_CH_LAYOUT_2POINT1 -> "2.1 声道"
            IjkMediaMeta.AV_CH_LAYOUT_2_1 -> "立体声"
            IjkMediaMeta.AV_CH_LAYOUT_SURROUND -> "环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_3POINT1 -> "3.1 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_4POINT0 -> "4.0 四声道"
            IjkMediaMeta.AV_CH_LAYOUT_4POINT1 -> "4.1 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_2_2 -> "四声道"
            IjkMediaMeta.AV_CH_LAYOUT_QUAD -> "四声道"
            IjkMediaMeta.AV_CH_LAYOUT_5POINT0 -> "5.0 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_5POINT1 -> "5.1 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_6POINT0 -> "6.0 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_6POINT1 -> "6.1 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_7POINT0 -> "7.0 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_7POINT1 -> "7.1 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_7POINT1_WIDE -> "宽域 7.1 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_7POINT1_WIDE_BACK -> "后置 7.1 环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_HEXAGONAL -> "六角环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_OCTAGONAL -> "八角环绕声"
            IjkMediaMeta.AV_CH_LAYOUT_STEREO_DOWNMIX -> "立体声下混音"
            LOCAL_AV3A_LAYOUT_5POINT1POINT4 -> "5.1.4 全景声"
            else -> null
        }
    }
}
