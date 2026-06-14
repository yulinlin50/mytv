package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 语音活动检测器（Voice Activity Detection）
 *
 * 基于 RMS 能量检测，判断音频段是否包含有效语音。
 * 静音段跳过推理，节省 CPU 和网络资源。
 *
 * 检测逻辑：
 * 1. 计算每帧 RMS 能量（dB）
 * 2. 超过阈值 → 标记为语音开始
 * 3. 低于阈值持续 minSilenceMs → 标记为语音结束
 * 4. 最短语音段 minSpeechMs 过滤噪声误触发
 * 5. 最大语音段 maxSpeechMs → 强制截断（防止直播音频持续播放导致永远不触发结束）
 */
class VadDetector(
    /** 能量阈值（dB），低于此值视为静音。典型值 -40 ~ -30 */
    private val thresholdDb: Float = -40f,
    /** 最短语音段时长（毫秒），短于此的语音段视为噪声 */
    private val minSpeechMs: Long = 200,
    /** 最短静音间隔（毫秒），静音持续此时间才判定语音结束 */
    private val minSilenceMs: Long = 300,
    /** 最大语音段时长（毫秒），超过此值强制截断输出 */
    private val maxSpeechMs: Long = 5000,
    /** 采样率 */
    private val sampleRate: Int = 16000,
) {
    /** VAD 事件 */
    sealed class Event {
        /** 检测到语音开始 */
        data class SpeechStart(val ptsUs: Long) : Event()
        /** 检测到语音结束 */
        data class SpeechEnd(val ptsUs: Long) : Event()
    }

    enum class State { SILENCE, SPEECH }

    var state: State = State.SILENCE
        private set

    // 语音开始时的 PTS
    private var speechStartPtsUs: Long = 0L
    // 进入静音后的持续时间（样本数）
    private var silenceSampleCount: Int = 0
    // 当前语音段的总样本数
    private var speechSampleCount: Int = 0

    // 上一次 SpeechEnd 是否由超时截断触发
    private var lastEndWasTimeout: Boolean = false

    private val minSilenceSamples = (minSilenceMs * sampleRate / 1000L).toInt()
    private val minSpeechSamples = (minSpeechMs * sampleRate / 1000L).toInt()
    private val maxSpeechSamples = (maxSpeechMs * sampleRate / 1000L).toInt()

    /**
     * 处理一帧音频数据
     *
     * @param samples PCM 浮点数据（归一化到 [-1, 1]）
     * @param ptsUs 当前帧的展示时间戳（微秒）
     * @return VAD 事件，无事件时返回 null
     */
    fun process(samples: FloatArray, ptsUs: Long): Event? {
        val rms = computeRms(samples)
        val db = rmsToDb(rms)
        val isSpeech = db > thresholdDb

        return when (state) {
            State.SILENCE -> {
                if (isSpeech) {
                    state = State.SPEECH
                    speechStartPtsUs = ptsUs
                    speechSampleCount = samples.size
                    silenceSampleCount = 0
                    Event.SpeechStart(ptsUs)
                } else {
                    null
                }
            }

            State.SPEECH -> {
                speechSampleCount += samples.size

                // 最大语音时长截断：直播音频持续播放时，防止永远不触发 SpeechEnd
                if (speechSampleCount >= maxSpeechSamples) {
                    state = State.SILENCE
                    lastEndWasTimeout = true
                    LiveAsrLogger.d("VAD: 语音段超时(${speechSampleCount * 1000 / sampleRate}ms >= ${maxSpeechMs}ms)，强制截断")
                    Event.SpeechEnd(ptsUs)
                } else if (isSpeech) {
                    // 仍在说话，重置静音计数
                    silenceSampleCount = 0
                    null
                } else {
                    // 可能的语音结束
                    silenceSampleCount += samples.size

                    if (silenceSampleCount >= minSilenceSamples) {
                        // 确认语音结束
                        state = State.SILENCE
                        lastEndWasTimeout = false

                        // 过滤过短的语音段
                        if (speechSampleCount >= minSpeechSamples) {
                            Event.SpeechEnd(ptsUs)
                        } else {
                            LiveAsrLogger.d("VAD: 语音段过短(${speechSampleCount * 1000 / sampleRate}ms < ${minSpeechMs}ms)，忽略")
                            null
                        }
                    } else {
                        // 静音时间不够，继续等待
                        null
                    }
                }
            }
        }
    }

    /**
     * 获取当前语音段的起始 PTS
     */
    fun getSpeechStartPtsUs(): Long = speechStartPtsUs

    /**
     * 上一次 SpeechEnd 是否由超时截断触发
     */
    fun wasTimeout(): Boolean = lastEndWasTimeout

    /**
     * 重置检测器状态
     */
    fun reset() {
        state = State.SILENCE
        speechStartPtsUs = 0L
        silenceSampleCount = 0
        speechSampleCount = 0
        lastEndWasTimeout = false
    }

    companion object {
        /** 计算 RMS 能量 */
        fun computeRms(samples: FloatArray): Float {
            if (samples.isEmpty()) return 0f
            var sum = 0.0
            for (s in samples) {
                sum += s.toDouble() * s.toDouble()
            }
            return sqrt(sum / samples.size).toFloat()
        }

        /** RMS 转 dB */
        fun rmsToDb(rms: Float): Float {
            return 20f * log10(max(rms, 1e-10f))
        }
    }
}
