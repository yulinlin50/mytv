package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.media3.common.text.Cue
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 实时字幕核心调度器
 *
 * 负责：
 * 1. 音频数据缓冲
 * 2. 引擎选择（识别 + 翻译）
 * 3. 语言检测
 * 4. 最终字幕输出
 */
class LiveAsrProcessor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onCues: (List<Cue>) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val audioBuffer = mutableListOf<ByteArray>()
    private var asrEngine: AsrEngine? = null
    private var translateEngine: TranslateEngine? = null
    private var processJob: Job? = null
    private var languageId: com.google.mlkit.nl.languageid.LanguageIdentifier? = null

    // 缓冲参数
    private companion object {
        const val BUFFER_DURATION_MS = 500L          // 每 500ms 处理一次
        const val AUDIO_BUFFER_POOL_SIZE = 10         // 最多保留 10 段音频
    }

    fun start() {
        if (running.getAndSet(true)) return

        processJob = scope.launch(Dispatchers.IO) {
            try {
                // 初始化语言检测
                languageId = LanguageIdentification.getClient()

                // 初始化 ASR 引擎
                val asrConfig = buildAsrConfig()
                asrEngine = createAsrEngine(asrConfig)
                asrEngine?.initialize(context, asrConfig)

                // 初始化翻译引擎
                val translateConfig = buildTranslateConfig()
                translateEngine = createTranslateEngine(translateConfig)
                translateEngine?.initialize(context, translateConfig)

                // 处理音频循环
                while (isActive && running.get()) {
                    processAudioBuffer()
                    delay(BUFFER_DURATION_MS)
                }
            } catch (e: Throwable) {
                // 引擎初始化失败（包括 UnsatisfiedLinkError 等 Error），静默处理
                withContext(Dispatchers.Main) {
                    onCues(emptyList())
                }
            } finally {
                running.set(false)
                releaseEngines()
            }
        }
    }

    fun stop() {
        running.set(false)
        processJob?.cancel()
        audioBuffer.clear()
        releaseEngines()
    }

    fun isRunning(): Boolean = running.get()

    fun feedAudio(data: ByteArray) {
        if (!running.get()) return

        // 限制缓冲区大小
        if (audioBuffer.size >= AUDIO_BUFFER_POOL_SIZE) {
            audioBuffer.removeAt(0)
        }
        audioBuffer.add(data)
    }

    private suspend fun processAudioBuffer() {
        if (audioBuffer.isEmpty()) return

        // 合并缓冲区中的音频数据
        val totalSize = audioBuffer.sumOf { it.size }
        val merged = ByteArray(totalSize)
        var offset = 0
        for (data in audioBuffer) {
            System.arraycopy(data, 0, merged, offset, data.size)
            offset += data.size
        }
        audioBuffer.clear()

        // 语音识别
        val recognizedText = asrEngine?.recognize(merged)?.takeIf { it.isNotBlank() } ?: return

        // 语言检测
        val sourceLang = try {
            languageId?.let { lid ->
                Tasks.await(lid.identifyLanguage(recognizedText), 5, TimeUnit.SECONDS)
            } ?: "en"
        } catch (e: Exception) {
            "en"
        }

        // 翻译
        val translatedText = translateEngine?.let { engine ->
            try {
                engine.translate(recognizedText, sourceLang)
            } catch (e: Exception) {
                "$recognizedText\n[$sourceLang]"
            }
        } ?: recognizedText

        // 生成字幕 Cue（应用样式配置，位置由 Compose 层 SubtitleView 控制）
        val styledText = applySubtitleStyle(translatedText)

        val cue = Cue.Builder()
            .setText(styledText)
            .build()

        withContext(Dispatchers.Main) {
            onCues(listOf(cue))
        }
    }

    private fun buildAsrConfig(): AsrConfig {
        return AsrConfig(
            provider = resolveAsrProvider(),
            apiKey = Configs.subtitleLiveAsrApiKey,
            apiRegion = Configs.subtitleLiveAsrRegion,
            whisperModel = Configs.subtitleLiveWhisperModel,
        )
    }

    private fun buildTranslateConfig(): TranslateConfig {
        return TranslateConfig(
            provider = resolveTranslateProvider(),
            apiKey = Configs.subtitleLiveTranslateApiKey,
            apiRegion = Configs.subtitleLiveTranslateRegion,
            translateTarget = Configs.subtitleLiveTranslateTarget,
        )
    }

    /**
     * 解析识别引擎：云端 API Key 为空时回退 Vosk
     */
    private fun resolveAsrProvider(): String {
        val provider = Configs.subtitleLiveAsrProvider
        return when (provider) {
            "azure", "baidu", "google" -> {
                if (Configs.subtitleLiveAsrApiKey.isBlank()) "vosk" else provider
            }
            "whisper" -> "vosk" // Whisper 暂未实现，回退 Vosk
            else -> provider
        }
    }

    /**
     * 解析翻译引擎：云端 API Key 为空时回退 ML Kit
     */
    private fun resolveTranslateProvider(): String {
        val provider = Configs.subtitleLiveTranslateProvider
        return when (provider) {
            "google", "azure", "baidu", "deepl" -> {
                if (Configs.subtitleLiveTranslateApiKey.isBlank()) "mlkit" else provider
            }
            else -> "mlkit"
        }
    }

    private fun createAsrEngine(config: AsrConfig): AsrEngine? {
        return when (config.provider) {
            "vosk" -> VoskAsrEngine()
            "azure" -> AzureAsrEngine()
            "baidu" -> BaiduAsrEngine()
            "google" -> GoogleAsrEngine()
            else -> VoskAsrEngine()
        }
    }

    private fun createTranslateEngine(config: TranslateConfig): TranslateEngine? {
        return when (config.provider) {
            "mlkit" -> MlKitTranslateEngine()
            "google" -> GoogleCloudTranslateEngine()
            "azure" -> AzureTranslateEngine()
            "baidu" -> BaiduTranslateEngine()
            "deepl" -> DeepLTranslateEngine()
            else -> MlKitTranslateEngine()
        }
    }

    private fun releaseEngines() {
        scope.launch(Dispatchers.IO) {
            asrEngine?.release()
            translateEngine?.release()
            languageId?.close()
            asrEngine = null
            translateEngine = null
            languageId = null
        }
    }

    /**
     * 应用字幕样式：文字颜色 + 背景颜色
     */
    private fun applySubtitleStyle(text: String): SpannableString {
        val spannable = SpannableString(text)

        // 文字颜色
        val textColor = when (Configs.subtitleLiveTextColor) {
            "yellow" -> Color.YELLOW
            "green" -> Color.GREEN
            "cyan" -> Color.CYAN
            else -> Color.WHITE
        }
        spannable.setSpan(
            ForegroundColorSpan(textColor),
            0, text.length,
            SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // 背景颜色
        val bgColor = when (Configs.subtitleLiveBgColor) {
            "black" -> Color.BLACK
            "semi-transparent" -> Color.argb(180, 0, 0, 0)
            "none" -> null
            else -> Color.argb(180, 0, 0, 0)
        }
        if (bgColor != null) {
            spannable.setSpan(
                BackgroundColorSpan(bgColor),
                0, text.length,
                SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannable
    }
}