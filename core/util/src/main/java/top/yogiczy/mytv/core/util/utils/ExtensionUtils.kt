package top.yogiczy.mytv.core.util.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.max

fun Long.humanizeMs(): String {
    if (this < 0) return "0秒"
    
    return when {
        this < 60_000 -> "${this / 1000}秒"
        this < 3_600_000 -> "${this / 60_000}分钟"
        this < 86_400_000 -> "${this / 3_600_000}小时"
        else -> "${this / 86_400_000}天"
    }
}

fun Long.humanizeBytes(): String {
    if (this < 0) return "0B"
    
    return when {
        this < 1024 -> "${this}B"
        this < 1024 * 1024 -> String.format("%.1fKB", this / 1024.0)
        this < 1024 * 1024 * 1024 -> String.format("%.1fMB", this / (1024.0 * 1024))
        this < 1024L * 1024 * 1024 * 1024 -> String.format("%.1fGB", this / (1024.0 * 1024 * 1024))
        else -> String.format("%.1fTB", this / (1024.0 * 1024 * 1024 * 1024))
    }
}

fun String.isIPv6(): Boolean {
    val urlPattern = Pattern.compile(
        "^((http|https)://)?(\\[[0-9a-fA-F:]+])(:[0-9]+)?(/.*)?$"
    )
    return urlPattern.matcher(this).matches()
}

fun String.compareVersion(version2: String): Int {
    return try {
        val parts1 = this.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = version2.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLen = maxOf(parts1.size, parts2.size)
        
        for (i in 0 until maxLen) {
            val v1 = parts1.getOrElse(i) { 0 }
            val v2 = parts2.getOrElse(i) { 0 }
            
            if (v1 != v2) return v1.compareTo(v2)
        }
        
        0
    } catch (e: Exception) {
        Log.w("ExtensionUtils", "Invalid version comparison: $this vs $version2")
        0
    }
}

fun String.urlHost(): String {
    return this.split("://").getOrElse(1) { "" }.split("/").firstOrNull() ?: this
}

fun Context.actionView(url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    }
}

fun String.toHeaders(): Map<String, String> {
    return runCatching {
        lines().associate {
            val (key, value) = it.split(":", limit = 2)
            key.trim() to value.trim()
        }
    }.getOrElse { emptyMap() }
}

fun String.headersValid(): Boolean {
    if (isBlank()) return true

    return lines().all { line ->
        val parts = line.split(":", limit = 2)
        parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()
    }
}

fun Int.humanizeBitrate(base: Int = 1000): String {
    if (this < 0) return "0bps"
    
    return when {
        this < base -> "${this}bps"
        this < base * base -> String.format("%.1fKbps", this / base.toDouble())
        this < base * base * base -> String.format("%.1fMbps", this / (base.toDouble() * base))
        else -> String.format("%.1fGbps", this / (base.toDouble() * base * base))
    }
}

fun Int.humanizeAudioChannels(): String {
    return when (this) {
        1 -> "单声道"
        2 -> "立体声"
        3 -> "2.1 声道"
        4 -> "4.0 四声道"
        5 -> "5.0 环绕声"
        6 -> "5.1 环绕声"
        7 -> "6.1 环绕声"
        8 -> "7.1 环绕声"
        10 -> "7.1.2 杜比全景声"
        12 -> "7.1.4 杜比全景声"
        else -> "${this}声道"
    }
}

fun String.humanizeLanguage(): String {
    val normalized = trim().replace('_', '-').lowercase()
    if (normalized.isEmpty()) return this

    val subtags = normalized.split('-').filter { it.isNotBlank() }
    val primary = subtags.firstOrNull() ?: return this
    val script = subtags.firstOrNull { it.length == 4 }
    val region = subtags.firstOrNull { it.length == 2 || it.length == 3 }

    if (primary == "yue" || primary == "cantonese" || normalized.contains("yue")) {
        return "粤语"
    }

    return when (primary) {
        "zh", "zho", "chi", "cmn", "chs", "cht", "tra" -> when {
            primary == "chs" || script == "hans" || region in setOf("cn", "sg") -> "简体中文"
            primary == "cht" || primary == "tra" || script == "hant" || region in setOf("tw", "hk", "mo") -> "繁体中文"
            else -> "中文"
        }
        "en", "eng" -> "英语"
        "ja", "jpn" -> "日语"
        "ko", "kor" -> "韩语"
        "es", "spa" -> "西班牙语"
        "fr", "fra", "fre" -> "法语"
        "de", "deu", "ger" -> "德语"
        "ru", "rus" -> "俄语"
        "pt", "por" -> "葡萄牙语"
        "it", "ita" -> "意大利语"
        "th", "tha" -> "泰语"
        "vi", "vie" -> "越南语"
        "ar", "ara" -> "阿拉伯语"
        else -> if (all { it.code <= 0x7F }) this else ""
    }
}

fun String.ensureSuffix(suffix: String): String {
    return if (endsWith(suffix)) this else "$this$suffix"
}
