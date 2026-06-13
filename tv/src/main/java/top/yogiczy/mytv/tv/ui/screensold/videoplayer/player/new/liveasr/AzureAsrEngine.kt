package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Azure Speech 云端语音识别引擎
 *
 * 免费额度：5小时/月
 * 配置：API Key + 区域（如 eastasia, japaneast）
 */
class AzureAsrEngine : AsrEngine {

    private var apiKey: String = ""
    private var region: String = ""
    private var running = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun initialize(context: Context, config: AsrConfig) {
        apiKey = config.apiKey
        region = config.apiRegion
        running = apiKey.isNotBlank() && region.isNotBlank()
    }

    override suspend fun recognize(pcmData: ByteArray): String? {
        if (!running) return null

        return try {
            val wavData = pcmToWav(pcmData, 16000, 16, 1)
            val url = "https://${region}.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=en-US"

            val requestBody = wavData.toRequestBody(
                "audio/wav; codecs=audio/pcm; samplerate=16000".toMediaType()
            )

            val request = Request.Builder()
                .url(url)
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .header("Accept", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            if (json.optString("RecognitionStatus") == "Success") {
                json.optString("DisplayText", "").takeIf { it.isNotBlank() }
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

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int, bitsPerSample: Int, channels: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = 44 + dataSize

        val output = ByteArrayOutputStream(totalSize)

        output.write("RIFF".toByteArray())
        output.write(intToLeBytes(totalSize - 8), 0, 4)
        output.write("WAVE".toByteArray())

        output.write("fmt ".toByteArray())
        output.write(intToLeBytes(16), 0, 4)
        output.write(shortToLeBytes(1), 0, 2)
        output.write(shortToLeBytes(channels), 0, 2)
        output.write(intToLeBytes(sampleRate), 0, 4)
        output.write(intToLeBytes(byteRate), 0, 4)
        output.write(shortToLeBytes(blockAlign), 0, 2)
        output.write(shortToLeBytes(bitsPerSample), 0, 2)

        output.write("data".toByteArray())
        output.write(intToLeBytes(dataSize), 0, 4)
        output.write(pcmData, 0, pcmData.size)

        return output.toByteArray()
    }

    private fun intToLeBytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        (value shr 8 and 0xff).toByte(),
        (value shr 16 and 0xff).toByte(),
        (value shr 24 and 0xff).toByte()
    )

    private fun shortToLeBytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        (value shr 8 and 0xff).toByte()
    )
}