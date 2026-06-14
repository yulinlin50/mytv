package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import java.io.File

/**
 * Whisper.cpp 离线语音识别引擎（BatchAsrEngine 改造版）
 *
 * 实现 BatchAsrEngine 接口，支持带 PTS 的结构化识别结果。
 * 同时兼容旧版 AsrEngine.recognize() 接口。
 *
 * 通过 WhisperJni 调用 whisper.cpp 原生库进行推理。
 * 模型通过 ModelManager 运行时下载（tiny ~75MB / base ~142MB），首次使用需联网。
 */
class WhisperAsrEngine : BatchAsrEngine {

    private var running = false
    private var contextPtr: Long = 0L
    @Volatile
    private var isInferencing = false
    private var config: AsrConfig? = null
    private val releaseLock = Any()  // 防止 release() 被并发调用导致 double free

    override suspend fun initialize(context: Context, config: AsrConfig) {
        try {
            this.config = config

            // 根据配置选择对应模型
            val modelInfo = when (config.whisperModel) {
                "base" -> ModelManager.WHISPER_BASE
                else -> ModelManager.WHISPER_TINY
            }

            LiveAsrLogger.i("Whisper: 初始化, 模型=${modelInfo.destDir}")

            // 下载模型（若未下载）
            val modelDir = ModelManager.ensureModel(context, modelInfo)
            val modelFile = File(modelDir, modelInfo.destFileName)
            if (!modelFile.exists()) {
                LiveAsrLogger.e("Whisper: 模型文件不存在: ${modelFile.absolutePath}")
                throw IllegalStateException("Whisper 模型文件不存在: ${modelFile.absolutePath}")
            }

            // 初始化 Whisper JNI 上下文（延迟加载原生库）
            contextPtr = WhisperJni.init(modelFile.absolutePath)
            if (contextPtr == 0L) {
                LiveAsrLogger.e("Whisper: 上下文初始化失败")
                throw IllegalStateException("Whisper 上下文初始化失败")
            }

            this.running = true
            LiveAsrLogger.i("Whisper: 初始化完成, contextPtr=$contextPtr")
        } catch (e: Exception) {
            running = false
            LiveAsrLogger.e("Whisper: 初始化失败", e)
            throw e
        }
    }

    // ==================== BatchAsrEngine 接口 ====================

    override suspend fun recognizeSegment(segment: AudioSegment): AsrResult? {
        if (!running || contextPtr == 0L) return null

        isInferencing = true
        LiveAsrLogger.d("Whisper: recognizeSegment 开始, ${segment.pcmData.size}samples, ${segment.durationMs}ms")

        return try {
            val result = WhisperJni.transcribe(contextPtr, segment.pcmData)
            val text = result?.trim()?.takeIf { it.isNotBlank() }

            if (text != null) {
                LiveAsrLogger.d("Whisper: 识别完成, \"$text\"")
                AsrResult(
                    text = text,
                    isFinal = true,
                    confidence = 1f,
                    startTimeUs = segment.startTimeUs,
                    endTimeUs = segment.endTimeUs,
                    language = config?.language ?: "",
                )
            } else {
                LiveAsrLogger.d("Whisper: 识别结果为空")
                null
            }
        } catch (e: Exception) {
            LiveAsrLogger.e("Whisper: recognizeSegment 异常", e)
            null
        } finally {
            isInferencing = false
        }
    }

    // ==================== 兼容旧版 AsrEngine 接口 ====================

    override suspend fun recognize(pcmData: ByteArray): String? {
        if (!running || contextPtr == 0L) return null

        isInferencing = true
        LiveAsrLogger.d("Whisper: recognize 开始, pcmData=${pcmData.size}字节, 约${pcmData.size / 32}ms")
        return try {
            val floats = WhisperJni.pcmBytesToFloats(pcmData)
            LiveAsrLogger.d("Whisper: pcm->floats 完成, ${floats.size} samples")
            val result = WhisperJni.transcribe(contextPtr, floats)
            LiveAsrLogger.d("Whisper: transcribe 完成, 结果=\"${result ?: "null"}\"")
            result?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            LiveAsrLogger.e("Whisper: recognize 异常", e)
            null
        } finally {
            isInferencing = false
        }
    }

    /**
     * 直接接受 FloatArray 的识别接口，避免 PCM→Float 重复转换
     * 滑动窗口推理时，LiveAsrProcessor 已经有 FloatArray 数据
     */
    suspend fun recognizeFloats(floats: FloatArray): String? {
        if (!running || contextPtr == 0L) return null

        isInferencing = true
        LiveAsrLogger.d("Whisper: recognizeFloats 开始, ${floats.size} samples, 约${floats.size * 1000 / 16000}ms")
        return try {
            val result = WhisperJni.transcribe(contextPtr, floats)
            LiveAsrLogger.d("Whisper: transcribe 完成, 结果=\"${result ?: "null"}\"")
            result?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            LiveAsrLogger.e("Whisper: recognizeFloats 异常", e)
            null
        } finally {
            isInferencing = false
        }
    }

    override suspend fun release() {
        synchronized(releaseLock) {
            LiveAsrLogger.i("Whisper: release(), contextPtr=$contextPtr, running=$running, isInferencing=$isInferencing")
            running = false

            if (contextPtr == 0L) {
                LiveAsrLogger.i("Whisper: 已释放，跳过")
                return
            }

            // 等待 native 推理完成后再释放上下文，避免 use-after-free 导致 SIGSEGV
            var waitCount = 0
            while (isInferencing && waitCount < 300) { // 最多等 30 秒
                Thread.sleep(100)
                waitCount++
            }
            if (isInferencing) {
                LiveAsrLogger.w("Whisper: 等待推理超时，强制释放（可能崩溃）")
            }
            if (contextPtr != 0L) {
                WhisperJni.free(contextPtr)
                contextPtr = 0L
                LiveAsrLogger.i("Whisper: 上下文已释放")
            }
        }
    }

    override fun isRunning(): Boolean = running
}
