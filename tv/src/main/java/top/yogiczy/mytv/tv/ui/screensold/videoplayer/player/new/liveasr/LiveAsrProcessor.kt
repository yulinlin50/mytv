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
    private var recognizeJob: Job? = null
    private var languageId: com.google.mlkit.nl.languageid.LanguageIdentifier? = null
    private val isRecognizing = AtomicBoolean(false)

    // 缓冲参数
    private companion object {
        const val BUFFER_DURATION_MS = 2000L         // 每 2 秒收集一次音频
        const val AUDIO_BUFFER_POOL_SIZE = 200       // 最多保留约 4 秒音频
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

                // 音频收集循环：定时取缓冲区数据，启动异步识别
                while (isActive && running.get()) {
                    collectAndRecognize()
                    delay(BUFFER_DURATION_MS)
                }
            } catch (e: Throwable) {
                LiveAsrLogger.e("LiveAsrProcessor: 引擎初始化失败", e)
                withContext(Dispatchers.Main) {
                    onCues(emptyList())
                }
            } finally {
                running.set(false)
                recognizeJob?.cancel()
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

    /**
     * 收集缓冲区音频数据并启动异步识别
     * 如果上一次识别还在进行中，则跳过（避免重叠推理）
     */
    private fun collectAndRecognize() {
        if (audioBuffer.isEmpty()) {
            LiveAsrLogger.d("collectAndRecognize: 缓冲区为空，跳过")
            return
        }

        if (isRecognizing.get()) {
            LiveAsrLogger.d("collectAndRecognize: 上次识别仍在进行，跳过本次")
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

        LiveAsrLogger.d("collectAndRecognize: ${segmentCount}段, ${totalSize}字节, ~${totalSize / 32}ms音频")

        // 异步启动识别（不阻塞音频收集循环）
        recognizeJob = scope.launch(Dispatchers.IO) {
            isRecognizing.set(true)
            try {
                val recognizedText = asrEngine?.recognize(merged)?.takeIf { it.isNotBlank() }
                if (recognizedText == null) {
                    LiveAsrLogger.d("recognize: 识别结果为空")
                    return@launch
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

                // 生成字幕 Cue
                val cue = Cue.Builder()
                    .setText(translatedText)
                    .build()

                withContext(Dispatchers.Main) {
                    onCues(listOf(cue))
                }
            } catch (e: Exception) {
                LiveAsrLogger.e("recognize: 异常", e)
            } finally {
                isRecognizing.set(false)
            }
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