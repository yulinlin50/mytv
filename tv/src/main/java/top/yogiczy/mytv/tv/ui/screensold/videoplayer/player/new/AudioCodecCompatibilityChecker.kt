package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import android.media.MediaCodecList

object AudioCodecCompatibilityChecker {
    
    private val supportedMimeTypes: Set<String> by lazy {
        try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder }
                .flatMap { runCatching { it.supportedTypes.toList() }.getOrDefault(emptyList()) }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
    
    fun isMimeTypeSupported(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return true
        return supportedMimeTypes.contains(mimeType)
    }
    
    fun getUnsupportedReason(mimeType: String?): String? {
        if (mimeType.isNullOrBlank() || isMimeTypeSupported(mimeType)) return null
        return "设备不支持此格式"
    }
    
    fun getSupportedAudioMimeTypes(): Set<String> =
        supportedMimeTypes.filter { it.startsWith("audio/", ignoreCase = true) }.toSet()
    
    fun codecNameToMimeType(codecName: String?): String? {
        val normalized = codecName?.lowercase()?.substringBefore(".")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            normalized == "mp4a" || normalized == "aac" -> "audio/mp4a-latm"
            normalized.startsWith("ac-3") || normalized == "ac3" || normalized == "dac3" -> "audio/ac3"
            normalized.startsWith("ec-3") || normalized == "eac3" || normalized == "dec3" -> "audio/eac3"
            normalized.startsWith("ac-4") || normalized == "ac4" || normalized == "dac4" -> "audio/ac4"
            normalized == "opus" -> "audio/opus"
            normalized == "vorbis" -> "audio/vorbis"
            normalized == "flac" -> "audio/flac"
            normalized == "alac" -> "audio/alac"
            normalized.startsWith("dts") -> "audio/dts"
            normalized.startsWith("mhm") -> "audio/mpeg-h"
            normalized == "mp3" || normalized == "mpg123" -> "audio/mpeg"
            normalized == "truehd" -> "audio/truehd"
            else -> "audio/$normalized"
        }
    }
}
