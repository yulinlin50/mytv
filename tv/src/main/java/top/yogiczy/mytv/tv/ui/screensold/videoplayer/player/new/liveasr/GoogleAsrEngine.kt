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
 * Google Speech-to-Text 云端语音识别引擎
 *
 * 免费额度：60分钟/月
 * 使用 API Key 认证
 */
class GoogleAsrEngine : AsrEngine {

    private var apiKey: String = ""
    private var running = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun initialize(context: Context, config: AsrConfig) {
        apiKey = config.apiKey
        running = apiKey.isNotBlank()
    }

    override suspend fun recognize(pcmData: ByteArray): String? {
        if (!running) return null

        return try {
            val audioBase64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)

            val jsonBody = JSONObject().apply {
                put("config", JSONObject().apply {
                    put("encoding", "LINEAR16")
                    put("sampleRateHertz", 16000)
                    put("languageCode", "en-US")
                    put("model", "default")
                })
                put("audio", JSONObject().apply {
                    put("content", audioBase64)
                })
            }

            val requestBody = jsonBody.toString().toRequestBody(
                "application/json".toMediaType()
            )

            val request = Request.Builder()
                .url("https://speech.googleapis.com/v1/speech:recognize?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val results = json.optJSONArray("results")
            if (results != null && results.length() > 0) {
                val firstResult = results.getJSONObject(0)
                val alternatives = firstResult.optJSONArray("alternatives")
                if (alternatives != null && alternatives.length() > 0) {
                    alternatives.getJSONObject(0)
                        .optString("transcript", "")
                        .takeIf { it.isNotBlank() }
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
    }

    override fun isRunning(): Boolean = running
}