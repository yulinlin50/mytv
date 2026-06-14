package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * Vosk 离线语音识别引擎（流式改造版）
 *
 * 实现 StreamingAsrEngine 接口，支持实时流式识别。
 * - feedChunk() 持续送入音频帧
 * - 通过 callback 实时输出 partial/final 结果
 * - 兼容旧版 recognize() 接口（BatchAsrEngine 回退）
 *
 * 模型通过 ModelManager 运行时下载（~40MB），首次使用需联网
 * 下载后永久缓存于 app 私有目录
 */
class VoskAsrEngine : StreamingAsrEngine {

    private var recognizer: Recognizer? = null
    private var model: Model? = null
    private var running = false

    // 流式状态
    @Volatile
    private var streaming = false
    private var streamCallback: ((AsrResult) -> Unit)? = null
    private var currentStartPtsUs: Long = 0L
    private var currentEndPtsUs: Long = 0L
    private var config: AsrConfig? = null

    override suspend fun initialize(context: Context, config: AsrConfig) {
        try {
            this.config = config

            // 根据识别语言选择对应的 Vosk 模型
            val modelId = when (config.language.lowercase()) {
                "zh", "zh-cn", "cmn" -> ModelManager.VOSK_ZH
                else -> ModelManager.VOSK_EN
            }

            // 确保模型已下载（未下载则自动下载 + 通知栏进度）
            val modelDir: File = try {
                ModelManager.ensureModel(context, modelId)
            } catch (e: Throwable) {
                LiveAsrLogger.e("Vosk: 模型不可用", e)
                throw IllegalStateException("Vosk 模型不可用: ${e.message}")
            }

            this.model = Model(modelDir.absolutePath)
            this.recognizer = Recognizer(this.model, 16000f)
            this.running = true
            LiveAsrLogger.i("Vosk: 初始化完成, 语言=${config.language}, 模型=$modelId, 路径=${modelDir.absolutePath}")
        } catch (e: Throwable) {
            running = false
            LiveAsrLogger.e("Vosk: 初始化失败", e)
            throw e
        }
    }

    // ==================== StreamingAsrEngine 接口 ====================

    override fun startStream(callback: (result: AsrResult) -> Unit) {
        streamCallback = callback
        streaming = true
        currentStartPtsUs = 0L
        currentEndPtsUs = 0L

        // 重置 Recognizer 状态，开始新的识别会话
        recognizer?.reset()
        LiveAsrLogger.d("Vosk: 流式识别会话开始")
    }

    override fun feedChunk(pcmData: ByteArray, ptsUs: Long) {
        val rec = recognizer ?: return
        if (!streaming) return

        // 更新 PTS 范围
        if (currentStartPtsUs == 0L) {
            currentStartPtsUs = ptsUs
        }
        currentEndPtsUs = ptsUs

        try {
            if (rec.acceptWaveForm(pcmData, pcmData.size)) {
                // Final 结果
                val json = JSONObject(rec.result)
                val text = json.optString("text", "").takeIf { it.isNotBlank() }
                if (text != null) {
                    streamCallback?.invoke(
                        AsrResult(
                            text = text,
                            isFinal = true,
                            confidence = 1f,
                            startTimeUs = currentStartPtsUs,
                            endTimeUs = currentEndPtsUs,
                            language = config?.language ?: "",
                        )
                    )
                    // Final 后重置起始 PTS
                    currentStartPtsUs = ptsUs
                }
            } else {
                // Partial 结果
                val json = JSONObject(rec.partialResult)
                val partial = json.optString("partial", "").takeIf { it.isNotBlank() }
                if (partial != null) {
                    streamCallback?.invoke(
                        AsrResult(
                            text = partial,
                            isFinal = false,
                            confidence = 0.5f,
                            startTimeUs = currentStartPtsUs,
                            endTimeUs = currentEndPtsUs,
                            language = config?.language ?: "",
                        )
                    )
                }
            }
        } catch (e: Exception) {
            LiveAsrLogger.e("Vosk: feedChunk 异常", e)
        }
    }

    override fun endStream() {
        if (!streaming) return
        streaming = false

        // 输出剩余的 final 结果
        val rec = recognizer ?: return
        try {
            val json = JSONObject(rec.finalResult)
            val text = json.optString("text", "").takeIf { it.isNotBlank() }
            if (text != null) {
                streamCallback?.invoke(
                    AsrResult(
                        text = text,
                        isFinal = true,
                        confidence = 1f,
                        startTimeUs = currentStartPtsUs,
                        endTimeUs = currentEndPtsUs,
                        language = config?.language ?: "",
                    )
                )
            }
        } catch (e: Exception) {
            LiveAsrLogger.e("Vosk: endStream 异常", e)
        }

        streamCallback = null
        LiveAsrLogger.d("Vosk: 流式识别会话结束")
    }

    // ==================== 兼容旧版 AsrEngine 接口 ====================

    override suspend fun recognize(pcmData: ByteArray): String? {
        val rec = recognizer ?: return null
        if (!running) return null

        return try {
            // 重置 Recognizer 状态
            rec.reset()

            // 一次性送入所有数据
            rec.acceptWaveForm(pcmData, pcmData.size)

            // 获取最终结果
            val json = JSONObject(rec.finalResult)
            val text = json.optString("text", "").takeIf { it.isNotBlank() }

            if (text != null) {
                text
            } else {
                // 尝试获取 partial 结果
                val partialJson = JSONObject(rec.partialResult)
                partialJson.optString("partial", "").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            LiveAsrLogger.e("Vosk: recognize 异常", e)
            null
        }
    }

    override suspend fun release() {
        streaming = false
        streamCallback = null
        running = false
        recognizer?.close()
        model?.close()
        recognizer = null
        model = null
        LiveAsrLogger.i("Vosk: 引擎已释放")
    }

    override fun isRunning(): Boolean = running
}
