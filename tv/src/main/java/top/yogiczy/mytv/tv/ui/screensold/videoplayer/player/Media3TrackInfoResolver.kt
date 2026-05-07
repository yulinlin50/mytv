package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.hls.HlsTrackMetadataEntry
import top.yogiczy.mytv.core.util.utils.humanizeLanguage
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.AudioCodecCompatibilityChecker
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.PlayerMetadata
import top.yogiczy.mytv.tv.ui.utils.Configs

@OptIn(UnstableApi::class)
internal object Media3TrackInfoResolver {

    internal data class AudioTrackCandidate(
        val metadata: PlayerMetadata.AudioTrack,
        val matchKeys: Set<String> = emptySet(),
    )

    private data class HlsAudioInfo(
        val groupId: String? = null,
        val name: String? = null,
        val audioGroupId: String? = null,
        val averageBitrate: Int? = null,
        val peakBitrate: Int? = null,
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

    internal fun resolveTrackType(group: Tracks.Group): @C.TrackType Int {
        if (group.mediaTrackGroup.type != C.TRACK_TYPE_UNKNOWN) return group.mediaTrackGroup.type

        var hasText = false
        var hasAudio = false
        var hasVideo = false

        for (trackIndex in 0 until group.mediaTrackGroup.length) {
            when (resolveTrackType(group.mediaTrackGroup.getFormat(trackIndex))) {
                C.TRACK_TYPE_TEXT -> hasText = true
                C.TRACK_TYPE_AUDIO -> hasAudio = true
                C.TRACK_TYPE_VIDEO -> hasVideo = true
            }
        }

        return when {
            hasVideo -> C.TRACK_TYPE_VIDEO
            hasAudio -> C.TRACK_TYPE_AUDIO
            hasText -> C.TRACK_TYPE_TEXT
            else -> C.TRACK_TYPE_UNKNOWN
        }
    }

    internal fun resolveTrackType(format: Format): @C.TrackType Int {
        format.sampleMimeType?.let { mimeType ->
            MimeTypes.getTrackType(mimeType)
                .takeIf { it != C.TRACK_TYPE_UNKNOWN }
                ?.let { return it }
        }

        format.containerMimeType?.let { mimeType ->
            MimeTypes.getTrackType(mimeType)
                .takeIf { it != C.TRACK_TYPE_UNKNOWN }
                ?.let { return it }
        }

        if (format.roleFlags and C.ROLE_FLAG_SUBTITLE != 0 || format.roleFlags and C.ROLE_FLAG_CAPTION != 0) {
            return C.TRACK_TYPE_TEXT
        }

        val codecs = format.codecs
        val hasVideoCodec = !codecs.isNullOrBlank() && Util.getCodecsOfType(codecs, C.TRACK_TYPE_VIDEO) != null
        val hasAudioCodec = !codecs.isNullOrBlank() && Util.getCodecsOfType(codecs, C.TRACK_TYPE_AUDIO) != null
        val hasTextCodec = !codecs.isNullOrBlank() && Util.getCodecsOfType(codecs, C.TRACK_TYPE_TEXT) != null

        val hasVideoProperties = format.width > 0 || format.height > 0 || format.frameRate > 0f
        val hasAudioProperties = format.channelCount > 0 || format.sampleRate > 0 || normalizeTrackLanguage(format.language) != null

        if (hasTextCodec && !hasVideoCodec && !hasAudioCodec) return C.TRACK_TYPE_TEXT

        if (hasVideoCodec && hasAudioCodec) {
            return when {
                hasVideoProperties -> C.TRACK_TYPE_VIDEO
                hasAudioProperties -> C.TRACK_TYPE_AUDIO
                else -> C.TRACK_TYPE_VIDEO
            }
        }

        if (hasVideoCodec) return C.TRACK_TYPE_VIDEO
        if (hasAudioCodec) return C.TRACK_TYPE_AUDIO
        if (hasTextCodec) return C.TRACK_TYPE_TEXT
        if (hasAudioProperties) return C.TRACK_TYPE_AUDIO
        if (hasVideoProperties) return C.TRACK_TYPE_VIDEO

        return C.TRACK_TYPE_UNKNOWN
    }

    internal fun buildAudioTrackCandidate(
        format: Format,
        audio: PlayerMetadata.AudioTrack? = null,
    ): AudioTrackCandidate {
        val hlsInfo = format.metadata.toHlsAudioInfo()
        val normalizedLanguage = normalizeTrackLanguage(format.language) ?: audio?.language
        val trackTitle = selectAudioTitle(format.label, hlsInfo.name) ?: audio?.title
        val groupId = hlsInfo.groupId?.takeIfMeaningful()
        val audioGroupId = hlsInfo.audioGroupId?.takeIfMeaningful()
        val bitrate = listOfNotNull(
            format.bitrate.takeIfPositive(),
            format.averageBitrate.takeIfPositive(),
            format.peakBitrate.takeIfPositive(),
            hlsInfo.averageBitrate,
            hlsInfo.peakBitrate,
            audio?.bitrate?.takeIfPositive(),
        ).firstOrNull()
        val codecLabel = format.codecs.toAudioCodecLabel(format.sampleMimeType ?: format.containerMimeType)
            ?: audio?.codecLabel

        val stableTrackId = buildStableAudioTrackId(
            mimeType = format.sampleMimeType ?: format.containerMimeType,
            language = normalizedLanguage,
            title = trackTitle,
            groupId = groupId ?: audioGroupId,
            codecLabel = codecLabel,
            channels = format.channelCount.takeIfPositive() ?: audio?.channels,
            roleFlags = format.roleFlags,
        )
        val legacyTrackId = buildLegacyAudioTrackId(
            mimeType = format.sampleMimeType ?: format.containerMimeType,
            language = normalizedLanguage,
            title = trackTitle,
            groupId = groupId,
            channels = format.channelCount.takeIfPositive() ?: audio?.channels,
            sampleRate = format.sampleRate.takeIfPositive() ?: audio?.sampleRate,
            codecs = format.codecs,
            selectionFlags = format.selectionFlags,
            roleFlags = format.roleFlags,
            bitrate = bitrate,
        )

        val audioMimeType = format.sampleMimeType ?: format.containerMimeType ?: audio?.mimeType
        val isSupported = AudioCodecCompatibilityChecker.isMimeTypeSupported(audioMimeType)
        val unsupportedReason = AudioCodecCompatibilityChecker.getUnsupportedReason(audioMimeType)
        
        val metadata = (audio ?: PlayerMetadata.AudioTrack()).copy(
            title = trackTitle,
            roleLabel = listOfNotNull(
                format.roleFlags.toAudioRoleLabel(),
                format.selectionFlags.toSelectionLabel(),
                groupId?.let { "组$it" },
                audioGroupId?.takeUnless { it == groupId }?.let { "音频组$it" },
            ).distinct().joinToString("/").takeIf { it.isNotBlank() } ?: audio?.roleLabel,
            codecLabel = codecLabel,
            channels = format.channelCount.takeIfPositive() ?: audio?.channels,
            sampleRate = format.sampleRate.takeIfPositive() ?: audio?.sampleRate,
            bitrate = bitrate,
            mimeType = audioMimeType,
            language = normalizedLanguage,
            trackId = stableTrackId,
            isSupported = isSupported,
            unsupportedReason = unsupportedReason
        )

        return AudioTrackCandidate(
            metadata = metadata,
            matchKeys = listOfNotNull(
                metadata.trackId,
                legacyTrackId,
                format.id?.trim()?.takeIf { it.isNotEmpty() },
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
                { it.metadata.roleLabel?.lowercase() ?: "\uFFFF" },
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
        if (!roleLabel.isNullOrBlank()) score += 2
        if (!codecLabel.isNullOrBlank()) score += 2
        if ((channels ?: 0) > 0) score += 1
        if ((bitrate ?: 0) > 0) score += 1
        return score
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
        mimeType: String?,
        language: String?,
        title: String?,
        groupId: String?,
        codecLabel: String?,
        channels: Int?,
        roleFlags: Int,
    ): String {
        val parts = listOfNotNull(
            "audio",
            mimeType.toTrackIdPart(),
            language.toTrackIdPart(),
            title.toTrackIdPart(),
            groupId.toTrackIdPart(),
            codecLabel.toTrackIdPart(),
            channels?.takeIf { it > 0 }?.toString(),
            roleFlags.takeIf { it != 0 }?.toString(),
        )
        return parts.joinToString("-").ifBlank { "audio-unknown" }
    }

    private fun buildLegacyAudioTrackId(
        mimeType: String?,
        language: String?,
        title: String?,
        groupId: String?,
        channels: Int?,
        sampleRate: Int?,
        codecs: String?,
        selectionFlags: Int,
        roleFlags: Int,
        bitrate: Int?,
    ): String? {
        return listOfNotNull(
            mimeType,
            language,
            title,
            groupId,
            channels?.takeIf { it > 0 }?.toString(),
            sampleRate?.takeIf { it > 0 }?.toString(),
            codecs?.takeIfMeaningful(),
            selectionFlags.takeIf { it != 0 }?.toString(),
            roleFlags.takeIf { it != 0 }?.toString(),
            bitrate?.takeIf { it > 0 }?.toString(),
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

    private fun String?.takeIfMeaningful(): String? {
        val trimmed = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return trimmed.takeUnless { it.lowercase() in invalidTrackValues }
    }

    private fun Int.takeIfPositive(): Int? {
        return takeIf { it > 0 }
    }

    private fun androidx.media3.common.Metadata?.toHlsAudioInfo(): HlsAudioInfo {
        if (this == null) return HlsAudioInfo()

        var groupId: String? = null
        var name: String? = null
        var audioGroupId: String? = null
        var averageBitrate: Int? = null
        var peakBitrate: Int? = null

        for (index in 0 until length()) {
            val entry = get(index)
            if (entry !is HlsTrackMetadataEntry) continue

            if (groupId == null) groupId = entry.groupId?.takeIfMeaningful()
            if (name == null) name = entry.name?.takeIfMeaningful()

            entry.variantInfos.forEach { variantInfo ->
                if (audioGroupId == null) audioGroupId = variantInfo.audioGroupId?.takeIfMeaningful()
                if (averageBitrate == null) averageBitrate = variantInfo.averageBitrate.takeIfPositive()
                if (peakBitrate == null) peakBitrate = variantInfo.peakBitrate.takeIfPositive()
            }
        }

        return HlsAudioInfo(
            groupId = groupId,
            name = name,
            audioGroupId = audioGroupId,
            averageBitrate = averageBitrate,
            peakBitrate = peakBitrate,
        )
    }

    private fun String?.toAudioCodecLabel(sampleMimeType: String?): String? {
        val normalized = this?.lowercase()?.substringBefore(".")?.trim()
        return when {
            normalized.isNullOrBlank() -> sampleMimeType.toAudioMimeTypeLabel()
            normalized == "mp4a" -> "AAC"
            normalized == "ac-3" || normalized == "dac3" -> "AC3"
            normalized == "ec-3" || normalized == "dec3" -> "E-AC3"
            normalized == "ac-4" || normalized == "dac4" -> "AC4"
            normalized == "opus" -> "Opus"
            normalized == "vorbis" -> "Vorbis"
            normalized == "flac" -> "FLAC"
            normalized == "alac" -> "ALAC"
            normalized.startsWith("dtse") || normalized.startsWith("dtsc") -> "DTS"
            normalized.startsWith("dtsx") -> "DTS:X"
            normalized.startsWith("mhm1") || normalized.startsWith("mhm2") -> "MPEG-H"
            else -> this ?: sampleMimeType.toAudioMimeTypeLabel()
        }
    }

    private fun String?.toAudioMimeTypeLabel(): String? {
        return when (this) {
            MimeTypes.AUDIO_AAC -> "AAC"
            MimeTypes.AUDIO_MPEG_L2,
            MimeTypes.AUDIO_MPEG_L1,
            MimeTypes.AUDIO_MPEG -> "MPEG"

            MimeTypes.AUDIO_AC3 -> "AC3"
            MimeTypes.AUDIO_E_AC3 -> "E-AC3"
            MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Atmos"
            MimeTypes.AUDIO_AC4 -> "AC4"
            MimeTypes.AUDIO_DTS,
            MimeTypes.AUDIO_DTS_EXPRESS,
            MimeTypes.AUDIO_DTS_HD -> "DTS"

            MimeTypes.AUDIO_TRUEHD -> "TrueHD"
            MimeTypes.AUDIO_MPEGH_MHA1,
            MimeTypes.AUDIO_MPEGH_MHM1 -> "MPEG-H"
            MimeTypes.AUDIO_OPUS -> "Opus"
            MimeTypes.AUDIO_VORBIS -> "Vorbis"
            MimeTypes.AUDIO_FLAC -> "FLAC"
            MimeTypes.AUDIO_ALAC -> "ALAC"
            MimeTypes.AUDIO_RAW -> "PCM"
            else -> this?.substringAfter("/")
        }
    }

    private fun Int.toAudioRoleLabel(): String? {
        val labels = buildList {
            if (this@toAudioRoleLabel and C.ROLE_FLAG_MAIN != 0) add("主音轨")
            if (this@toAudioRoleLabel and C.ROLE_FLAG_ALTERNATE != 0) add("备用")
            if (this@toAudioRoleLabel and C.ROLE_FLAG_SUPPLEMENTARY != 0) add("补充")
            if (this@toAudioRoleLabel and C.ROLE_FLAG_COMMENTARY != 0) add("解说")
            if (this@toAudioRoleLabel and C.ROLE_FLAG_DUB != 0) add("配音")
            if (this@toAudioRoleLabel and C.ROLE_FLAG_DESCRIBES_VIDEO != 0) add("音频描述")
            if (this@toAudioRoleLabel and C.ROLE_FLAG_EASY_TO_READ != 0) add("易读")
            if (this@toAudioRoleLabel and C.ROLE_FLAG_EMERGENCY != 0) add("应急")
        }

        return labels.distinct().takeIf { it.isNotEmpty() }?.joinToString("/")
    }

    private fun Int.toSelectionLabel(): String? {
        val labels = buildList {
            if (this@toSelectionLabel and C.SELECTION_FLAG_DEFAULT != 0) add("默认")
            if (this@toSelectionLabel and C.SELECTION_FLAG_AUTOSELECT != 0) add("自动")
            if (this@toSelectionLabel and C.SELECTION_FLAG_FORCED != 0) add("强制")
        }

        return labels.distinct().takeIf { it.isNotEmpty() }?.joinToString("/")
    }
}
