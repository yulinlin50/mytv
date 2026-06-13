package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Random
import java.util.concurrent.TimeUnit

/**
 * 百度翻译 云端翻译引擎
 *
 * 免费额度：200万字符/月
 * API Key 格式：APP ID:Secret Key（用冒号分隔）
 * 使用 MD5 签名认证
 */
class BaiduTranslateEngine : TranslateEngine {

    private var appId: String = ""
    private var secretKey: String = ""
    private var targetLang: String = "zh"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun initialize(context: Context, config: TranslateConfig) {
        val parts = config.apiKey.split(":")
        appId = parts.getOrElse(0) { "" }
        secretKey = parts.getOrElse(1) { "" }
        targetLang = config.translateTarget
    }

    override suspend fun translate(text: String, sourceLanguage: String): String {
        if (text.isBlank() || appId.isBlank() || secretKey.isBlank()) return text

        return try {
            val salt = Random().nextInt(100000).toString()
            val sign = md5(appId + text + salt + secretKey)

            val url = "https://fanyi-api.baidu.com/api/trans/vip/translate?" +
                "q=${java.net.URLEncoder.encode(text, "UTF-8")}" +
                "&from=auto" +
                "&to=${mapTargetLang()}" +
                "&appid=$appId" +
                "&salt=$salt" +
                "&sign=$sign"

            val requestBody = "".toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return text
            val json = JSONObject(body)

            val transResult = json.optJSONArray("trans_result")
            if (transResult != null && transResult.length() > 0) {
                transResult.getJSONObject(0)
                    .optString("dst", text)
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

    private fun mapTargetLang(): String = when (targetLang) {
        "zh" -> "zh"
        "en" -> "en"
        "ja" -> "jp"
        "ko" -> "kor"
        "fr" -> "fra"
        "de" -> "de"
        "es" -> "spa"
        "pt" -> "pt"
        "ru" -> "ru"
        "ar" -> "ara"
        "th" -> "th"
        "vi" -> "vie"
        else -> "zh"
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}