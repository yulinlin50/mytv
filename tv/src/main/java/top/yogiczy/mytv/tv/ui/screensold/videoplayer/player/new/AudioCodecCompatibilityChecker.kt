package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import android.media.MediaCodecList
import android.media.MediaCodecInfo

object AudioCodecCompatibilityChecker {
    
    private val supportedMimeTypes: Set<String> by lazy {
        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            codecList.codecInfos
                .filter { !it.isEncoder }
                .flatMap { codecInfo ->
                    try {
                        codecInfo.supportedTypes.toList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    fun isMimeTypeSupported(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return true
        return supportedMimeTypes.contains(mimeType)
    }
    
    fun getUnsupportedReason(mimeType: String?): String? {
        if (mimeType.isNullOrBlank()) return null
        return if (!isMimeTypeSupported(mimeType)) {
            "设备不支持此格式"
        } else {
            null
        }
    }
    
    fun getSupportedAudioMimeTypes(): Set<String> {
        return supportedMimeTypes.filter { 
            it.startsWith("audio/", ignoreCase = true) 
        }.toSet()
    }
    
    fun codecNameToMimeType(codecName: String?): String? {
        if (codecName.isNullOrBlank()) return null
        val normalized = codecName.lowercase().substringBefore(".").trim()
        return when {
            normalized.isBlank() -> null
            normalized == "mp4a" || normalized == "aac" -> "audio/mp4a-latm"
            normalized == "ac-3" || normalized == "ac3" || normalized == "dac3" -> "audio/ac3"
            normalized == "ec-3" || normalized == "eac3" || normalized == "dec3" -> "audio/eac3"
            normalized == "ac-4" || normalized == "ac4" || normalized == "dac4" -> "audio/ac4"
            normalized == "opus" -> "audio/opus"
            normalized == "vorbis" -> "audio/vorbis"
            normalized == "flac" -> "audio/flac"
            normalized == "alac" -> "audio/alac"
            normalized.startsWith("dtse") || normalized.startsWith("dtsc") || normalized == "dts" -> "audio/dts"
            normalized.startsWith("dtsx") -> "audio/dts"
            normalized.startsWith("mhm1") || normalized.startsWith("mhm2") -> "audio/mpeg-h"
            normalized == "mp3" || normalized == "mpg123" -> "audio/mpeg"
            normalized == "truehd" -> "audio/truehd"
            else -> "audio/$normalized"
        }
    }
}
