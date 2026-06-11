package top.yogiczy.mytv.core.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

object HttpClient {

    private const val TAG = "HttpClient"

    enum class SslMode { SAFE, TRUST_ALL }

    private val clients = ConcurrentHashMap<SslMode, OkHttpClient>()
    private val epgClients = ConcurrentHashMap<SslMode, OkHttpClient>()

    private var _defaultSslMode: SslMode = SslMode.TRUST_ALL
    val defaultSslMode: SslMode get() = _defaultSslMode

    fun setDefaultSslMode(mode: SslMode) {
        _defaultSslMode = mode
        Log.i(TAG, "Default SSL mode set to: $mode")
    }

    fun getSharedClient(sslMode: SslMode = _defaultSslMode): OkHttpClient =
        clients.getOrPut(sslMode) { createClient(sslMode) }

    val sharedClient: OkHttpClient get() = getSharedClient(_defaultSslMode)

    val epgClient: OkHttpClient
        get() = epgClients.getOrPut(_defaultSslMode) { createClient(_defaultSslMode, epg = true) }

    private fun createClient(sslMode: SslMode, epg: Boolean = false): OkHttpClient {
        val trustAll = sslMode == SslMode.TRUST_ALL
        return OkHttpClient.Builder()
            .apply {
                if (trustAll) {
                    sslSocketFactory(
                        TrustAllSSLSocketFactory.getSSLSocketFactory(true),
                        TrustAllSSLSocketFactory.getTrustManager(true)
                    )
                    hostnameVerifier(TrustAllSSLSocketFactory.getHostnameVerifier(true))
                    Log.w(TAG, "Created ${if (epg) "EpgClient" else "OkHttpClient"} with SSL verification disabled")
                }
            }
            .addInterceptor(RetryInterceptor(maxRetry = 3))
            .connectTimeout(if (epg) 60L else 30L, TimeUnit.SECONDS)
            .readTimeout(if (epg) 120L else 60L, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) {
                response.body?.let { response.closeQuietly() }
            }
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })

    continuation.invokeOnCancellation {
        try { cancel() } catch (_: Throwable) {}
    }
}

suspend fun <T> String.request(
    builder: (Request.Builder) -> Request.Builder = { it },
    action: suspend (ResponseBody) -> T,
): T = requestWithClient(HttpClient.sharedClient, builder) { response, _ ->
    response.body?.let { action(it) } ?: throw IOException("Response body is null")
}

suspend fun <T> String.request(
    builder: (Request.Builder) -> Request.Builder = { it },
    action: suspend (Response, Request) -> T,
): T = requestWithClient(HttpClient.sharedClient, builder, action)

suspend fun <T> String.requestEpg(
    builder: (Request.Builder) -> Request.Builder = { it },
    action: suspend (Response, Request) -> T,
): T = requestWithClient(HttpClient.epgClient, builder, action)

private suspend fun <T> String.requestWithClient(
    client: OkHttpClient,
    builder: (Request.Builder) -> Request.Builder,
    action: suspend (Response, Request) -> T,
): T = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(this@requestWithClient).let(builder).build()
    val response = client.newCall(request).await()
    if (!response.isSuccessful) throw Exception("${response.code}: ${response.message}")
    action(response, response.request)
}
