package top.yogiczy.mytv.core.data.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

sealed class DataResult<out T> {
    data class Success<T>(val data: T) : DataResult<T>()
    data class Error(
        val exception: Exception,
        val message: String,
        val code: Int = -1
    ) : DataResult<Nothing>()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    
    fun getOrNull(): T? = (this as? Success)?.data
    fun exceptionOrNull(): Exception? = (this as? Error)?.exception
    
    inline fun <R> map(transform: (T) -> R): DataResult<R> {
        return when (this) {
            is Success -> Success(transform(data))
            is Error -> this
        }
    }
    
    inline fun onSuccess(action: (T) -> Unit): DataResult<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onError(action: (Error) -> Unit): DataResult<T> {
        if (this is Error) action(this)
        return this
    }
}

object DataResultHelper {
    
    suspend fun <T> safeCall(
        operation: String,
        log: Logger,
        block: suspend () -> T
    ): DataResult<T> {
        return try {
            DataResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e("$operation 失败", e)
            DataResult.Error(e, "$operation 失败: ${e.message}")
        }
    }
    
    suspend fun <T> safeCallWithRetry(
        operation: String,
        log: Logger,
        maxRetries: Int = 3,
        delayMs: Long = 1000,
        block: suspend () -> T
    ): DataResult<T> {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                return DataResult.Success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                log.w("$operation 失败 (尝试 ${attempt + 1}/$maxRetries)", e)
                if (attempt < maxRetries - 1) {
                    delay(delayMs)
                    coroutineContext.ensureActive()
                }
            }
        }
        
        return DataResult.Error(
            lastException ?: Exception("Unknown error"),
            "$operation 失败，已重试 $maxRetries 次"
        )
    }
}
