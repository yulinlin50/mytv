package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
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
        const val BUFFER_DURATION_MS = 2000L         // 每 2 秒处理一次（Whisper 需要足够长的音频）
        const val AUDIO_BUFFER_POOL_SIZE = 120       // 最多保留约 2.5 秒音频（21ms/段 × 120）
    }

    fun start() {
        if (running.getAndSet(true)) return

        LiveAsrLogger.init(context)
        LiveAsrLogger.i("LiveAsrProcessor: start()")

        processJob = scope.launch(Dispatchers.IO) {
            try {
                // 初始化语言检测
                languageId = LanguageIdentification.getClient()
                LiveAsrLogger.i("LiveAsrProcessor: 语言检测初始化完成")

                // 初始化 ASR 引擎
                val asrConfig = buildAsrConfig()
                LiveAsrLogger.i("LiveAsrProcessor: ASR引擎=${asrConfig.provider}, 开始初始化...")
                asrEngine = createAsrEngine(asrConfig)
                asrEngine?.initialize(context, asrConfig)
                LiveAsrLogger.i("LiveAsrProcessor: ASR引擎初始化完成")

                // 初始化翻译引擎
                val translateConfig = buildTranslateConfig()
                LiveAsrLogger.i("LiveAsrProcessor: 翻译引擎=${translateConfig.provider}, 目标语言=${translateConfig.translateTarget}, 开始初始化...")
                translateEngine = createTranslateEngine(translateConfig)
                translateEngine?.initialize(context, translateConfig)
                LiveAsrLogger.i("LiveAsrProcessor: 翻译引擎初始化完成，开始处理音频")

                // 处理音频循环
                while (isActive && running.get()) {
                    processAudioBuffer()
                    delay(BUFFER_DURATION_MS)
                }
            } catch (e: Throwable) {
                LiveAsrLogger.e("LiveAsrProcessor: 引擎初始化失败", e)
                withContext(Dispatchers.Main) {
                    onCues(emptyList())
                }
            } finally {
                running.set(false)
                asrEngine?.release()
                translateEngine?.release()
                languageId?.close()
                asrEngine = null
                translateEngine = null
                languageId = null
                LiveAsrLogger.i("LiveAsrProcessor: 已停止")
            }
        }
    }

    fun stop() {
        LiveAsrLogger.i("LiveAsrProcessor: stop()")
        running.set(false)
        processJob?.cancel()
        audioBuffer.clear()
        // 不在此处释放引擎 — 由协程 finally 块在 native 调用安全退出后释放
        // 避免 native recognize() 还在运行时释放上下文导致 SIGSEGV
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
        if (audioBuffer.isEmpty()) {
            LiveAsrLogger.d("processAudioBuffer: 缓冲区为空，跳过")
            return
        }

        // 合并缓冲区中的音频数据
        val totalSize = audioBuffer.sumOf { it.size }
        val merged = ByteArray(totalSize)
        var offset = 0
        for (data in audioBuffer) {
            System.arraycopy(data, 0, merged, offset, data.size)
            offset += data.size
        }
        val segmentCount = audioBuffer.size
        audioBuffer.clear()

        LiveAsrLogger.d("processAudioBuffer: ${segmentCount}段, ${totalSize}字节, ~${totalSize / 32}ms音频")

        // 语音识别
        val recognizedText = asrEngine?.recognize(merged)?.takeIf { it.isNotBlank() }
        if (recognizedText == null) {
            LiveAsrLogger.d("processAudioBuffer: 识别结果为空")
            return
        }

        LiveAsrLogger.d("ASR识别: \"$recognizedText\" [${merged.size}字节]")

        // 语言检测
        val sourceLang = try {
            languageId?.let { lid ->
                Tasks.await(lid.identifyLanguage(recognizedText), 5, TimeUnit.SECONDS)
            } ?: "en"
        } catch (e: Exception) {
            LiveAsrLogger.w("语言检测失败，默认en", e)
            "en"
        }
        LiveAsrLogger.d("语言检测: $sourceLang")

        // 翻译
        val translatedText = translateEngine?.let { engine ->
            try {
                val result = engine.translate(recognizedText, sourceLang)
                LiveAsrLogger.d("翻译结果: \"$result\"")
                result
            } catch (e: Exception) {
                LiveAsrLogger.w("翻译失败", e)
                "$recognizedText\n[$sourceLang]"
            }
        } ?: recognizedText

        // 生成字幕 Cue（样式由 SubtitleView.setStyle() 控制，位置由 setBottomPaddingFraction 控制）
        val cue = Cue.Builder()
            .setText(translatedText)
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
            "whisper" -> "whisper"
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
            "whisper" -> WhisperAsrEngine()
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
}