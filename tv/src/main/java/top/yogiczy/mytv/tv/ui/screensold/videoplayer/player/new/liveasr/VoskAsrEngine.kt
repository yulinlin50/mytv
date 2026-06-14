package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * Vosk 离线语音识别引擎
 *
 * 模型通过 ModelManager 运行时下载（~40MB），首次使用需联网
 * 下载后永久缓存于 app 私有目录
 */
class VoskAsrEngine : AsrEngine {

    private var recognizer: Recognizer? = null
    private var model: Model? = null
    private var running = false

    override suspend fun initialize(context: Context, config: AsrConfig) {
        try {
            // 确保模型已下载（未下载则自动下载 + 通知栏进度）
            val modelDir: File = try {
                ModelManager.ensureModel(context, ModelManager.VOSK_EN)
            } catch (e: Throwable) {
                LiveAsrLogger.e("Vosk: 模型不可用", e)
                throw IllegalStateException("Vosk 模型不可用: ${e.message}")
            }

            this.model = Model(modelDir.absolutePath)
            this.recognizer = Recognizer(this.model, 16000f)
            this.running = true
            LiveAsrLogger.i("Vosk: 初始化完成, 模型路径=${modelDir.absolutePath}")
        } catch (e: Throwable) {
            running = false
            LiveAsrLogger.e("Vosk: 初始化失败", e)
            throw e
        }
    }

    override suspend fun recognize(pcmData: ByteArray): String? {
        val rec = recognizer ?: return null
        if (!running) return null

        return try {
            if (rec.acceptWaveForm(pcmData, pcmData.size)) {
                val json = JSONObject(rec.result)
                json.optString("text", "").takeIf { it.isNotBlank() }
            } else {
                val json = JSONObject(rec.partialResult)
                json.optString("partial", "").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun release() {
        running = false
        recognizer?.close()
        model?.close()
        recognizer = null
        model = null
    }

    override fun isRunning(): Boolean = running
}