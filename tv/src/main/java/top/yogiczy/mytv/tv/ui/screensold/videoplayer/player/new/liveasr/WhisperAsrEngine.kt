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

            // 下载模型（若未下载）
            val modelDir = ModelManager.ensureModel(context, modelInfo)
            val modelFile = File(modelDir, modelInfo.destFileName)
            if (!modelFile.exists()) {
                throw IllegalStateException("Whisper 模型文件不存在: ${modelFile.absolutePath}")
            }

            // 初始化 Whisper JNI 上下文（延迟加载原生库）
            contextPtr = WhisperJni.init(modelFile.absolutePath)
            if (contextPtr == 0L) {
                throw IllegalStateException("Whisper 上下文初始化失败")
            }

            this.running = true
        } catch (e: Exception) {
            running = false
            throw e
        }
    }

    override suspend fun recognize(pcmData: ByteArray): String? {
        if (!running || contextPtr == 0L) return null

        return try {
            // 将 16bit PCM 转换为 float 数组
            val floats = WhisperJni.pcmBytesToFloats(pcmData)
            // 调用 whisper_full 推理
            WhisperJni.transcribe(contextPtr, floats)?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun release() {
        running = false
        if (contextPtr != 0L) {
            WhisperJni.free(contextPtr)
            contextPtr = 0L
        }
    }

    override fun isRunning(): Boolean = running
}
