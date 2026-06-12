package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import top.yogiczy.mytv.core.util.utils.humanizeLanguage
import top.yogiczy.mytv.tv.ui.utils.Configs

internal data class AudioTrackCandidate(
    val metadata: PlayerMetadata.AudioTrack,
    val streamIndex: Int = 0,
    val matchKeys: Set<String> = emptySet(),
)

internal object AudioTrackResolverCommon {

    private val genericAudioTitles = setOf(
        "audio", "audio track", "default", "track", "null",
    )

    private val invalidTrackValues = setOf("und", "unknown", "null")

    private val numberedGenericPattern = Regex(
        """^(audio|track|音轨|声道)\s*\d+$""",
        RegexOption.IGNORE_CASE
    )

    private val languageTagRegex = Regex("^[a-z]{2,3}(?:[-_][a-z0-9]{2,8})*$", RegexOption.IGNORE_CASE)

    private val trackIdSanitizer = Regex("""[\p{Punct}\s]+""")

    fun sanitizeAudioTitle(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.lowercase() in genericAudioTitles) return null
        if (trimmed.lowercase() in invalidTrackValues) return null
        if (numberedGenericPattern.matches(trimmed)) return null
        if (languageTagRegex.matches(trimmed) && trimmed.length <= 3) return null
        return trimmed
    }

    fun selectAudioTitle(vararg candidates: String?): String? {
        return candidates
            .mapNotNull(::sanitizeAudioTitle)
            .distinctBy { it.lowercase() }
            .firstOrNull()
    }

    fun normalizeTrackLanguage(language: String?): String? {
        val normalized = language?.trim()?.replace('_', '-')?.takeIf { it.isNotEmpty() } ?: return null
        return normalized.takeUnless { it.lowercase() in invalidTrackValues }
    }

    fun String?.toTrackIdPart(): String? {
        return this
            ?.trim()
            ?.lowercase()
            ?.replace(trackIdSanitizer, "_")
            ?.trim('_')
            ?.takeIf { it.isNotEmpty() }
    }

    fun buildAudioTrackId(
        language: String?,
        title: String?,
        codecLabel: String?,
        channels: Int?,
        streamIndex: Int = 0,
        mimeType: String? = null,
        groupId: String? = null,
        roleFlags: Int = 0,
        sampleRate: Int? = null,
        bitrate: Int? = null,
        codecs: String? = null,
        selectionFlags: Int = 0,
    ): String {
        val hasDistinctInfo = !language.isNullOrBlank() || !title.isNullOrBlank()
        val parts = if (hasDistinctInfo) {
            listOfNotNull(
                "audio",
                language.toTrackIdPart(),
                title.toTrackIdPart(),
                codecLabel.toTrackIdPart(),
                channels?.takeIf { it > 0 }?.toString(),
                sampleRate?.takeIf { it > 0 }?.toString(),
                bitrate?.takeIf { it > 0 }?.toString(),
            )
        } else {
            listOfNotNull(
                "audio",
                mimeType.toTrackIdPart(),
                codecLabel.toTrackIdPart(),
                channels?.takeIf { it > 0 }?.toString(),
                groupId.toTrackIdPart(),
                "stream$streamIndex",
            )
        }
        return parts.joinToString("-").ifBlank { "audio-stream$streamIndex" }
    }

    fun dedupeAndSortAudioTracks(
        tracks: List<AudioTrackCandidate>,
        sortMode: Configs.AudioTrackSortMode,
    ): List<AudioTrackCandidate> {
        return tracks
            .groupBy { it.metadata.trackId.orEmpty().ifBlank { "audio-${it.metadata.index ?: -1}" } }
            .values
            .map { duplicates ->
                duplicates.maxWithOrNull(
                    compareBy<AudioTrackCandidate>(
                        { if (it.metadata.isSelected == true) 1 else 0 },
                        { it.metadata.bitrate ?: 0 },
                        { it.metadata.channels ?: 0 },
                    )
                )?.copy(
                    matchKeys = duplicates.flatMapTo(linkedSetOf<String>()) { it.matchKeys }
                ) ?: duplicates.first()
            }
            .sortedWith(audioTrackComparator(sortMode))
    }

    private fun audioTrackComparator(sortMode: Configs.AudioTrackSortMode): Comparator<AudioTrackCandidate> {
        val baseComparator: Comparator<AudioTrackCandidate> = when (sortMode) {
            Configs.AudioTrackSortMode.CHANNELS -> compareByDescending<AudioTrackCandidate> { it.metadata.channels ?: 0 }
                .thenByDescending { it.metadata.bitrate ?: 0 }.thenBy { it.metadata.index ?: Int.MAX_VALUE }

            Configs.AudioTrackSortMode.BITRATE -> compareByDescending<AudioTrackCandidate> { it.metadata.bitrate ?: 0 }
                .thenByDescending { it.metadata.channels ?: 0 }.thenBy { it.metadata.index ?: Int.MAX_VALUE }

            Configs.AudioTrackSortMode.LANGUAGE -> compareBy<AudioTrackCandidate>(
                { it.languageSortKey() },
                { it.metadata.codecLabel?.lowercase() ?: "\uFFFF" },
            ).thenByDescending { it.metadata.channels ?: 0 }
                .thenByDescending { it.metadata.bitrate ?: 0 }
                .thenBy { it.metadata.index ?: Int.MAX_VALUE }
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

    private fun AudioTrackCandidate.languagePriorityScore(): Int {
        val lang = metadata.language?.lowercase() ?: return 100
        return when {
            lang.startsWith("zh") || lang == "chi" || lang == "cmn" -> 0
            lang == "en" || lang == "eng" -> 1
            lang == "und" -> 99
            else -> 50
        }
    }

    fun String?.toAudioCodecLabel(): String? {
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

    fun Int.takeIfPositive(): Int? = takeIf { it > 0 }
}
