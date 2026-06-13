package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Azure Translator 云端翻译引擎
 *
 * 免费额度：200万字符/月
 * 使用 API Key + 区域 认证
 */
class AzureTranslateEngine : TranslateEngine {

    private var apiKey: String = ""
    private var region: String = "global"
    private var targetLang: String = "zh"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun initialize(context: Context, config: TranslateConfig) {
        apiKey = config.apiKey
        region = config.apiRegion.ifBlank { "global" }
        targetLang = config.translateTarget
    }

    override suspend fun translate(text: String, sourceLanguage: String): String {
        if (text.isBlank() || apiKey.isBlank()) return text

        return try {
            val jsonBody = JSONArray().apply {
                put(JSONObject().apply { put("Text", text) })
            }

            val requestBody = jsonBody.toString().toRequestBody(
                "application/json".toMediaType()
            )

            val url = "https://api.cognitive.microsofttranslator.com/translate" +
                "?api-version=3.0" +
                "&to=$targetLang"

            val request = Request.Builder()
                .url(url)
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .header("Ocp-Apim-Subscription-Region", region)
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return text
            val jsonArray = JSONArray(body)

            if (jsonArray.length() > 0) {
                val translations = jsonArray
                    .getJSONObject(0)
                    .optJSONArray("translations")
                if (translations != null && translations.length() > 0) {
                    translations.getJSONObject(0)
                        .optString("text", text)
                } else text
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