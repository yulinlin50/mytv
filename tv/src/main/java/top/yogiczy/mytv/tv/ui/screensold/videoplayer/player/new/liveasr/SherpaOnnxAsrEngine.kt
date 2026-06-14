package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import java.io.File

/**
 * sherpa-onnx + SenseVoice 离线语音识别引擎
 *
 * 核心优势：
 * - 非自回归模型：一次前向传播输出全部文本，推理速度比 Whisper 快 10-40 倍
 * - SenseVoice-Small INT8 模型仅 ~228MB，支持中/英/日/韩/粤 5 种语言
 * - 内置语言识别，无需额外 ML Kit Language ID 检测
 * - 支持标点恢复（useInverseTextNormalization）
 *
 * 对比 Whisper：
 * - Whisper tiny (75MB): 6秒音频推理 ~3-8秒（自回归逐token解码）
 * - SenseVoice INT8 (228MB): 6秒音频推理 ~200-500ms（非自回归一次输出）
 */
class SherpaOnnxAsrEngine : BatchAsrEngine {

    private var running = false
    private var recognizer: OfflineRecognizer? = null
    private var config: AsrConfig? = null

    override suspend fun initialize(context: Context, config: AsrConfig) {
        try {
            this.config = config

            // 确定模型目录
            val modelInfo = ModelManager.SENSE_VOICE_INT8
            LiveAsrLogger.i("SherpaOnnx: 初始化, 模型=${modelInfo.destDir}")

            // 下载模型（若未下载）
            val modelDir = ModelManager.ensureModel(context, modelInfo)

            // 模型文件路径（tar 解压可能有子目录，递归查找）
            val modelFile = ModelManager.findModelFile(context, modelInfo, modelInfo.destFileName)
                ?: File(modelDir, modelInfo.destFileName)
            val tokensFile = ModelManager.findModelFile(context, modelInfo, "tokens.txt")
                ?: File(modelDir, "tokens.txt")

            if (!modelFile.exists()) {
                LiveAsrLogger.e("SherpaOnnx: 模型文件不存在: ${modelFile.absolutePath}")
                throw IllegalStateException("SenseVoice 模型文件不存在")
            }
            if (!tokensFile.exists()) {
                LiveAsrLogger.e("SherpaOnnx: tokens.txt 不存在: ${tokensFile.absolutePath}")
                throw IllegalStateException("SenseVoice tokens.txt 不存在")
            }

            // 根据配置语言映射 SenseVoice 语言代码
            val senseVoiceLang = mapToSenseVoiceLanguage(config.language)

            LiveAsrLogger.i("SherpaOnnx: 创建识别器, model=${modelFile.absolutePath}, lang=$senseVoiceLang")

            // 创建 sherpa-onnx OfflineRecognizer 配置（Kotlin data class 命名参数）
            val senseVoiceConfig = OfflineSenseVoiceModelConfig(
                model = modelFile.absolutePath,
                language = senseVoiceLang,
                useInverseTextNormalization = true,
            )

            val modelConfig = OfflineModelConfig(
                senseVoice = senseVoiceConfig,
                tokens = tokensFile.absolutePath,
                numThreads = 4,
                debug = false,
                provider = "cpu",
            )

            val recognizerConfig = OfflineRecognizerConfig(
                modelConfig = modelConfig,
            )

            recognizer = OfflineRecognizer(config = recognizerConfig)
            running = true

            LiveAsrLogger.i("SherpaOnnx: 初始化完成")
        } catch (e: Exception) {
            running = false
            LiveAsrLogger.e("SherpaOnnx: 初始化失败", e)
            throw e
        }
    }

    // ==================== BatchAsrEngine 接口 ====================

    override suspend fun recognizeSegment(segment: AudioSegment): AsrResult? {
        if (!running) return null

        val rec = recognizer ?: return null

        LiveAsrLogger.d("SherpaOnnx: recognizeSegment 开始, ${segment.pcmData.size}samples, ${segment.durationMs}ms")

        return try {
            val stream: OfflineStream = rec.createStream()
            stream.acceptWaveform(segment.pcmData, segment.sampleRate)
            rec.decode(stream)

            val result = rec.getResult(stream)
            stream.release()

            val text = result.text.trim().takeIf { it.isNotBlank() }
            val lang = result.lang.takeIf { it.isNotBlank() } ?: config?.language ?: ""

            if (text != null) {
                LiveAsrLogger.d("SherpaOnnx: 识别完成, \"$text\", lang=$lang")
                AsrResult(
                    text = text,
                    isFinal = true,
                    confidence = 1f,
                    startTimeUs = segment.startTimeUs,
                    endTimeUs = segment.endTimeUs,
                    language = lang,
                )
            } else {
                LiveAsrLogger.d("SherpaOnnx: 识别结果为空")
                null
            }
        } catch (e: Exception) {
            LiveAsrLogger.e("SherpaOnnx: recognizeSegment 异常", e)
            null
        }
    }

    // ==================== 兼容旧版 AsrEngine 接口 ====================

    override suspend fun recognize(pcmData: ByteArray): String? {
        if (!running) return null

        val rec = recognizer ?: return null

        LiveAsrLogger.d("SherpaOnnx: recognize 开始, pcmData=${pcmData.size}字节, 约${pcmData.size / 32}ms")

        return try {
            val floats = pcmBytesToFloats(pcmData)

            val stream: OfflineStream = rec.createStream()
            stream.acceptWaveform(floats, 16000)
            rec.decode(stream)

            val result = rec.getResult(stream)
            stream.release()

            val text = result.text.trim().takeIf { it.isNotBlank() }
            LiveAsrLogger.d("SherpaOnnx: recognize 完成, 结果=\"${text ?: "null"}\"")
            text
        } catch (e: Exception) {
            LiveAsrLogger.e("SherpaOnnx: recognize 异常", e)
            null
        }
    }

    /**
     * 直接接受 FloatArray 的识别接口
     * LiveAsrProcessor 已有 FloatArray 数据，避免重复转换
     */
    fun recognizeFloats(floats: FloatArray, sampleRate: Int = 16000): String? {
        if (!running) return null

        val rec = recognizer ?: return null

        LiveAsrLogger.d("SherpaOnnx: recognizeFloats 开始, ${floats.size}samples, 约${floats.size * 1000 / sampleRate}ms")

        return try {
            val stream: OfflineStream = rec.createStream()
            stream.acceptWaveform(floats, sampleRate)
            rec.decode(stream)

            val result = rec.getResult(stream)
            stream.release()

            val text = result.text.trim().takeIf { it.isNotBlank() }
            LiveAsrLogger.d("SherpaOnnx: recognizeFloats 完成, 结果=\"${text ?: "null"}\"")
            text
        } catch (e: Exception) {
            LiveAsrLogger.e("SherpaOnnx: recognizeFloats 异常", e)
            null
        }
    }

    override suspend fun release() {
        LiveAsrLogger.i("SherpaOnnx: release(), running=$running")
        running = false
        recognizer?.let {
            runCatching { it.release() }
        }
        recognizer = null
        LiveAsrLogger.i("SherpaOnnx: 已释放")
    }

    override fun isRunning(): Boolean = running

    // ==================== 工具方法 ====================

    /** PCM 16-bit 字节 → 浮点数据（归一化到 [-1, 1]） */
    private fun pcmBytesToFloats(pcmBytes: ByteArray): FloatArray {
        val floatCount = pcmBytes.size / 2
        val floats = FloatArray(floatCount)
        val buf = java.nio.ByteBuffer.wrap(pcmBytes)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        for (i in 0 until floatCount) {
            floats[i] = buf.get(i) / 32768f
        }
        return floats
    }

    /**
     * 将配置语言代码映射为 SenseVoice 语言代码
     *
     * SenseVoice 支持的语言：
     * - auto: 自动检测
     * - zh: 中文（普通话）
     * - yue: 粤语
     * - en: 英语
     * - ja: 日语
     * - ko: 韩语
     */
    private fun mapToSenseVoiceLanguage(lang: String): String {
        return when (lang.lowercase()) {
            "auto", "" -> "auto"
            "zh", "zh-cn", "zh-hans", "cmn", "chi" -> "zh"
            "yue", "zh-yue", "zh-hk" -> "yue"
            "en", "eng" -> "en"
            "ja", "jpn" -> "ja"
            "ko", "kor" -> "ko"
            else -> "auto"  // 未知语言使用自动检测
        }
    }
}
