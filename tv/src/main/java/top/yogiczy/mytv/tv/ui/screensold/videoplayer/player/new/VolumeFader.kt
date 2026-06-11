package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.ui.utils.Configs
import kotlin.math.abs

class VolumeFader(
    private val coroutineScope: CoroutineScope,
    private val setVolumeActual: (Float) -> Unit
) {
    private val log = Logger.create("VolumeFader")

    private var fadeJob: Job? = null
    private var currentVolume: Float = 1f

    fun fadeTo(
        targetVolume: Float,
        durationMs: Int? = null,
        force: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        val target = targetVolume.coerceIn(0f, 1f)
        val duration = durationMs ?: Configs.videoPlayerVolumeFadeDurationMs

        if (!Configs.videoPlayerEnableVolumeFade && !force) {
            setVolumeActual(target)
            currentVolume = target
            onComplete?.invoke()
            return
        }

        if (abs(currentVolume - target) < VOLUME_THRESHOLD) {
            setVolumeActual(target)
            currentVolume = target
            onComplete?.invoke()
            return
        }

        fadeJob?.cancel()
        fadeJob = coroutineScope.launch(Dispatchers.Main) {
            val startVolume = currentVolume
            val volumeDiff = target - startVolume
            val steps = (duration / FADE_STEP_INTERVAL_MS).coerceAtLeast(1)
            val volumeStep = volumeDiff / steps

            repeat(steps) { step ->
                val newVolume = (startVolume + volumeStep * (step + 1)).coerceIn(0f, 1f)
                setVolumeActual(newVolume)
                currentVolume = newVolume
                delay(FADE_STEP_INTERVAL_MS.toLong())
            }

            setVolumeActual(target)
            currentVolume = target
            onComplete?.invoke()
        }
    }

    fun fadeOut(durationMs: Int? = null, force: Boolean = false, onComplete: (() -> Unit)? = null) {
        fadeTo(0f, durationMs, force, onComplete)
    }

    fun fadeIn(targetVolume: Float = 1f, durationMs: Int? = null, force: Boolean = false) {
        fadeTo(targetVolume, durationMs, force)
    }

    fun setVolumeImmediate(volume: Float) {
        fadeJob?.cancel()
        val target = volume.coerceIn(0f, 1f)
        setVolumeActual(target)
        currentVolume = target
    }

    fun cancel() {
        fadeJob?.cancel()
        fadeJob = null
    }

    fun getCurrentVolume(): Float = currentVolume

    fun syncCurrentVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
    }

    companion object {
        private const val FADE_STEP_INTERVAL_MS = 10
        private const val VOLUME_THRESHOLD = 0.01f
    }
}
