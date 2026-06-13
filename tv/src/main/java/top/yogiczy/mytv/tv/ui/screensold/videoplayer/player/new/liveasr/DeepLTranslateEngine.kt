package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * DeepL 云端翻译引擎
 *
 * 免费额度：50万字符/月
 * 使用 API Key 认证
 * 对欧洲语言翻译质量特别高
 */
class DeepLTranslateEngine : TranslateEngine {

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
            val url = "https://api-free.deepl.com/v2/translate"

            val formBody = okhttp3.FormBody.Builder()
                .add("text", text)
                .add("target_lang", mapTargetLang())
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "DeepL-Auth-Key $apiKey")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return text
            val json = JSONObject(body)

            val translations = json.optJSONArray("translations")
            if (translations != null && translations.length() > 0) {
                translations.getJSONObject(0)
                    .optString("text", text)
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

    private fun mapTargetLang(): String = when (targetLang.uppercase()) {
        "ZH" -> "ZH"
        "EN" -> "EN-US"
        "JA" -> "JA"
        "KO" -> "KO"
        "FR" -> "FR"
        "DE" -> "DE"
        "ES" -> "ES"
        "PT" -> "PT-PT"
        "RU" -> "RU"
        "IT" -> "IT"
        else -> "ZH"
    }
}