package top.yogiczy.mytv.core.data.network

import kotlinx.coroutines.CoroutineScope
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
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

object HttpClient {
    val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .sslSocketFactory(
                TrustAllSSLSocketFactory.sslSocketFactory,
                TrustAllSSLSocketFactory.trustManager
            )
            .hostnameVerifier { _, _ -> true }
            .followRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    val epgClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .sslSocketFactory(
                TrustAllSSLSocketFactory.sslSocketFactory,
                TrustAllSSLSocketFactory.trustManager
            )
            .hostnameVerifier { _, _ -> true }
            .followRedirects(true)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) {
                    if (response.body != null) {
                        response.closeQuietly()
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        }
    )

    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (t: Throwable) {
        }
    }
}

suspend fun <T> String.request(
    builder: (Request.Builder) -> Request.Builder = { it -> it },
    action: suspend CoroutineScope.(ResponseBody) -> T,
): T {
    return requestWithClient(HttpClient.sharedClient, builder) { response, _ ->
        response.body?.let { action(it) } ?: throw IOException("Response body is null")
    }
}

suspend fun <T> String.request(
    builder: (Request.Builder) -> Request.Builder = { it -> it },
    action: suspend CoroutineScope.(Response, Request) -> T,
): T {
    return requestWithClient(HttpClient.sharedClient, builder, action)
}

suspend fun <T> String.requestEpg(
    builder: (Request.Builder) -> Request.Builder = { it -> it },
    action: suspend CoroutineScope.(Response, Request) -> T,
): T {
    return requestWithClient(HttpClient.epgClient, builder, action)
}

private suspend fun <T> String.requestWithClient(
    client: OkHttpClient,
    builder: (Request.Builder) -> Request.Builder,
    action: suspend CoroutineScope.(Response, Request) -> T,
): T {
    val url = this
    return withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .let(builder)
            .build()

        val response = client.newCall(request).await()

        if (!response.isSuccessful) throw Exception("${response.code}: ${response.message}")

        action(response, response.request)
    }
}