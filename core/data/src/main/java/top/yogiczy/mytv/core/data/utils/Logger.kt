package top.yogiczy.mytv.core.data.utils

import android.util.Log
import kotlinx.serialization.Serializable
import kotlin.time.Duration

class Logger private constructor(private val tag: String) {

    private fun log(level: LevelType, message: String, throwable: Throwable? = null, duration: Duration? = null) {
        val msg = if (duration != null) "$message \t [$duration]" else message
        when (level) {
            LevelType.DEBUG -> Log.d(tag, msg, throwable)
            LevelType.INFO -> Log.i(tag, msg, throwable)
            LevelType.WARN -> Log.w(tag, msg, throwable)
            LevelType.ERROR -> Log.e(tag, msg, throwable)
        }
        addHistoryItem(HistoryItem(level, tag, msg, throwable?.message))
    }

    fun v(message: String, throwable: Throwable? = null, duration: Duration? = null) = log(LevelType.DEBUG, message, throwable, duration)
    fun d(message: String, throwable: Throwable? = null, duration: Duration? = null) = log(LevelType.DEBUG, message, throwable, duration)
    fun i(message: String, throwable: Throwable? = null, duration: Duration? = null) = log(LevelType.INFO, message, throwable, duration)
    fun w(message: String, throwable: Throwable? = null, duration: Duration? = null) = log(LevelType.WARN, message, throwable, duration)
    fun e(message: String, throwable: Throwable? = null, duration: Duration? = null) = log(LevelType.ERROR, message, throwable, duration)
    fun wtf(message: String, throwable: Throwable? = null, duration: Duration? = null) = log(LevelType.ERROR, message, throwable, duration)

    companion object {
        fun create(tag: String) = Logger(tag)

        private var _history = mutableListOf<HistoryItem>()
        val history: List<HistoryItem> get() = _history

        fun addHistoryItem(item: HistoryItem) {
            if (item.level in listOf(LevelType.INFO, LevelType.WARN, LevelType.ERROR)) {
                _history.add(item)
                if (_history.size > Constants.LOG_HISTORY_MAX_SIZE) {
                    _history = _history.takeLast(Constants.LOG_HISTORY_MAX_SIZE).toMutableList()
                }
            }
        }
    }

    enum class LevelType { DEBUG, INFO, WARN, ERROR }

    @Serializable
    data class HistoryItem(
        val level: LevelType,
        val tag: String,
        val message: String,
        val cause: String? = null,
        val time: Long = System.currentTimeMillis(),
    )
}

abstract class Loggable(private val tag: String) {
    protected val log: Logger get() = Logger.create(tag)
}
