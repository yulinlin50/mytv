package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import androidx.media3.common.PlaybackException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.utils.Logger

sealed class PlayerErrorType {
    abstract val errorCode: Int
    abstract val message: String
    
    data class NetworkError(
        override val errorCode: Int,
        override val message: String
    ) : PlayerErrorType()
    
    data class DecoderError(
        override val errorCode: Int,
        override val message: String
    ) : PlayerErrorType()
    
    data class FormatError(
        override val errorCode: Int,
        override val message: String
    ) : PlayerErrorType()
    
    data class DrmError(
        override val errorCode: Int,
        override val message: String
    ) : PlayerErrorType()
    
    data class TimeoutError(
        override val errorCode: Int,
        override val message: String
    ) : PlayerErrorType()
    
    data class PlaybackError(
        override val errorCode: Int,
        override val message: String
    ) : PlayerErrorType()
    
    data class UnknownError(
        override val errorCode: Int,
        override val message: String
    ) : PlayerErrorType()
    
    fun getUserFriendlyMessage(): String {
        return when (this) {
            is NetworkError -> "网络连接失败，请检查网络设置"
            is DecoderError -> "视频解码失败，正在尝试其他方式"
            is FormatError -> "不支持的视频格式"
            is DrmError -> "版权保护验证失败"
            is TimeoutError -> "加载超时，请稍后重试"
            is PlaybackError -> "回放内容已过期"
            is UnknownError -> "播放出错：$message"
        }
    }
    
    fun isRecoverable(): Boolean {
        return this is NetworkError || 
               this is TimeoutError || 
               this is DecoderError || 
               this is FormatError
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
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                PlayerErrorType.PlaybackError(
                    errorCode = ex.errorCode,
                    message = ex.message ?: "Behind live window"
                )
            }
            
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                PlayerErrorType.DecoderError(
                    errorCode = ex.errorCode,
                    message = ex.message ?: "Decoder failed"
                )
            }
            
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                PlayerErrorType.FormatError(
                    errorCode = ex.errorCode,
                    message = ex.message ?: "Source error"
                )
            }
            
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
                PlayerErrorType.NetworkError(
                    errorCode = ex.errorCode,
                    message = ex.message ?: "Network error"
                )
            }
            
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                PlayerErrorType.TimeoutError(
                    errorCode = ex.errorCode,
                    message = ex.message ?: "Connection timeout"
                )
            }
            
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> {
                PlayerErrorType.FormatError(
                    errorCode = ex.errorCode,
                    message = ex.message ?: "Unsupported format"
                )
            }
            
            PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED,
            PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR -> {
                PlayerErrorType.DrmError(
                    errorCode = ex.errorCode,
                    message = ex.message ?: "DRM error"
                )
            }
            
            else -> {
                PlayerErrorType.UnknownError(
                    errorCode = ex.errorCode,
                    message = ex.message ?: "Unknown error"
                )
            }
        }
        
        handleError(errorType)
    }
    
    fun handleIjkError(what: Int, extra: Int) {
        val errorType = when (what) {
            -10000 -> PlayerErrorType.NetworkError(
                errorCode = what,
                message = "Network error (extra: $extra)"
            )
            -20000 -> PlayerErrorType.DecoderError(
                errorCode = what,
                message = "Decoder error (extra: $extra)"
            )
            else -> PlayerErrorType.UnknownError(
                errorCode = what,
                message = "IJK error (what: $what, extra: $extra)"
            )
        }
        
        handleError(errorType)
    }
    
    fun handleTimeoutError() {
        handleError(
            PlayerErrorType.TimeoutError(
                errorCode = 10003,
                message = "Load timeout"
            )
        )
    }
    
    fun handlePlaybackBehindLiveWindow() {
        handleError(
            PlayerErrorType.PlaybackError(
                errorCode = 10004,
                message = "Playback behind live window"
            )
        )
    }
    
    internal fun handleError(errorType: PlayerErrorType) {
        stateManager.updateError(errorType.getUserFriendlyMessage())
        reportError(errorType)
        
        if (errorType.isRecoverable()) {
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
                is PlayerErrorType.NetworkError,
                is PlayerErrorType.TimeoutError -> {
                    log.i("Retrying for network/timeout error")
                }
                is PlayerErrorType.DecoderError,
                is PlayerErrorType.FormatError -> {
                    log.i("Switching player for decoder/format error")
                    recoveryCallback?.onPlayerSwitch()
                }
                else -> {
                    log.w("Unknown recoverable error type: ${errorType::class.simpleName}")
                }
            }
        }
    }
    
    private fun calculateRetryDelay(): Long {
        val exponentialDelay = INITIAL_RETRY_DELAY_MS * (1 shl retryCount)
        return minOf(exponentialDelay, MAX_RETRY_DELAY_MS)
    }
    
    private fun reportError(errorType: PlayerErrorType) {
        coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                log.e("Error: ${errorType.message}, Code: ${errorType.errorCode}")
            }
        }
    }
}
