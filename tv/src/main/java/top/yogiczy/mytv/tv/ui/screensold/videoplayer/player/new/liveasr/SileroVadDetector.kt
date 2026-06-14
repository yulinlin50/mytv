package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/**
 * 基于 Silero VAD 的语音活动检测器（sherpa-onnx 实现）
 *
 * 对比 RMS 能量检测：
 * - Silero VAD：4 层 DNN 模型，精准区分人声/噪声/音乐/掌声
 * - RMS 能量：简单阈值判断，噪声环境误判率高
 *
 * Silero VAD 是业界标准，几乎所有主流实时字幕方案都使用它。
 * 模型仅 ~2MB，推理耗时 ~1ms/帧。
 *
 * 使用与 VadDetector 相同的 Event 类型，便于 LiveAsrProcessor 无缝切换。
 */
class SileroVadDetector(
    private val sampleRate: Int = 16000,
    /** Silero VAD 语音概率阈值（0.0-1.0），高于此值判定为语音 */
    private val threshold: Float = 0.5f,
    /** 最短语音段时长（毫秒），短于此的语音段视为噪声 */
    private val minSpeechMs: Long = 200,
    /** 最短静音间隔（毫秒），静音持续此时间才判定语音结束 */
    private val minSilenceMs: Long = 300,
    /** 最大语音段时长（毫秒），超过此值强制截断输出 */
    private val maxSpeechMs: Long = 5000,
) {

    enum class State { SILENCE, SPEECH }

    var state: State = State.SILENCE
        private set

    private var speechStartPtsUs: Long = 0L
    private var silenceSampleCount: Int = 0
    private var speechSampleCount: Int = 0
    private var lastEndWasTimeout: Boolean = false

    private val minSilenceSamples = (minSilenceMs * sampleRate / 1000L).toInt()
    private val minSpeechSamples = (minSpeechMs * sampleRate / 1000L).toInt()
    private val maxSpeechSamples = (maxSpeechMs * sampleRate / 1000L).toInt()

    // sherpa-onnx VAD 实例
    private var vad: Vad? = null

    /**
     * 初始化 Silero VAD 模型
     * 必须在调用 process() 之前调用
     */
    fun initialize(context: Context) {
        try {
            // 确保 Silero VAD 模型已下载
            val modelDir = ModelManager.ensureModel(context, ModelManager.SILERO_VAD)
            val modelFile = File(modelDir, ModelManager.SILERO_VAD.destFileName)

            if (!modelFile.exists()) {
                LiveAsrLogger.e("SileroVAD: 模型文件不存在: ${modelFile.absolutePath}")
                throw IllegalStateException("Silero VAD 模型文件不存在")
            }

            // 使用 sherpa-onnx Builder 模式创建 VAD 配置
            val sileroConfig = SileroVadModelConfig.builder()
                .setModel(modelFile.absolutePath)
                .setThreshold(threshold)
                .setMinSilenceDuration(minSilenceMs / 1000f)
                .setMinSpeechDuration(minSpeechMs / 1000f)
                .setWindowSizeInSeconds(512f / sampleRate)  // Silero VAD 标准窗口 512 samples
                .build()

            val vadConfig = VadModelConfig.builder()
                .setSileroVadModelConfig(sileroConfig)
                .setSampleRate(sampleRate)
                .setNumThreads(1)
                .setProvider("cpu")
                .setDebug(false)
                .build()

            vad = Vad(vadConfig)
            LiveAsrLogger.i("SileroVAD: 初始化完成, threshold=$threshold")
        } catch (e: Exception) {
            LiveAsrLogger.e("SileroVAD: 初始化失败", e)
            throw e
        }
    }

    /**
     * 处理一帧音频数据
     *
     * @param samples PCM 浮点数据（归一化到 [-1, 1]）
     * @param ptsUs 当前帧的展示时间戳（微秒）
     * @return VAD 事件（使用 VadDetector.Event 类型），无事件时返回 null
     */
    fun process(samples: FloatArray, ptsUs: Long): VadDetector.Event? {
        val vadInstance = vad ?: return null

        // 送入 Silero VAD 推理
        vadInstance.acceptWaveform(samples)
        val isSpeech = vadInstance.isSpeechDetected

        return when (state) {
            State.SILENCE -> {
                if (isSpeech) {
                    state = State.SPEECH
                    speechStartPtsUs = ptsUs
                    speechSampleCount = samples.size
                    silenceSampleCount = 0
                    VadDetector.Event.SpeechStart(ptsUs)
                } else {
                    null
                }
            }

            State.SPEECH -> {
                speechSampleCount += samples.size

                // 最大语音时长截断
                if (speechSampleCount >= maxSpeechSamples) {
                    state = State.SILENCE
                    lastEndWasTimeout = true
                    LiveAsrLogger.d("SileroVAD: 语音段超时(${speechSampleCount * 1000 / sampleRate}ms >= ${maxSpeechMs}ms)，强制截断")
                    vadInstance.reset()  // 重置 VAD 状态
                    VadDetector.Event.SpeechEnd(ptsUs)
                } else if (isSpeech) {
                    silenceSampleCount = 0
                    null
                } else {
                    silenceSampleCount += samples.size

                    if (silenceSampleCount >= minSilenceSamples) {
                        state = State.SILENCE
                        lastEndWasTimeout = false

                        if (speechSampleCount >= minSpeechSamples) {
                            VadDetector.Event.SpeechEnd(ptsUs)
                        } else {
                            LiveAsrLogger.d("SileroVAD: 语音段过短(${speechSampleCount * 1000 / sampleRate}ms < ${minSpeechMs}ms)，忽略")
                            null
                        }
                    } else {
                        null
                    }
                }
            }
        }
    }

    fun getSpeechStartPtsUs(): Long = speechStartPtsUs

    fun wasTimeout(): Boolean = lastEndWasTimeout

    fun reset() {
        state = State.SILENCE
        speechStartPtsUs = 0L
        silenceSampleCount = 0
        speechSampleCount = 0
        lastEndWasTimeout = false
        vad?.reset()
    }

    fun release() {
        vad?.let {
            runCatching { it.release() }
        }
        vad = null
    }
}
