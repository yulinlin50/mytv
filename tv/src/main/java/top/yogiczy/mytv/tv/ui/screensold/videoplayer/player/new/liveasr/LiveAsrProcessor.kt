package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import androidx.media3.common.text.Cue
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
 * 实时字幕核心调度器
 *
 * 支持两种推理模式：
 *
 * 1. 批处理模式（batch）：
 *    VAD → 段提取 → 整段 ASR → 翻译 → 显示
 *    适合 SenseVoice/Whisper，准确度高，延迟取决于段长度
 *
 * 2. 流式模式（streaming）：
 *    音频持续送入 → OnlineRecognizer 实时推理 → 翻译 → 显示
 *    适合 Paraformer，延迟极低（~100ms），边说边出字幕
 *
 * 数据流（批处理）：
 * feedAudio → RingBuffer → VAD → [滑动窗口/段提取] → Channel → ASR → 翻译 → Cue
 *
 * 数据流（流式）：
 * feedAudio → StreamingAsrEngine → callback(partial/final) → 翻译 → Cue
 */
class LiveAsrProcessor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onCues: (List<Cue>) -> Unit,
) {
    private val running = AtomicBoolean(false)

    // 会话代次：每次 start() 递增，用于防止旧 processJob 的 finally 覆盖新会话状态
    private val generation = java.util.concurrent.atomic.AtomicInteger(0)

    // 当前推理模式
    @Volatile
    private var asrMode: String = "batch"

    // ==================== 批处理模式基础设施 ====================

    private val ringBuffer = AudioRingBuffer(capacitySeconds = 30, sampleRate = 16000)
    private val energyVadDetector = VadDetector(
        thresholdDb = -40f,
        minSpeechMs = 150,
        minSilenceMs = 250,
        maxSpeechMs = 3000,
        sampleRate = 16000,
    )
    private var sileroVadDetector: SileroVadDetector? = null
    private val segmentExtractor = SegmentExtractor(
        ringBuffer = ringBuffer,
        mergeGapMs = 150,
        minSegmentMs = 200,
        maxSegmentMs = 6000,
    )

    // 引擎引用（原子操作确保线程安全释放）
    private val asrEngineRef = AtomicReference<AsrEngine?>(null)
    private val translateEngineRef = AtomicReference<TranslateEngine?>(null)

    // 协程 Job
    private var processJob: Job? = null
    private var recognizeJob: Job? = null

    // 清理 Job（跟踪 stop() 的异步释放）
    private var cleanupJob: Job? = null

    // 语言检测
    @Volatile
    private var languageId: com.google.mlkit.nl.languageid.LanguageIdentifier? = null

    // 音频段队列：段排队顺序处理，不丢失
    private val segmentChannel = Channel<AudioSegment>(capacity = Channel.UNLIMITED)

    // ==================== 滑动窗口推理 ====================

    private companion object {
        const val STEP_MS = 3000L          // 每3秒推理一次（主流方案 2-5秒）
        const val FLUSH_INTERVAL_MS = 1500L // 定时刷新间隔
        const val MIN_PARTIAL_MS = 1000L    // 最短部分结果时长
    }

    // 语音进行中的推理控制
    @Volatile
    private var speechStartPtsUs: Long = 0L
    @Volatile
    private var isInSpeech: Boolean = false
    @Volatile
    private var lastPartialPtsUs: Long = 0L  // 上次部分推理的结束 PTS
    @Volatile
    private var lastPartialText: String = ""  // 上次部分推理结果（去重用）

    // ==================== 流式模式状态 ====================

    @Volatile
    private var streamingEngine: SherpaOnnxStreamingAsrEngine? = null
    @Volatile
    private var streamingLastText: String = ""  // 流式去重

    init {
        segmentExtractor.onSegment = { segment ->
            val result = segmentChannel.trySend(segment)
            if (result.isFailure) {
                LiveAsrLogger.w("onAudioSegment: 段队列已满，丢弃")
            }
        }
    }

    fun start() {
        if (running.getAndSet(true)) return

        val gen = generation.incrementAndGet()
        LiveAsrLogger.init(context)
        LiveAsrLogger.i("LiveAsrProcessor: start(), generation=$gen")

        // 等待上一次 stop() 的异步清理完成
        cleanupJob?.let { job ->
            if (!job.isCompleted) {
                LiveAsrLogger.i("LiveAsrProcessor: 等待上一次清理完成...")
                runCatching { kotlinx.coroutines.runBlocking { job.join() } }
            }
            cleanupJob = null
        }

        processJob = scope.launch(Dispatchers.IO) {
            try {
                // 读取推理模式配置
                asrMode = Configs.subtitleLiveAsrMode
                LiveAsrLogger.i("LiveAsrProcessor: 推理模式=$asrMode")

                // 初始化语言检测
                languageId = LanguageIdentification.getClient()
                LiveAsrLogger.i("LiveAsrProcessor: 语言检测初始化完成")

                // 初始化 VAD
                val vadProvider = Configs.subtitleLiveVadProvider
                if (vadProvider == "silero") {
                    val silero = SileroVadDetector(
                        threshold = 0.5f,
                        minSpeechMs = 200,
                        minSilenceMs = 300,
                        maxSpeechMs = 5000,
                    )
                    silero.initialize(context)
                    sileroVadDetector = silero
                    LiveAsrLogger.i("LiveAsrProcessor: Silero VAD 初始化完成")
                } else {
                    sileroVadDetector = null
                    LiveAsrLogger.i("LiveAsrProcessor: 使用 RMS 能量 VAD")
                }

                if (asrMode == "streaming") {
                    // ===== 流式模式初始化 =====
                    val streamingAsr = SherpaOnnxStreamingAsrEngine()
                    val asrConfig = AsrConfig(
                        provider = "streaming-paraformer",
                        apiKey = "",
                        apiRegion = "",
                        whisperModel = "",
                        language = Configs.subtitleLiveAsrLanguage.ifBlank { "zh" },
                    )
                    streamingAsr.initialize(context, asrConfig)
                    streamingEngine = streamingAsr

                    // 启动流式会话
                    streamingAsr.startStream { result ->
                        handleStreamingResult(result)
                    }

                    LiveAsrLogger.i("LiveAsrProcessor: 流式 ASR 引擎初始化完成")
                } else {
                    // ===== 批处理模式初始化 =====
                    val asrConfig = buildAsrConfig()
                    LiveAsrLogger.i("LiveAsrProcessor: ASR引擎=${asrConfig.provider}, 语言=${asrConfig.language}, 开始初始化...")
                    val asrEngine = createAsrEngine(asrConfig)
                    asrEngine.initialize(context, asrConfig)
                    asrEngineRef.set(asrEngine)
                    LiveAsrLogger.i("LiveAsrProcessor: ASR引擎初始化完成")
                }

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

                if (asrMode == "streaming") {
                    // 流式模式：不需要滑动窗口循环，feedAudio 直接送入引擎
                    // 只需保持协程活跃
                    while (isActive && running.get() && generation.get() == gen) {
                        delay(1000)
                    }
                } else {
                    // 批处理模式：启动段消费协程 + 滑动窗口推理循环
                    recognizeJob = scope.launch(Dispatchers.IO) {
                        pipelineProcessSegments()
                    }

                    while (isActive && running.get() && generation.get() == gen) {
                        // 滑动窗口：语音进行中，每 STEP_MS 推理一次
                        if (isInSpeech && speechStartPtsUs > 0) {
                            val currentPts = ringBuffer.getCurrentPtsUs()
                            val speechDurationMs = (currentPts - speechStartPtsUs) / 1000L
                            val sinceLastPartialMs = (currentPts - lastPartialPtsUs) / 1000L

                            if (speechDurationMs >= MIN_PARTIAL_MS && sinceLastPartialMs >= STEP_MS) {
                                val partialSegment = ringBuffer.readRange(speechStartPtsUs, currentPts)
                                if (partialSegment != null && partialSegment.durationMs >= MIN_PARTIAL_MS) {
                                    LiveAsrLogger.d("滑动窗口: 部分推理 ${partialSegment.durationMs}ms")
                                    segmentChannel.trySend(partialSegment)
                                    lastPartialPtsUs = currentPts
                                }
                            }
                        }

                        // 定时刷新 SegmentExtractor 的待合并段
                        segmentExtractor.flush()
                        delay(minOf(STEP_MS, FLUSH_INTERVAL_MS) / 2)
                    }
                }
            } catch (e: Throwable) {
                LiveAsrLogger.e("LiveAsrProcessor: 引擎初始化失败", e)
                recognizeJob?.cancel()
                releaseEngines()
                withContext(Dispatchers.Main) {
                    onCues(emptyList())
                }
            } finally {
                if (generation.get() == gen) {
                    running.set(false)
                }
                LiveAsrLogger.i("LiveAsrProcessor: processJob 结束, generation=$gen")
            }
        }
    }

    fun stop() {
        LiveAsrLogger.i("LiveAsrProcessor: stop()")
        running.set(false)
        processJob?.cancel()
        recognizeJob?.cancel()
        segmentChannel.close()
        isInSpeech = false
        speechStartPtsUs = 0L
        lastPartialPtsUs = 0L
        lastPartialText = ""
        streamingLastText = ""
        // 在 IO 线程释放引擎
        cleanupJob = scope.launch(Dispatchers.IO) {
            releaseEngines()
        }
        ringBuffer.clear()
        energyVadDetector.reset()
        sileroVadDetector?.reset()
        segmentExtractor.reset()
    }

    private suspend fun releaseEngines() {
        asrEngineRef.getAndSet(null)?.let {
            runCatching { it.release() }
        }
        streamingEngine?.let {
            runCatching { it.endStream() }
            runCatching { it.release() }
        }
        streamingEngine = null
        translateEngineRef.getAndSet(null)?.let {
            runCatching { it.release() }
        }
        sileroVadDetector?.release()
        sileroVadDetector = null
        languageId?.close()
        languageId = null
        LiveAsrLogger.i("LiveAsrProcessor: 引擎已释放")
    }

    fun isRunning(): Boolean = running.get()

    /**
     * 接收音频数据
     *
     * 流式模式：直接送入 StreamingAsrEngine
     * 批处理模式：写入 RingBuffer + VAD 检测
     */
    fun feedAudio(data: ByteArray, ptsUs: Long) {
        if (!running.get()) return

        val samples = pcmBytesToFloats(data)

        if (asrMode == "streaming") {
            // ===== 流式模式：直接送入流式引擎 =====
            val engine = streamingEngine ?: return
            engine.feedChunkFloats(samples, ptsUs)
        } else {
            // ===== 批处理模式：VAD + 段提取 =====
            ringBuffer.write(samples, ptsUs)

            val silero = sileroVadDetector
            val vadEvent = if (silero != null) {
                silero.process(samples, ptsUs)
            } else {
                energyVadDetector.process(samples, ptsUs)
            }

            when (vadEvent) {
                is VadDetector.Event.SpeechStart -> {
                    LiveAsrLogger.d("VAD: 语音开始 @ ${vadEvent.ptsUs}us")
                    isInSpeech = true
                    speechStartPtsUs = vadEvent.ptsUs
                    lastPartialPtsUs = vadEvent.ptsUs
                }
                is VadDetector.Event.SpeechEnd -> {
                    val isTimeout = if (silero != null) silero.wasTimeout() else energyVadDetector.wasTimeout()
                    LiveAsrLogger.d("VAD: 语音结束 @ ${vadEvent.ptsUs}us${if (isTimeout) " (超时截断)" else ""}")
                    isInSpeech = false
                    val speechStartPts = if (silero != null) silero.getSpeechStartPtsUs() else energyVadDetector.getSpeechStartPtsUs()

                    if (isTimeout) {
                        segmentExtractor.flush()
                    }
                    segmentExtractor.onSpeechEnd(speechStartPts, vadEvent.ptsUs)
                }
                null -> { /* 无事件 */ }
            }
        }
    }

    // ==================== 流式模式回调 ====================

    /**
     * 处理流式 ASR 结果
     *
     * OnlineRecognizer 的回调在音频处理线程触发，
     * 需要异步调度翻译和 UI 更新
     */
    private fun handleStreamingResult(result: AsrResult) {
        val text = result.text.trim()
        if (text.isBlank()) return

        // 去重：partial 结果可能和上次相同
        if (text == streamingLastText && !result.isFinal) return

        LiveAsrLogger.d("StreamingASR: ${if (result.isFinal) "final" else "partial"} \"$text\"")

        if (result.isFinal) {
            // final 结果：翻译并显示
            streamingLastText = ""
            scope.launch(Dispatchers.IO) {
                translateAndShowStreaming(text, result.language)
            }
        } else {
            // partial 结果：直接显示原文（不翻译，避免频繁翻译请求）
            streamingLastText = text
            scope.launch(Dispatchers.Main) {
                if (running.get()) {
                    onCues(listOf(Cue.Builder().setText(text).build()))
                }
            }
        }
    }

    /**
     * 流式模式翻译并显示
     */
    private suspend fun translateAndShowStreaming(text: String, lang: String) {
        try {
            val configuredLang = Configs.subtitleLiveAsrLanguage
            val sourceLang = if (configuredLang.isNotBlank() && configuredLang != "auto") {
                configuredLang
            } else {
                lang.ifBlank { "zh" }
            }

            val translateEngine = translateEngineRef.get()
            val translatedText = translateEngine?.let { engine ->
                try {
                    val result = engine.translate(text, sourceLang)
                    LiveAsrLogger.d("翻译结果: \"$result\"")
                    result
                } catch (e: Exception) {
                    LiveAsrLogger.w("翻译失败", e)
                    text
                }
            } ?: text

            if (!running.get()) return

            val cue = Cue.Builder()
                .setText(translatedText)
                .build()

            withContext(Dispatchers.Main) {
                if (running.get()) {
                    onCues(listOf(cue))
                }
            }
        } catch (e: Exception) {
            LiveAsrLogger.e("translateAndShowStreaming: 异常", e)
        }
    }

    // ==================== 批处理模式 ====================

    /**
     * 流水线并行处理段：ASR 和翻译重叠执行
     */
    private suspend fun pipelineProcessSegments() {
        var pendingTranslation: Job? = null

        for (segment in segmentChannel) {
            if (!running.get()) break

            pendingTranslation?.join()

            val asrResult = recognizeSegment(segment) ?: continue

            pendingTranslation = scope.launch(Dispatchers.IO) {
                translateAndShow(asrResult, segment)
            }
        }

        pendingTranslation?.join()
    }

    /**
     * ASR 识别一个音频段（批处理模式）
     */
    private suspend fun recognizeSegment(segment: AudioSegment): String? {
        val asrEngine = asrEngineRef.get() ?: return null

        val recognizedText = when (asrEngine) {
            is WhisperAsrEngine -> {
                asrEngine.recognizeFloats(segment.pcmData)?.trim()?.takeIf { it.isNotBlank() }
            }
            is SherpaOnnxAsrEngine -> {
                asrEngine.recognizeFloats(segment.pcmData)?.trim()?.takeIf { it.isNotBlank() }
            }
            else -> {
                val pcmBytes = floatsToPcmBytes(segment.pcmData)
                asrEngine.recognize(pcmBytes)?.trim()?.takeIf { it.isNotBlank() }
            }
        }

        if (recognizedText == null) {
            LiveAsrLogger.d("ASR: 识别结果为空 [${segment.durationMs}ms]")
            return null
        }

        LiveAsrLogger.d("ASR识别: \"$recognizedText\" [${segment.durationMs}ms]")
        return recognizedText
    }

    /**
     * 翻译并显示字幕（批处理模式）
     */
    private suspend fun translateAndShow(recognizedText: String, segment: AudioSegment) {
        try {
            val configuredLang = Configs.subtitleLiveAsrLanguage
            val sourceLang = if (configuredLang.isNotBlank() && configuredLang != "auto") {
                configuredLang
            } else if (asrEngineRef.get() is SherpaOnnxAsrEngine) {
                "zh"
            } else {
                detectLanguage(recognizedText)
            }

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

            if (!running.get()) return

            if (translatedText == lastPartialText) {
                LiveAsrLogger.d("去重: 与上次结果相同，跳过")
                return
            }
            lastPartialText = translatedText

            val cue = Cue.Builder()
                .setText(translatedText)
                .build()

            withContext(Dispatchers.Main) {
                onCues(listOf(cue))
            }
        } catch (e: Exception) {
            LiveAsrLogger.e("translateAndShow: 异常", e)
        }
    }

    /**
     * 语言检测（带缓存和超时）
     */
    private suspend fun detectLanguage(text: String): String {
        val configuredLang = Configs.subtitleLiveAsrLanguage
        return try {
            languageId?.let { lid ->
                Tasks.await(lid.identifyLanguage(text), 2, TimeUnit.SECONDS)
            } ?: configuredLang.ifBlank { "en" }
        } catch (e: Exception) {
            LiveAsrLogger.w("语言检测失败，使用配置语言", e)
            configuredLang.ifBlank { "en" }
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
                if (Configs.subtitleLiveAsrApiKey.isBlank()) "whisper" else provider
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
            "sensevoice" -> SherpaOnnxAsrEngine()
            else -> WhisperAsrEngine()
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
