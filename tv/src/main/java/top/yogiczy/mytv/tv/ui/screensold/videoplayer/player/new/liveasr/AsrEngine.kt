package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context

/**
 * 语音识别引擎配置
 */
data class AsrConfig(
    val provider: String,         // "vosk" / "whisper" / "azure" / "baidu" / "google"
    val apiKey: String,           // 云端 API Key，为空时回退离线
    val apiRegion: String,        // Azure 区域
    val whisperModel: String,     // Whisper 模型大小 "tiny" / "base"
)

/**
 * 翻译引擎配置（与 AsrConfig 独立）
 */
data class TranslateConfig(
    val provider: String,         // "mlkit" / "google" / "azure" / "baidu" / "deepl"
    val apiKey: String,           // 云端 API Key，为空时回退 ML Kit
    val apiRegion: String,        // Azure Translator 区域
    val translateTarget: String,  // 翻译目标语言
)

/**
 * 语音识别引擎接口
 */
interface AsrEngine {
    /** 初始化引擎 */
    suspend fun initialize(context: Context, config: AsrConfig)

    /** 识别 PCM 音频数据，返回识别文本（阻塞） */
    suspend fun recognize(pcmData: ByteArray): String?

    /** 释放引擎资源 */
    suspend fun release()

    /** 是否正在运行 */
    fun isRunning(): Boolean
}

/**
 * 翻译引擎接口
 */
interface TranslateEngine {
    /** 初始化翻译引擎 */
    suspend fun initialize(context: Context, config: TranslateConfig)

    /** 翻译文本，返回翻译后文本 */
    suspend fun translate(text: String, sourceLanguage: String): String

    /** 释放引擎资源 */
    suspend fun release()
}