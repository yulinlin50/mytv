package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import java.io.File

/**
 * Whisper.cpp 离线语音识别引擎
 *
 * 通过 WhisperJni 调用 whisper.cpp 原生库进行推理。
 * 模型通过 ModelManager 运行时下载（tiny ~75MB / base ~142MB），首次使用需联网。
 */
class WhisperAsrEngine : AsrEngine {

    private var running = false
    private var contextPtr: Long = 0L

    override suspend fun initialize(context: Context, config: AsrConfig) {
        try {
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

    override suspend fun recognize(pcmData: ByteArray): String? {
        if (!running) {
            LiveAsrLogger.d("Whisper: recognize 跳过 (running=false)")
            return null
        }
        if (contextPtr == 0L) {
            LiveAsrLogger.d("Whisper: recognize 跳过 (contextPtr=0)")
            return null
        }

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
        }
    }

    override suspend fun release() {
        LiveAsrLogger.i("Whisper: release(), contextPtr=$contextPtr, running=$running")
        running = false
        if (contextPtr != 0L) {
            WhisperJni.free(contextPtr)
            contextPtr = 0L
            LiveAsrLogger.i("Whisper: 上下文已释放")
        }
    }

    override fun isRunning(): Boolean = running
}
