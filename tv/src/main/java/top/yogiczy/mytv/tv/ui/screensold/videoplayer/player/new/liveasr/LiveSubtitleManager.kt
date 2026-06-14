package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import androidx.media3.common.text.Cue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 实时字幕管理器（状态机 + 错误恢复）
 *
 * 管理实时字幕功能的完整生命周期：
 * - 状态机：Idle → Initializing → Running → Error → Recovering → Running
 * - 错误恢复：引擎崩溃时自动重试（最多 maxRetries 次）
 * - 健康检查：定期检查引擎状态，异常时自动重启
 */
class LiveSubtitleManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    /** 管理器状态 */
    enum class State {
        IDLE,           // 未启动
        INITIALIZING,   // 正在初始化引擎
        RUNNING,        // 正常运行
        ERROR,          // 出错
        RECOVERING,     // 正在恢复
        STOPPED,        // 已停止
    }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _cues = MutableStateFlow<List<Cue>>(emptyList())
    val cues: StateFlow<List<Cue>> = _cues

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // 管道
    private var pipeline: SubtitlePipeline? = null

    // 错误恢复
    private var retryCount = 0
    private var healthCheckJob: Job? = null

    companion object {
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 5000L
        const val HEALTH_CHECK_INTERVAL_MS = 10000L
    }

    /**
     * 启动实时字幕
     */
    fun start() {
        if (_state.value == State.RUNNING || _state.value == State.INITIALIZING) return

        _state.value = State.INITIALIZING
        _errorMessage.value = null
        retryCount = 0

        startPipeline()
    }

    /**
     * 停止实时字幕
     */
    fun stop() {
        _state.value = State.STOPPED
        healthCheckJob?.cancel()
        pipeline?.stop()
        pipeline = null
        _cues.value = emptyList()
    }

    /**
     * 喂入音频数据
     */
    fun feedAudio(data: ByteArray, ptsUs: Long) {
        pipeline?.feedAudio(data, ptsUs)
    }

    /**
     * 切换实时字幕开关
     */
    fun toggle() {
        when (_state.value) {
            State.IDLE, State.STOPPED, State.ERROR -> start()
            State.RUNNING -> stop()
            else -> { /* INITIALIZING / RECOVERING 不操作 */ }
        }
    }

    fun isRunning(): Boolean = _state.value == State.RUNNING

    // ==================== 私有方法 ====================

    private fun startPipeline() {
        scope.launch(Dispatchers.IO) {
            try {
                val p = SubtitlePipeline(context, scope)
                pipeline = p

                // 观察管道的 cues 输出
                scope.launch {
                    p.cues.collect { newCues ->
                        _cues.value = newCues
                    }
                }

                p.start()
                _state.value = State.RUNNING
                retryCount = 0

                // 启动健康检查
                startHealthCheck()
            } catch (e: Exception) {
                LiveAsrLogger.e("LiveSubtitleManager: 启动失败", e)
                handleError(e)
            }
        }
    }

    private fun handleError(error: Throwable) {
        LiveAsrLogger.e("LiveSubtitleManager: 错误", error)
        _errorMessage.value = error.message ?: "未知错误"

        if (retryCount < MAX_RETRIES) {
            _state.value = State.RECOVERING
            retryCount++

            LiveAsrLogger.i("LiveSubtitleManager: 第${retryCount}次重试，${RETRY_DELAY_MS}ms 后执行")

            scope.launch(Dispatchers.IO) {
                delay(RETRY_DELAY_MS)

                if (_state.value == State.RECOVERING) {
                    // 清理旧管道
                    pipeline?.stop()
                    pipeline = null

                    // 重新启动
                    _state.value = State.INITIALIZING
                    startPipeline()
                }
            }
        } else {
            _state.value = State.ERROR
            LiveAsrLogger.e("LiveSubtitleManager: 超过最大重试次数($MAX_RETRIES)，停止恢复")
        }
    }

    private fun startHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch(Dispatchers.IO) {
            while (isActive && _state.value == State.RUNNING) {
                delay(HEALTH_CHECK_INTERVAL_MS)

                val p = pipeline
                if (p != null && !p.isRunning()) {
                    LiveAsrLogger.w("LiveSubtitleManager: 健康检查发现管道已停止，尝试恢复")
                    handleError(RuntimeException("管道意外停止"))
                }
            }
        }
    }
}
