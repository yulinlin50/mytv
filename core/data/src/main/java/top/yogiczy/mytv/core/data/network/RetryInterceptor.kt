package top.yogiczy.mytv.core.data.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RetryInterceptor(
    private val maxRetry: Int = 3,
    private val retryOnErrors: Set<Int> = setOf(500, 502, 503, 504, 429)
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response? = null
        var exception: Exception? = null
        
        repeat(maxRetry + 1) { attempt ->
            try {
                response?.close()
                val resp = chain.proceed(request)
                response = resp
                
                if (resp.isSuccessful || resp.code !in retryOnErrors) {
                    return resp
                }
                
                Log.w("RetryInterceptor", "请求失败 (${resp.code})，准备重试 (${attempt + 1}/$maxRetry)")
                
            } catch (e: Exception) {
                exception = e
                Log.w("RetryInterceptor", "请求异常: ${e.message}，准备重试 (${attempt + 1}/$maxRetry)")
            }
            
            if (attempt < maxRetry) {
                try {
                    Thread.sleep(calculateDelay(attempt))
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Retry interrupted", ie)
                }
            }
        }
        
        return response ?: throw exception ?: IOException("Unknown error after $maxRetry retries")
    }
    
    private fun calculateDelay(attempt: Int): Long {
        return minOf(1000L * (1 shl attempt), 10_000L)
    }
}
