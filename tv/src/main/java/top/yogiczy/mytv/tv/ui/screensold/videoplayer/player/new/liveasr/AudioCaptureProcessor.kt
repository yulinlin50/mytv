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
 */
@OptIn(UnstableApi::class)
class AudioCaptureProcessor : AudioProcessor {

    private val listeners = CopyOnWriteArrayList<(ByteArray) -> Unit>()
    private var isActive = false
    private var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        // 如果需要重采样到 16kHz 单声道 16bit，可以在此处理
        // 当前保持原始格式，由 ASR 引擎自行处理
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    fun setActive(active: Boolean) {
        isActive = active
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive || listeners.isEmpty()) return

        // 读取 PCM 数据
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position
        if (size <= 0) return

        val data = ByteArray(size)
        // 复制原始字节数据（不依赖 ByteOrder）
        inputBuffer.get(data, 0, size)
        inputBuffer.position(position)

        // 通知所有监听器
        listeners.forEach { listener ->
            try {
                listener(data)
            } catch (_: Exception) {
                // 忽略单个监听器的异常
            }
        }
    }

    fun addListener(listener: (ByteArray) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (ByteArray) -> Unit) {
        listeners.remove(listener)
    }

    override fun getOutput(): Int = inputAudioFormat

    override fun queueEndOfStream() {
        // 不需要特殊处理
    }

    override fun flush() {
        // 不需要特殊处理
    }

    override fun reset() {
        listeners.clear()
        isActive = false
        inputAudioFormat = AudioFormat.NOT_SET
    }
}