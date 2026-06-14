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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 实时字幕核心调度器（重构版）
 *
 * 架构升级：
 * - AudioRingBuffer：固定大小环形缓冲区，零 GC 压力
 * - VadDetector：静音检测，静音段跳过推理
 * - SegmentExtractor：从环形缓冲提取有效语音段 + PTS 时间戳
 * - AtomicReference：线程安全的引擎引用管理
 *
 * 数据流：
 * feedAudio(pcmData, ptsUs) → RingBuffer → VAD → SegmentExtractor → ASR → 翻译 → Cue
 */
class LiveAsrProcessor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onCues: (List<Cue>) -> Unit,
) {
    private val running = AtomicBoolean(false)

    // 基础设施
    private val ringBuffer = AudioRingBuffer(capacitySeconds = 30, sampleRate = 16000)
    private val vadDetector = VadDetector(
        thresholdDb = -40f,
        minSpeechMs = 300,
        minSilenceMs = 600,
        sampleRate = 16000,
    )
    private val segmentExtractor = SegmentExtractor(
        ringBuffer = ringBuffer,
        mergeGapMs = 500,
        minSegmentMs = 500,
        maxSegmentMs = 30000,
    )

    // 引擎引用（原子操作确保线程安全释放）
    private val asrEngineRef = AtomicReference<AsrEngine?>(null)
    private val translateEngineRef = AtomicReference<TranslateEngine?>(null)

    // 协程 Job
    private var processJob: Job? = null
    private var recognizeJob: Job? = null

    // 语言检测
    @Volatile
    private var languageId: com.google.mlkit.nl.languageid.LanguageIdentifier? = null

    // 识别状态
    private val isRecognizing = AtomicBoolean(false)

    // 定时刷新间隔（VAD 无事件时强制刷新待合并段）
    private companion object {
        const val FLUSH_INTERVAL_MS = 3000L
    }

    init {
        segmentExtractor.onSegment = { segment ->
            onAudioSegment(segment)
        }
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
                LiveAsrLogger.i("LiveAsrProcessor: ASR引擎=${asrConfig.provider}, 语言=${asrConfig.language}, 开始初始化...")
                val asrEngine = createAsrEngine(asrConfig)
                asrEngine.initialize(context, asrConfig)
                asrEngineRef.set(asrEngine)
                LiveAsrLogger.i("LiveAsrProcessor: ASR引擎初始化完成")

                // 初始化翻译引擎
                val translateConfig = buildTranslateConfig()
                LiveAsrLogger.i("LiveAsrProcessor: 翻译引擎=${translateConfig.provider}, 目标语言=${translateConfig.translateTarget}, 开始初始化...")
                val translateEngine = createTranslateEngine(translateConfig)
                translateEngine.initialize(context, translateConfig)
                translateEngineRef.set(translateEngine)
                LiveAsrLogger.i("LiveAsrProcessor: 翻译引擎初始化完成，开始处理音频")

                // 显示加载提示
                withContext(Dispatchers.Main) {
                    onCues(listOf(Cue.Builder().setText("...").build()))
                }

                // 定时刷新循环（VAD 无事件时强制输出待合并段）
                while (isActive && running.get()) {
                    segmentExtractor.flush()
                    delay(FLUSH_INTERVAL_MS)
                }
            } catch (e: Throwable) {
                LiveAsrLogger.e("LiveAsrProcessor: 引擎初始化失败", e)
                recognizeJob?.cancel()
                releaseEngines()
                withContext(Dispatchers.Main) {
                    onCues(emptyList())
                }
            } finally {
                running.set(false)
                LiveAsrLogger.i("LiveAsrProcessor: processJob 结束")
            }
        }
    }

    fun stop() {
        LiveAsrLogger.i("LiveAsrProcessor: stop()")
        running.set(false)
        processJob?.cancel()
        recognizeJob?.cancel()
        releaseEngines()
        ringBuffer.clear()
        vadDetector.reset()
        segmentExtractor.reset()
    }

    /**
     * 安全释放引擎：使用 getAndSet(null) 原子操作，确保只释放一次
     */
    private fun releaseEngines() {
        asrEngineRef.getAndSet(null)?.let {
            runCatching { kotlinx.coroutines.runBlocking { it.release() } }
        }
        translateEngineRef.getAndSet(null)?.let {
            runCatching { kotlinx.coroutines.runBlocking { it.release() } }
        }
        languageId?.close()
        languageId = null
        LiveAsrLogger.i("LiveAsrProcessor: 引擎已释放")
    }

    fun isRunning(): Boolean = running.get()

    /**
     * 接收音频数据并写入环形缓冲区 + VAD 检测
     *
     * @param data 16kHz 单声道 PCM 字节数据
     * @param ptsUs 展示时间戳（微秒）
     */
    fun feedAudio(data: ByteArray, ptsUs: Long) {
        if (!running.get()) return

        // PCM 字节 → 浮点数据（归一化到 [-1, 1]）
        val samples = pcmBytesToFloats(data)

        // 写入环形缓冲区
        ringBuffer.write(samples, ptsUs)

        // VAD 检测
        val vadEvent = vadDetector.process(samples, ptsUs)
        when (vadEvent) {
            is VadDetector.Event.SpeechStart -> {
                LiveAsrLogger.d("VAD: 语音开始 @ ${vadEvent.ptsUs}us")
            }
            is VadDetector.Event.SpeechEnd -> {
                LiveAsrLogger.d("VAD: 语音结束 @ ${vadEvent.ptsUs}us")
                val speechStartPts = vadDetector.getSpeechStartPtsUs()
                segmentExtractor.onSpeechEnd(speechStartPts, vadEvent.ptsUs)
            }
            null -> { /* 无事件 */ }
        }
    }

    /**
     * 处理提取出的音频段：ASR → 语言检测 → 翻译 → 输出 Cue
     */
    private fun onAudioSegment(segment: AudioSegment) {
        if (isRecognizing.get()) {
            LiveAsrLogger.d("onAudioSegment: 上次识别仍在进行，跳过")
            return
        }

        recognizeJob = scope.launch(Dispatchers.IO) {
            isRecognizing.set(true)
            try {
                // 将 FloatArray 转回 ByteArray 供现有引擎使用
                val pcmBytes = floatsToPcmBytes(segment.pcmData)

                val asrEngine = asrEngineRef.get()
                val recognizedText = asrEngine?.recognize(pcmBytes)?.takeIf { it.isNotBlank() }
                if (recognizedText == null) {
                    LiveAsrLogger.d("ASR: 识别结果为空 [${segment.durationMs}ms]")
                    return@launch
                }

                LiveAsrLogger.d("ASR识别: \"$recognizedText\" [${segment.durationMs}ms, PTS=${segment.startTimeUs}]")

                // 语言检测
                val sourceLang = try {
                    languageId?.let { lid ->
                        Tasks.await(lid.identifyLanguage(recognizedText), 5, TimeUnit.SECONDS)
                    } ?: Configs.subtitleLiveAsrLanguage
                } catch (e: Exception) {
                    LiveAsrLogger.w("语言检测失败，使用配置语言", e)
                    Configs.subtitleLiveAsrLanguage
                }
                LiveAsrLogger.d("语言检测: $sourceLang")

                // 翻译
                val translateEngine = translateEngineRef.get()
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

                if (!running.get()) {
                    LiveAsrLogger.d("已停止，丢弃识别结果")
                    return@launch
                }

                // 生成字幕 Cue
                val cue = Cue.Builder()
                    .setText(translatedText)
                    .build()

                withContext(Dispatchers.Main) {
                    onCues(listOf(cue))
                }
            } catch (e: Exception) {
                LiveAsrLogger.e("onAudioSegment: 异常", e)
            } finally {
                isRecognizing.set(false)
            }
        }
    }

    // ==================== 配置构建 ====================

    private fun buildAsrConfig(): AsrConfig {
        return AsrConfig(
            provider = resolveAsrProvider(),
            apiKey = Configs.subtitleLiveAsrApiKey,
            apiRegion = Configs.subtitleLiveAsrRegion,
            whisperModel = Configs.subtitleLiveWhisperModel,
            language = Configs.subtitleLiveAsrLanguage,
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

    private fun resolveTranslateProvider(): String {
        val provider = Configs.subtitleLiveTranslateProvider
        return when (provider) {
            "google", "azure", "baidu", "deepl" -> {
                if (Configs.subtitleLiveTranslateApiKey.isBlank()) "mlkit" else provider
            }
            else -> "mlkit"
        }
    }

    private fun createAsrEngine(config: AsrConfig): AsrEngine {
        return when (config.provider) {
            "vosk" -> VoskAsrEngine()
            "azure" -> AzureAsrEngine()
            "baidu" -> BaiduAsrEngine()
            "google" -> GoogleAsrEngine()
            "whisper" -> WhisperAsrEngine()
            else -> VoskAsrEngine()
        }
    }

    private fun createTranslateEngine(config: TranslateConfig): TranslateEngine {
        return when (config.provider) {
            "mlkit" -> MlKitTranslateEngine()
            "google" -> GoogleCloudTranslateEngine()
            "azure" -> AzureTranslateEngine()
            "baidu" -> BaiduTranslateEngine()
            "deepl" -> DeepLTranslateEngine()
            else -> MlKitTranslateEngine()
        }
    }

    // ==================== 工具方法 ====================

    /** PCM 16-bit 字节 → 浮点数据（归一化到 [-1, 1]） */
    private fun pcmBytesToFloats(pcmBytes: ByteArray): FloatArray {
        val floatCount = pcmBytes.size / 2
        val floats = FloatArray(floatCount)
        val buf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until floatCount) {
            floats[i] = buf.get(i) / 32768f
        }
        return floats
    }

    /** 浮点数据 → PCM 16-bit 字节 */
    private fun floatsToPcmBytes(floats: FloatArray): ByteArray {
        val result = ByteArray(floats.size * 2)
        val buf = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in floats.indices) {
            val sample = (floats[i] * 32768f).toInt().coerceIn(-32768, 32767).toShort()
            buf.put(i, sample)
        }
        return result
    }
}
