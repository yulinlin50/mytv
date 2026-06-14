package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import java.io.File

/**
 * sherpa-onnx 流式语音识别引擎（OnlineRecognizer + Paraformer）
 *
 * 核心优势：
 * - 真正的流式推理：音频持续送入，实时输出 partial 结果
 * - 延迟极低：每帧推理 ~50-100ms，用户几乎无感
 * - 内置端点检测（endpoint）：自动检测句子结束，输出 final 结果
 * - 中英双语：支持普通话、河南话、天津话、四川话等方言
 *
 * 对比批处理模式：
 * - 批处理（SenseVoice）：整段推理，延迟 200-500ms + 等待 VAD 段结束
 * - 流式（Paraformer）：持续推理，延迟 ~100ms，边说边出字幕
 *
 * 使用方式：
 * 1. initialize() → 初始化模型
 * 2. startStream() → 开始流式会话
 * 3. feedChunk() → 持续送入音频帧
 * 4. 回调自动触发 partial/final 结果
 * 5. endStream() → 结束会话
 */
class SherpaOnnxStreamingAsrEngine : StreamingAsrEngine {

    private var running = false
    private var recognizer: OnlineRecognizer? = null
    private var config: AsrConfig? = null
    private var stream: OnlineStream? = null
    private var callback: ((AsrResult) -> Unit)? = null

    override suspend fun initialize(context: Context, config: AsrConfig) {
        try {
            this.config = config

            val modelInfo = ModelManager.STREAMING_PARAFORMER
            LiveAsrLogger.i("StreamingAsr: 初始化, 模型=${modelInfo.destDir}")

            // 下载模型（若未下载）
            val modelDir = ModelManager.ensureModel(context, modelInfo)

            // 模型文件路径
            val encoderFile = File(modelDir, "encoder.int8.onnx")
            val decoderFile = File(modelDir, "decoder.int8.onnx")
            val tokensFile = File(modelDir, "tokens.txt")

            if (!encoderFile.exists()) {
                LiveAsrLogger.e("StreamingAsr: encoder.int8.onnx 不存在: ${encoderFile.absolutePath}")
                throw IllegalStateException("流式 Paraformer encoder 模型文件不存在")
            }
            if (!decoderFile.exists()) {
                LiveAsrLogger.e("StreamingAsr: decoder.int8.onnx 不存在: ${decoderFile.absolutePath}")
                throw IllegalStateException("流式 Paraformer decoder 模型文件不存在")
            }
            if (!tokensFile.exists()) {
                LiveAsrLogger.e("StreamingAsr: tokens.txt 不存在: ${tokensFile.absolutePath}")
                throw IllegalStateException("流式 Paraformer tokens.txt 不存在")
            }

            LiveAsrLogger.i("StreamingAsr: 创建 OnlineRecognizer, encoder=${encoderFile.absolutePath}")

            // 创建 sherpa-onnx OnlineRecognizer 配置（Kotlin data class 命名参数）
            val paraformerConfig = OnlineParaformerModelConfig(
                encoder = encoderFile.absolutePath,
                decoder = decoderFile.absolutePath,
            )

            val modelConfig = OnlineModelConfig(
                paraformer = paraformerConfig,
                tokens = tokensFile.absolutePath,
                numThreads = 4,
                debug = false,
                provider = "cpu",
            )

            val recognizerConfig = OnlineRecognizerConfig(
                modelConfig = modelConfig,
                enableEndpoint = true,
            )

            recognizer = OnlineRecognizer(config = recognizerConfig)
            running = true

            LiveAsrLogger.i("StreamingAsr: 初始化完成")
        } catch (e: Exception) {
            running = false
            LiveAsrLogger.e("StreamingAsr: 初始化失败", e)
            throw e
        }
    }

    // ==================== StreamingAsrEngine 接口 ====================

    override fun startStream(callback: (result: AsrResult) -> Unit) {
        val rec = recognizer ?: return
        this.callback = callback

        stream = rec.createStream()
        LiveAsrLogger.i("StreamingAsr: 流式会话已开始")
    }

    override fun feedChunk(pcmData: ByteArray, ptsUs: Long) {
        val rec = recognizer ?: return
        val s = stream ?: return

        // PCM 字节 → 浮点
        val floats = pcmBytesToFloats(pcmData)

        // 送入流式识别器
        s.acceptWaveform(floats, 16000)

        // 解码
        while (rec.isReady(s)) {
            rec.decode(s)
        }

        // 获取 partial 结果
        val result = rec.getResult(s)
        val text = result.text.trim().takeIf { it.isNotBlank() }

        if (text != null) {
            val isFinal = rec.isEndpoint(s)

            callback?.invoke(
                AsrResult(
                    text = text,
                    isFinal = isFinal,
                    confidence = 1f,
                    startTimeUs = 0L,
                    endTimeUs = ptsUs,
                    language = config?.language ?: "zh",
                )
            )

            // 端点检测触发后重置流，准备下一段
            if (isFinal) {
                LiveAsrLogger.d("StreamingAsr: 端点检测, final=\"$text\"")
                rec.reset(s)
            }
        }
    }

    /**
     * 直接接受 FloatArray 的流式送入接口
     */
    fun feedChunkFloats(floats: FloatArray, ptsUs: Long) {
        val rec = recognizer ?: return
        val s = stream ?: return

        s.acceptWaveform(floats, 16000)

        while (rec.isReady(s)) {
            rec.decode(s)
        }

        val result = rec.getResult(s)
        val text = result.text?.trim()?.takeIf { it.isNotBlank() }

        if (text != null) {
            val isFinal = rec.isEndpoint(s)

            callback?.invoke(
                AsrResult(
                    text = text,
                    isFinal = isFinal,
                    confidence = 1f,
                    startTimeUs = 0L,
                    endTimeUs = ptsUs,
                    language = config?.language ?: "zh",
                )
            )

            if (isFinal) {
                LiveAsrLogger.d("StreamingAsr: 端点检测, final=\"$text\"")
                rec.reset(s)
            }
        }
    }

    override fun endStream() {
        stream?.let {
            runCatching { it.release() }
        }
        stream = null
        callback = null
        LiveAsrLogger.i("StreamingAsr: 流式会话已结束")
    }

    // ==================== 兼容旧版 AsrEngine 接口 ====================

    override suspend fun recognize(pcmData: ByteArray): String? {
        // 流式引擎不支持批处理识别，返回 null
        LiveAsrLogger.w("StreamingAsr: 不支持批处理 recognize()，请使用 startStream/feedChunk")
        return null
    }

    override suspend fun release() {
        LiveAsrLogger.i("StreamingAsr: release(), running=$running")
        running = false
        endStream()
        recognizer?.let {
            runCatching { it.release() }
        }
        recognizer = null
        LiveAsrLogger.i("StreamingAsr: 已释放")
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
}
