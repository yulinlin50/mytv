package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.ui.utils.Configs
import kotlin.math.abs

/**
 * 音量淡入淡出工具类
 * 
 * 使用协程实现音量渐变效果，支持：
 * - 用户手动调节音量时的淡入淡出
 * - 切换频道时的淡入淡出
 * - 播放开始/暂停时的淡入淡出
 * 
 * @param coroutineScope 协程作用域
 * @param setVolumeActual 实际设置音量的回调函数
 */
class VolumeFader(
    private val coroutineScope: CoroutineScope,
    private val setVolumeActual: (Float) -> Unit
) {
    private val log = Logger.create("VolumeFader")
    
    private var fadeJob: Job? = null
    private var currentVolume: Float = 1f
    
    /**
     * 渐变到目标音量
     * 
     * @param targetVolume 目标音量 (0.0 - 1.0)
     * @param durationMs 渐变持续时间（毫秒），如果为null则使用配置值
     * @param force 是否强制渐变（忽略配置中的启用状态）
     */
    fun fadeTo(
        targetVolume: Float,
        durationMs: Int? = null,
        force: Boolean = false
    ) {
        val target = targetVolume.coerceIn(0f, 1f)
        val duration = durationMs ?: Configs.videoPlayerVolumeFadeDurationMs
        
        if (!Configs.videoPlayerEnableVolumeFade && !force) {
            setVolumeActual(target)
            currentVolume = target
            return
        }
        
        if (abs(currentVolume - target) < VOLUME_THRESHOLD) {
            setVolumeActual(target)
            currentVolume = target
            return
        }
        
        fadeJob?.cancel()
        fadeJob = coroutineScope.launch(Dispatchers.Main) {
            val startVolume = currentVolume
            val volumeDiff = target - startVolume
            val steps = (duration / FADE_STEP_INTERVAL_MS).coerceAtLeast(1)
            val volumeStep = volumeDiff / steps
            
            log.d("Fading volume from $startVolume to $target over ${steps * FADE_STEP_INTERVAL_MS}ms")
            
            repeat(steps) { step ->
                val newVolume = (startVolume + volumeStep * (step + 1))
                    .coerceIn(0f, 1f)
                
                setVolumeActual(newVolume)
                currentVolume = newVolume
                
                delay(FADE_STEP_INTERVAL_MS.toLong())
            }
            
            setVolumeActual(target)
            currentVolume = target
        }
    }
    
    /**
     * 淡出音量到静音
     * 
     * @param durationMs 渐变持续时间（毫秒），如果为null则使用配置值
     * @param force 是否强制渐变（忽略配置中的启用状态）
     */
    fun fadeOut(durationMs: Int? = null, force: Boolean = false) {
        fadeTo(0f, durationMs, force)
    }
    
    /**
     * 淡入音量到指定值
     * 
     * @param targetVolume 目标音量 (0.0 - 1.0)，默认为1.0
     * @param durationMs 渐变持续时间（毫秒），如果为null则使用配置值
     * @param force 是否强制渐变（忽略配置中的启用状态）
     */
    fun fadeIn(targetVolume: Float = 1f, durationMs: Int? = null, force: Boolean = false) {
        fadeTo(targetVolume, durationMs, force)
    }
    
    /**
     * 立即设置音量（无渐变）
     * 
     * @param volume 目标音量 (0.0 - 1.0)
     */
    fun setVolumeImmediate(volume: Float) {
        fadeJob?.cancel()
        val target = volume.coerceIn(0f, 1f)
        setVolumeActual(target)
        currentVolume = target
    }
    
    /**
     * 取消正在进行的渐变动画
     */
    fun cancel() {
        fadeJob?.cancel()
        fadeJob = null
    }
    
    /**
     * 获取当前音量
     */
    fun getCurrentVolume(): Float = currentVolume
    
    companion object {
        private const val FADE_STEP_INTERVAL_MS = 10
        private const val VOLUME_THRESHOLD = 0.01f
    }
}
