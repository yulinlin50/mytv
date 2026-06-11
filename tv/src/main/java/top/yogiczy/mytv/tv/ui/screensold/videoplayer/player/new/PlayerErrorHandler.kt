package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import androidx.media3.common.PlaybackException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.utils.Logger

enum class PlayerErrorType(val isRecoverable: Boolean) {
    NETWORK(true),
    DECODER(true),
    FORMAT(true),
    DRM(false),
    TIMEOUT(true),
    PLAYBACK(false),
    UNKNOWN(false);

    fun getUserFriendlyMessage(detail: String): String = when (this) {
        NETWORK -> "网络连接失败，请检查网络设置"
        DECODER -> "视频解码失败，正在尝试其他方式"
        FORMAT -> "不支持的视频格式"
        DRM -> "版权保护验证失败"
        TIMEOUT -> "加载超时，请稍后重试"
        PLAYBACK -> "回放内容已过期"
        UNKNOWN -> "播放出错：$detail"
    }
}

interface RecoveryCallback {
    fun onRetry(retryCount: Int, maxRetries: Int)
    fun onPlayerSwitch()
    fun onRecoveryFailed()
}

class PlayerErrorHandler(
    private val stateManager: VideoPlayerStateManager,
    private val coroutineScope: CoroutineScope
) {
    private val log = Logger.create("PlayerErrorHandler")

    private var retryCount = 0
    private var recoveryJob: Job? = null
    private var recoveryCallback: RecoveryCallback? = null

    companion object {
        private const val MAX_RETRY_COUNT = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 4000L
    }

    fun setRecoveryCallback(callback: RecoveryCallback) {
        this.recoveryCallback = callback
    }

    fun resetRetryCount() {
        retryCount = 0
        recoveryJob?.cancel()
        recoveryJob = null
    }

    fun handleMedia3Error(ex: PlaybackException) {
        val errorType = when (ex.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> PlayerErrorType.PLAYBACK
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> PlayerErrorType.DECODER
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> PlayerErrorType.FORMAT
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> PlayerErrorType.NETWORK
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlayerErrorType.TIMEOUT
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> PlayerErrorType.FORMAT
            PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED,
            PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR -> PlayerErrorType.DRM
            else -> PlayerErrorType.UNKNOWN
        }
        handleError(errorType, ex.message ?: "Unknown error")
    }

    fun handleIjkError(what: Int, extra: Int) {
        val errorType = when (what) {
            -10000 -> PlayerErrorType.NETWORK
            -20000 -> PlayerErrorType.DECODER
            else -> PlayerErrorType.UNKNOWN
        }
        handleError(errorType, "IJK error (what: $what, extra: $extra)")
    }

    fun handleTimeoutError() {
        handleError(PlayerErrorType.TIMEOUT, "Load timeout")
    }

    fun handlePlaybackBehindLiveWindow() {
        handleError(PlayerErrorType.PLAYBACK, "Playback behind live window")
    }

    internal fun handleError(errorType: PlayerErrorType, detail: String) {
        stateManager.updateError(errorType.getUserFriendlyMessage(detail))
        log.e("Error: $detail, Type: $errorType")

        if (errorType.isRecoverable) {
            attemptRecovery(errorType)
        }
    }

    private fun attemptRecovery(errorType: PlayerErrorType) {
        if (retryCount >= MAX_RETRY_COUNT) {
            log.w("Max retry count reached, recovery failed")
            recoveryCallback?.onRecoveryFailed()
            return
        }

        recoveryJob?.cancel()
        recoveryJob = coroutineScope.launch(Dispatchers.Main) {
            val delayMs = calculateRetryDelay()
            log.i("Attempting recovery in ${delayMs}ms (attempt ${retryCount + 1}/$MAX_RETRY_COUNT)")

            delay(delayMs)

            retryCount++
            recoveryCallback?.onRetry(retryCount, MAX_RETRY_COUNT)

            when (errorType) {
                PlayerErrorType.NETWORK, PlayerErrorType.TIMEOUT -> {
                    log.i("Retrying for network/timeout error")
                }
                PlayerErrorType.DECODER, PlayerErrorType.FORMAT -> {
                    log.i("Switching player for decoder/format error")
                    recoveryCallback?.onPlayerSwitch()
                }
                else -> {
                    log.w("Unknown recoverable error type: $errorType")
                }
            }
        }
    }

    private fun calculateRetryDelay(): Long {
        val exponentialDelay = INITIAL_RETRY_DELAY_MS * (1 shl retryCount)
        return minOf(exponentialDelay, MAX_RETRY_DELAY_MS)
    }
}
