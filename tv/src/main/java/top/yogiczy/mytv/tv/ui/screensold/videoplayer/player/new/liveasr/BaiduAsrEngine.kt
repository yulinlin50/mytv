package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 百度语音 云端语音识别引擎
 *
 * 免费额度：5万次/天
 * API Key 格式：API Key:Secret Key（用冒号分隔）
 * 需要自动获取 access_token
 */
class BaiduAsrEngine : AsrEngine {

    private var apiKey: String = ""
    private var secretKey: String = ""
    private var language: String = "en"
    private var running = false
    private var accessToken: String? = null
    private var tokenExpireTime: Long = 0L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun initialize(context: Context, config: AsrConfig) {
        val parts = config.apiKey.split(":")
        apiKey = parts.getOrElse(0) { "" }
        secretKey = parts.getOrElse(1) { "" }
        language = config.language
        running = apiKey.isNotBlank() && secretKey.isNotBlank()

        if (running) {
            accessToken = fetchAccessToken()
            running = accessToken != null
        }
    }

    override suspend fun recognize(pcmData: ByteArray): String? {
        if (!running) return null

        val token = ensureAccessToken() ?: return null

        return try {
            val speechBase64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)
            val jsonBody = JSONObject().apply {
                put("format", "pcm")
                put("rate", 16000)
                put("channel", 1)
                put("cuid", "mytv-android")
                put("token", token)
                put("speech", speechBase64)
                put("len", pcmData.size)
                put("dev_pid", mapDevPid())
            }

            val requestBody = jsonBody.toString().toRequestBody(
                "application/json".toMediaType()
            )

            val request = Request.Builder()
                .url("https://vop.baidu.com/server_api")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            if (json.optInt("err_no") == 0) {
                val result = json.optJSONArray("result")
                if (result != null && result.length() > 0) {
                    result.getString(0).takeIf { it.isNotBlank() }
                } else null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun release() {
        running = false
        accessToken = null
        tokenExpireTime = 0L
    }

    override fun isRunning(): Boolean = running

    /** 根据语言代码映射百度 ASR 的 dev_pid */
    private fun mapDevPid(): Int = when (language.lowercase()) {
        "zh", "zh-cn", "cmn" -> 1537  // 中文普通话(有标点)
        "en" -> 1737                   // 英语(有标点)
        "ja" -> 1936                   // 日语
        "ko" -> 1936                   // 韩语
        "yue", "zh-yue" -> 1637       // 粤语
        else -> 1737                   // 默认英语
    }

    /**
     * 确保有效的 access_token，过期前 60 秒自动刷新
     */
    private fun ensureAccessToken(): String? {
        val now = System.currentTimeMillis()
        if (accessToken != null && now < tokenExpireTime - 60_000L) {
            return accessToken
        }
        return fetchAccessToken()?.also { accessToken = it }
    }

    private fun fetchAccessToken(): String? {
        return try {
            val url = "https://aip.baidubce.com/oauth/2.0/token?" +
                "grant_type=client_credentials&" +
                "client_id=$apiKey&" +
                "client_secret=$secretKey"

            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val token = json.optString("access_token", null)
            if (token != null) {
                val expiresIn = json.optLong("expires_in", 2592000L)
                tokenExpireTime = System.currentTimeMillis() + expiresIn * 1000L
            }
            token
        } catch (e: Exception) {
            null
        }
    }
}