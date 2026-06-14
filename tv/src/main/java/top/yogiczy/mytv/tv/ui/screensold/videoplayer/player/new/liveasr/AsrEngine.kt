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
    val language: String = "en",  // 识别语言 BCP-47 代码（如 "en", "zh", "ja"）
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
 * ASR 识别结果（结构化）
 */
data class AsrResult(
    val text: String,
    val isFinal: Boolean = true,      // partial vs final
    val confidence: Float = 1f,       // 置信度 [0, 1]
    val startTimeUs: Long = 0L,       // 开始时间戳（微秒）
    val endTimeUs: Long = 0L,         // 结束时间戳（微秒）
    val language: String = "",        // 检测到的语言
)

/**
 * 语音识别引擎基础接口（兼容旧引擎）
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
 * 流式 ASR 引擎接口
 *
 * 适用于 Vosk、Azure Speech、Google Speech-to-Text 等 WebSocket 流式引擎。
 * 音频数据通过 feedChunk 持续送入，识别结果通过回调实时输出。
 * 优势：低延迟，partial 结果可实时显示。
 */
interface StreamingAsrEngine : AsrEngine {
    /**
     * 开始流式识别会话
     *
     * @param callback 识别结果回调（partial 和 final 结果都会回调）
     */
    fun startStream(callback: (result: AsrResult) -> Unit)

    /**
     * 送入一帧音频数据
     *
     * @param pcmData 16kHz 单声道 PCM 字节数据
     * @param ptsUs 展示时间戳（微秒）
     */
    fun feedChunk(pcmData: ByteArray, ptsUs: Long)

    /**
     * 结束流式识别会话
     */
    fun endStream()
}

/**
 * 批处理 ASR 引擎接口
 *
 * 适用于 Whisper.cpp、云端 REST API 等整段识别引擎。
 * 一次送入完整音频段，返回最终识别结果。
 * 优势：识别精度高，适合离线场景。
 */
interface BatchAsrEngine : AsrEngine {
    /**
     * 识别一个音频段
     *
     * @param segment 带 PTS 时间戳的音频段
     * @return 识别结果，失败返回 null
     */
    suspend fun recognizeSegment(segment: AudioSegment): AsrResult?
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
