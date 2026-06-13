package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Google Cloud Translation 云端翻译引擎
 *
 * 免费额度：50万字符/月
 * 使用 API Key 认证
 */
class GoogleCloudTranslateEngine : TranslateEngine {

    private var apiKey: String = ""
    private var targetLang: String = "zh"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun initialize(context: Context, config: TranslateConfig) {
        apiKey = config.apiKey
        targetLang = config.translateTarget
    }

    override suspend fun translate(text: String, sourceLanguage: String): String {
        if (text.isBlank() || apiKey.isBlank()) return text

        return try {
            val jsonBody = JSONObject().apply {
                put("q", text)
                put("target", targetLang)
            }

            val requestBody = jsonBody.toString().toRequestBody(
                "application/json".toMediaType()
            )

            val request = Request.Builder()
                .url("https://translation.googleapis.com/language/translate/v2?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return text
            val json = JSONObject(body)

            val translations = json
                .optJSONObject("data")
                ?.optJSONArray("translations")

            if (translations != null && translations.length() > 0) {
                translations.getJSONObject(0)
                    .optString("translatedText", text)
            } else {
                text
            }
        } catch (e: Exception) {
            text
        }
    }

    override suspend fun release() {
        // 无资源需要释放
    }
}