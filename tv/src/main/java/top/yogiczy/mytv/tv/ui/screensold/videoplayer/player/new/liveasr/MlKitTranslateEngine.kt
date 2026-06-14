package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.TimeUnit

/**
 * Google ML Kit 离线翻译引擎
 *
 * 免费、无限制、设备端离线翻译，不依赖 Google Play Services
 * 支持 59 种语言之间的互译
 * 首次使用需下载模型（约 30MB/语对），下载时通知栏显示进度
 */
class MlKitTranslateEngine : TranslateEngine {

    private val translators = mutableMapOf<String, com.google.mlkit.nl.translate.Translator>()
    private var targetLang = "zh"
    private var appContext: Context? = null

    private companion object {
        const val NOTIFY_CHANNEL = "model_download" // 复用 ModelManager 的通知渠道
    }

    override suspend fun initialize(context: Context, config: TranslateConfig) {
        targetLang = config.translateTarget
        appContext = context.applicationContext
        LiveAsrLogger.i("MLKit翻译: 初始化, 目标语言=$targetLang")
    }

    override suspend fun translate(text: String, sourceLanguage: String): String {
        if (text.isBlank()) return text

        val sourceLang = mapLanguageCode(sourceLanguage)
        val target = mapLanguageCode(targetLang)
        val key = "${sourceLang}_$target"

        var translator = translators[key]
        if (translator == null) {
            LiveAsrLogger.i("MLKit翻译: 创建翻译器 $sourceLang -> $target")
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(target)
                .build()
            translator = Translation.getClient(options)
            translators[key] = translator

            // 下载模型（带通知栏进度提示）
            downloadModelWithNotification(translator, sourceLang, target)
        }

        return try {
            val result = Tasks.await(translator.translate(text), 10, TimeUnit.SECONDS)
            LiveAsrLogger.d("MLKit翻译: \"$text\" -> \"$result\"")
            result
        } catch (e: Exception) {
            LiveAsrLogger.w("MLKit翻译失败，返回原文", e)
            text // 翻译失败时返回原文
        }
    }

    override suspend fun release() {
        translators.values.forEach { translator ->
            try { translator.close() } catch (_: Exception) {}
        }
        translators.clear()
        appContext = null
    }

    // ==================== 私有方法 ====================

    /**
     * 下载翻译模型，通知栏显示进度
     * ML Kit API 不暴露实际下载百分比，使用 indeterminate 进度
     */
    private fun downloadModelWithNotification(
        translator: com.google.mlkit.nl.translate.Translator,
        sourceLang: String,
        target: String,
    ) {
        val ctx = appContext ?: run {
            Tasks.await(translator.downloadModelIfNeeded(), 120, TimeUnit.SECONDS)
            return
        }

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifyId = "mlkit_${sourceLang}_$target".hashCode()
        val title = "正在下载翻译模型"
        val text = "${langLabel(sourceLang)} → ${langLabel(target)}"

        // 下载中通知（indeterminate）
        nm.notify(notifyId, NotificationCompat.Builder(ctx, NOTIFY_CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
        )

        try {
            val conditions = DownloadConditions.Builder().build() // 不限制网络类型
            Tasks.await(translator.downloadModelIfNeeded(conditions), 120, TimeUnit.SECONDS)

            // 完成通知
            nm.notify(notifyId, NotificationCompat.Builder(ctx, NOTIFY_CHANNEL)
                .setContentTitle("翻译模型已就绪")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .build()
            )
        } catch (e: Exception) {
            nm.notify(notifyId, NotificationCompat.Builder(ctx, NOTIFY_CHANNEL)
                .setContentTitle("翻译模型下载失败")
                .setContentText("${text}: ${e.message}")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .build()
            )
            throw e
        }
    }

    /** 语言代码 → 显示标签 */
    private fun langLabel(code: String): String = when (code) {
        TranslateLanguage.CHINESE -> "中文"
        TranslateLanguage.ENGLISH -> "英语"
        TranslateLanguage.JAPANESE -> "日语"
        TranslateLanguage.KOREAN -> "韩语"
        TranslateLanguage.FRENCH -> "法语"
        TranslateLanguage.GERMAN -> "德语"
        TranslateLanguage.SPANISH -> "西班牙语"
        TranslateLanguage.PORTUGUESE -> "葡萄牙语"
        TranslateLanguage.ITALIAN -> "意大利语"
        TranslateLanguage.RUSSIAN -> "俄语"
        TranslateLanguage.ARABIC -> "阿拉伯语"
        TranslateLanguage.HINDI -> "印地语"
        TranslateLanguage.THAI -> "泰语"
        TranslateLanguage.VIETNAMESE -> "越南语"
        TranslateLanguage.TURKISH -> "土耳其语"
        TranslateLanguage.POLISH -> "波兰语"
        TranslateLanguage.DUTCH -> "荷兰语"
        else -> code
    }

    /** BCP-47 语言代码映射为 ML Kit TranslateLanguage 常量 */
    private fun mapLanguageCode(code: String): String = when (code.lowercase()) {
        "zh", "zh-cn", "zh-hans", "cmn", "chi" -> TranslateLanguage.CHINESE
        "en", "eng" -> TranslateLanguage.ENGLISH
        "ja", "jpn" -> TranslateLanguage.JAPANESE
        "ko", "kor" -> TranslateLanguage.KOREAN
        "fr", "fra", "fre" -> TranslateLanguage.FRENCH
        "de", "deu", "ger" -> TranslateLanguage.GERMAN
        "es", "spa" -> TranslateLanguage.SPANISH
        "pt", "por" -> TranslateLanguage.PORTUGUESE
        "it", "ita" -> TranslateLanguage.ITALIAN
        "ru", "rus" -> TranslateLanguage.RUSSIAN
        "ar", "ara" -> TranslateLanguage.ARABIC
        "hi", "hin" -> TranslateLanguage.HINDI
        "th", "tha" -> TranslateLanguage.THAI
        "vi", "vie" -> TranslateLanguage.VIETNAMESE
        "tr", "tur" -> TranslateLanguage.TURKISH
        "pl", "pol" -> TranslateLanguage.POLISH
        "nl", "nld", "dut" -> TranslateLanguage.DUTCH
        else -> TranslateLanguage.ENGLISH
    }
}