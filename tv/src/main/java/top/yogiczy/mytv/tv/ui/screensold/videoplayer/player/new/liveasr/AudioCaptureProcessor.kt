package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ExoPlayer 音频截取处理器
 * 从播放器中截取 PCM 音频数据，输出给 ASR 引擎
 *
 * 注意：configure() 返回 inputAudioFormat（pass-through），不影响播放音质。
 * 内部将截取的音频重采样为 16kHz 单声道后输出给 ASR 引擎。
 */
@OptIn(UnstableApi::class)
class AudioCaptureProcessor : AudioProcessor {

    private val listeners = CopyOnWriteArrayList<(ByteArray, Long) -> Unit>()
    private var enabled = false
    private var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var audioDataCount = 0 // 用于周期性日志

    // 重采样状态
    private var inputSampleRate = 0
    private var inputChannelCount = 0

    // PTS 追踪
    private var totalInputSamples: Long = 0L  // 累计输入样本数

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        this.inputSampleRate = inputAudioFormat.sampleRate
        this.inputChannelCount = inputAudioFormat.channelCount
        LiveAsrLogger.init() // 确保 logger 已初始化（configure 可能在 startLiveSubtitle 之前调用）
        LiveAsrLogger.i("AudioCapture: configure sampleRate=${inputAudioFormat.sampleRate}, channels=${inputAudioFormat.channelCount}, encoding=${inputAudioFormat.encoding}")
        // Pass-through：不改变输出格式，不影响播放音质
        return inputAudioFormat
    }

    // 始终返回 true，确保 DefaultAudioSink 在配置时将本处理器纳入处理链
    // 即使实时字幕未启用，处理器也会接收音频数据（但不会转发给监听器）
    override fun isActive(): Boolean = true

    fun setActive(active: Boolean) {
        enabled = active
        LiveAsrLogger.i("AudioCapture: setActive($active)")
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position

        if (size > 0) {
            // 截取音频数据给 ASR 引擎（使用 duplicate 不影响 position）
            if (enabled && listeners.isNotEmpty()) {
                // 计算当前帧的 PTS（基于累计样本数）
                val inputSamples = size / (2 * inputChannelCount)  // 16-bit = 2 bytes per sample
                val ptsUs = if (inputSampleRate > 0) {
                    totalInputSamples * 1_000_000L / inputSampleRate
                } else 0L
                totalInputSamples += inputSamples

                // 复制原始 PCM 数据
                val data = ByteArray(size)
                inputBuffer.duplicate().get(data, 0, size)
                // 重采样为 16kHz 单声道后输出给 ASR 引擎
                val resampled = resampleTo16kMono(data)
                if (resampled.isNotEmpty()) {
                    listeners.forEach { listener ->
                        try {
                            listener(resampled, ptsUs)
                        } catch (_: Exception) {
                        }
                    }
                    // 日志
                    audioDataCount++
                    if (audioDataCount == 1) {
                        LiveAsrLogger.i("AudioCapture: 首次转发, 原始${size}字节 → 重采样后${resampled.size}字节 (${inputSampleRate}Hz/${inputChannelCount}ch → 16000Hz/1ch)")
                    } else if (audioDataCount % 100 == 1) {
                        LiveAsrLogger.d("AudioCapture: 已转发${audioDataCount}次音频数据, 本次${resampled.size}字节")
                    }
                }
            }

            // Pass through: 复制输入数据到输出缓冲区
            val copy = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
            inputBuffer.duplicate().get(copy.array(), 0, size)
            copy.flip()
            outputBuffer = copy

            // 关键：必须推进 inputBuffer 的 position，否则 ExoPlayer 会认为数据未被处理
            inputBuffer.position(limit)
        } else {
            outputBuffer = AudioProcessor.EMPTY_BUFFER
        }
    }

    fun addListener(listener: (ByteArray, Long) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (ByteArray, Long) -> Unit) {
        listeners.remove(listener)
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = false

    override fun queueEndOfStream() {
        // No special handling needed
    }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
    }

    override fun reset() {
        listeners.clear()
        enabled = false
        inputAudioFormat = AudioFormat.NOT_SET
        inputSampleRate = 0
        inputChannelCount = 0
        totalInputSamples = 0L
        outputBuffer = AudioProcessor.EMPTY_BUFFER
    }

    /**
     * 将 PCM 16-bit 数据重采样为 16kHz 单声道
     *
     * 处理步骤：
     * 1. 多声道 → 单声道（取声道平均值）
     * 2. 采样率转换（线性插值）
     */
    private fun resampleTo16kMono(data: ByteArray): ByteArray {
        if (data.size < 2) return ByteArray(0)

        // PCM 16-bit sample 数量
        val sampleCount = data.size / 2
        val shortBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = ShortArray(sampleCount)
        shortBuffer.get(samples)

        // 1. 多声道 → 单声道
        val monoSamples = if (inputChannelCount > 1) {
            val monoCount = sampleCount / inputChannelCount
            val mono = ShortArray(monoCount)
            for (i in 0 until monoCount) {
                var sum = 0L
                for (ch in 0 until inputChannelCount) {
                    sum += samples[i * inputChannelCount + ch]
                }
                mono[i] = (sum / inputChannelCount).toShort()
            }
            mono
        } else {
            samples
        }

        // 2. 采样率转换（线性插值）
        val targetSampleRate = 16000
        val resampled = if (inputSampleRate != targetSampleRate && inputSampleRate > 0) {
            val ratio = monoSamples.size.toDouble() * targetSampleRate / inputSampleRate
            val outputLength = ratio.toInt()
            if (outputLength <= 0) return ByteArray(0)
            val out = ShortArray(outputLength)
            for (i in 0 until outputLength) {
                val srcIndex = i.toDouble() * inputSampleRate / targetSampleRate
                val srcFloor = srcIndex.toInt()
                val srcCeil = srcFloor + 1
                val fraction = srcIndex - srcFloor
                if (srcCeil < monoSamples.size) {
                    out[i] = (monoSamples[srcFloor] * (1.0 - fraction) + monoSamples[srcCeil] * fraction).toInt().toShort()
                } else if (srcFloor < monoSamples.size) {
                    out[i] = monoSamples[srcFloor]
                }
            }
            out
        } else {
            monoSamples
        }

        // ShortArray → ByteArray (little-endian)
        val result = ByteArray(resampled.size * 2)
        ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(resampled)
        return result
    }
}