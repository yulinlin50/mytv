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
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    fun setActive(active: Boolean) {
        isActive = active
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (isActive && listeners.isNotEmpty()) {
            val position = inputBuffer.position()
            val limit = inputBuffer.limit()
            val size = limit - position
            if (size > 0) {
                val data = ByteArray(size)
                inputBuffer.duplicate().get(data, 0, size)
                listeners.forEach { listener ->
                    try {
                        listener(data)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        // Pass through: store input for output
        outputBuffer = inputBuffer
    }

    fun addListener(listener: (ByteArray) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (ByteArray) -> Unit) {
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
        isActive = false
        inputAudioFormat = AudioFormat.NOT_SET
        outputBuffer = AudioProcessor.EMPTY_BUFFER
    }
}