package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import androidx.media3.common.text.Cue
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 字幕处理管道
 *
 * 管道式编排：音频捕获 → RingBuffer → VAD → SegmentExtractor → ASR → 翻译 → Cue 输出
 *
 * 设计目标：
 * - 统一管理流式引擎（StreamingAsrEngine）和批处理引擎（BatchAsrEngine）
 * - 流式引擎：feedAudio 直接 feedChunk，实时输出 partial/final 结果
 * - 批处理引擎：VAD 检测到语音结束后提取段，再送入引擎识别
 * - 翻译结果通过 StateFlow 输出，UI 层可观察
 */
class SubtitlePipeline(
    private val context: Context,
    private val scope: CoroutineScope,
) {
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

    // 引擎引用
    private val asrEngineRef = AtomicReference<AsrEngine?>(null)
    private val translateEngineRef = AtomicReference<TranslateEngine?>(null)

    // 语言检测
    @Volatile
    private var languageId: com.google.mlkit.nl.languageid.LanguageIdentifier? = null

    // 管道状态
    private val running = AtomicBoolean(false)
    private val isRecognizing = AtomicBoolean(false)
    private var pipelineJob: Job? = null
    private var recognizeJob: Job? = null

    // 字幕输出
    private val _cues = MutableStateFlow<List<Cue>>(emptyList())
    val cues: StateFlow<List<Cue>> = _cues

    // 流式引擎的 partial 结果
    @Volatile
    private var partialText: String = ""

    // 翻译上下文窗口（最近 N 条翻译结果，用于上下文感知翻译）
    private val translationContext = ArrayDeque<String>()

    // 翻译缓存
    private val translationCache = TranslationCache()

    // 字幕渲染器
    private val subtitleRenderer = SubtitleRenderer(displayDurationMs = 5000L)

    init {
        segmentExtractor.onSegment = { segment ->
            onAudioSegment(segment)
        }
    }

    /**
     * 启动管道
     */
    fun start() {
        if (running.getAndSet(true)) return

        LiveAsrLogger.init(context)
        LiveAsrLogger.i("SubtitlePipeline: start()")

        pipelineJob = scope.launch(Dispatchers.IO) {
            try {
                // 初始化语言检测
                languageId = LanguageIdentification.getClient()

                // 初始化 ASR 引擎
                val asrConfig = buildAsrConfig()
                val asrEngine = createAsrEngine(asrConfig)
                asrEngine.initialize(context, asrConfig)
                asrEngineRef.set(asrEngine)

                // 如果是流式引擎，启动流式会话
                if (asrEngine is StreamingAsrEngine) {
                    asrEngine.startStream { result ->
                        onAsrResult(result)
                    }
                    LiveAsrLogger.i("SubtitlePipeline: 流式 ASR 会话已启动")
                }

                // 初始化翻译引擎
                val translateConfig = buildTranslateConfig()
                val translateEngine = createTranslateEngine(translateConfig)
                translateEngine.initialize(context, translateConfig)
                translateEngineRef.set(translateEngine)

                LiveAsrLogger.i("SubtitlePipeline: 管道就绪")

                // 显示加载提示
                _cues.value = listOf(Cue.Builder().setText("...").build())

                // 定时刷新循环
                while (isActive && running.get()) {
                    segmentExtractor.flush()
                    kotlinx.coroutines.delay(3000L)
                }
            } catch (e: Throwable) {
                LiveAsrLogger.e("SubtitlePipeline: 启动失败", e)
                releaseEngines()
                _cues.value = emptyList()
            } finally {
                running.set(false)
            }
        }
    }

    /**
     * 停止管道
     */
    fun stop() {
        LiveAsrLogger.i("SubtitlePipeline: stop()")
        running.set(false)
        pipelineJob?.cancel()
        recognizeJob?.cancel()

        // 结束流式会话
        (asrEngineRef.get() as? StreamingAsrEngine)?.endStream()

        releaseEngines()
        ringBuffer.clear()
        vadDetector.reset()
        segmentExtractor.reset()
        subtitleRenderer.clear()
        translationCache.clear()
        partialText = ""
        translationContext.clear()
        _cues.value = emptyList()
    }

    /**
     * 接收音频数据
     *
     * @param data 16kHz 单声道 PCM 字节数据
     * @param ptsUs 展示时间戳（微秒）
     */
    fun feedAudio(data: ByteArray, ptsUs: Long) {
        if (!running.get()) return

        // PCM 字节 → 浮点数据
        val samples = pcmBytesToFloats(data)

        // 写入环形缓冲区
        ringBuffer.write(samples, ptsUs)

        // 流式引擎：直接 feedChunk
        val asrEngine = asrEngineRef.get()
        if (asrEngine is StreamingAsrEngine) {
            asrEngine.feedChunk(data, ptsUs)
        }

        // 批处理引擎：VAD + SegmentExtractor
        if (asrEngine is BatchAsrEngine || asrEngine !is StreamingAsrEngine) {
            val vadEvent = vadDetector.process(samples, ptsUs)
            when (vadEvent) {
                is VadDetector.Event.SpeechEnd -> {
                    val speechStartPts = vadDetector.getSpeechStartPtsUs()
                    segmentExtractor.onSpeechEnd(speechStartPts, vadEvent.ptsUs)
                }
                else -> { /* SpeechStart 或 null 不需要操作 */ }
            }
        }
    }

    fun isRunning(): Boolean = running.get()

    // ==================== ASR 结果处理 ====================

    /**
     * 处理流式 ASR 结果（partial/final）
     */
    private fun onAsrResult(result: AsrResult) {
        if (!running.get()) return

        if (result.isFinal) {
            // Final 结果：翻译并输出
            partialText = ""
            scope.launch(Dispatchers.IO) {
                translateAndEmit(result.text, result.startTimeUs, result.endTimeUs, result.language)
            }
        } else {
            // Partial 结果：通过渲染器实时显示
            subtitleRenderer.updatePartial(result.text)
            emitCues()
        }
    }

    /**
     * 处理批处理 ASR 的音频段
     */
    private fun onAudioSegment(segment: AudioSegment) {
        val asrEngine = asrEngineRef.get()
        if (asrEngine is StreamingAsrEngine) return // 流式引擎不需要批处理

        if (isRecognizing.get()) {
            LiveAsrLogger.d("onAudioSegment: 上次识别仍在进行，跳过")
            return
        }

        recognizeJob = scope.launch(Dispatchers.IO) {
            isRecognizing.set(true)
            try {
                val asrResult = when (asrEngine) {
                    is BatchAsrEngine -> asrEngine.recognizeSegment(segment)
                    else -> {
                        // 兼容旧引擎
                        val pcmBytes = floatsToPcmBytes(segment.pcmData)
                        val text = asrEngine?.recognize(pcmBytes)
                        text?.let {
                            AsrResult(
                                text = it,
                                isFinal = true,
                                startTimeUs = segment.startTimeUs,
                                endTimeUs = segment.endTimeUs,
                            )
                        }
                    }
                }

                if (asrResult != null && asrResult.text.isNotBlank()) {
                    LiveAsrLogger.d("ASR识别: \"${asrResult.text}\" [${segment.durationMs}ms]")
                    translateAndEmit(
                        asrResult.text,
                        asrResult.startTimeUs,
                        asrResult.endTimeUs,
                        asrResult.language,
                    )
                }
            } catch (e: Exception) {
                LiveAsrLogger.e("onAudioSegment: 异常", e)
            } finally {
                isRecognizing.set(false)
            }
        }
    }

    /**
     * 翻译并输出字幕
     */
    private suspend fun translateAndEmit(
        text: String,
        startTimeUs: Long,
        endTimeUs: Long,
        detectedLanguage: String,
    ) {
        // 语言检测
        val sourceLang = try {
            languageId?.let { lid ->
                Tasks.await(lid.identifyLanguage(text), 5, TimeUnit.SECONDS)
            } ?: Configs.subtitleLiveAsrLanguage
        } catch (e: Exception) {
            detectedLanguage.ifBlank { Configs.subtitleLiveAsrLanguage }
        }

        val targetLang = Configs.subtitleLiveTranslateTarget

        // 查询翻译缓存
        val cached = translationCache.get(text, sourceLang, targetLang)
        if (cached != null) {
            LiveAsrLogger.d("翻译缓存命中: \"$text\" -> \"$cached\"")
            subtitleRenderer.addFinal(text, cached)
            emitCues()
            return
        }

        // 翻译
        val translateEngine = translateEngineRef.get()
        val translatedText = translateEngine?.let { engine ->
            try {
                // 构建带上下文的翻译文本
                val contextText = buildContextText(text)
                val result = engine.translate(contextText, sourceLang)
                // 提取最后一句翻译（去除上下文部分的翻译）
                val finalResult = extractLastTranslation(result, text)

                // 存入缓存
                translationCache.put(text, sourceLang, targetLang, finalResult)

                finalResult
            } catch (e: Exception) {
                LiveAsrLogger.w("翻译失败", e)
                "$text\n[$sourceLang]"
            }
        } ?: text

        if (!running.get()) return

        // 更新翻译上下文
        translationContext.add(text)
        if (translationContext.count() > 5) translationContext.removeFirst()

        // 通过渲染器输出
        subtitleRenderer.addFinal(text, translatedText)
        emitCues()
    }

    /**
     * 构建带上下文的翻译文本
     * 将最近几条原文拼接到当前文本前，帮助翻译引擎理解上下文
     */
    private fun buildContextText(currentText: String): String {
        if (translationContext.isEmpty()) return currentText
        val context = translationContext.joinToString(" ")
        return "$context $currentText"
    }

    /**
     * 从带上下文的翻译结果中提取当前句的翻译
     * 简单策略：取最后一句（以句号、问号、感叹号分割）
     */
    private fun extractLastTranslation(translated: String, original: String): String {
        // 如果翻译结果比原文长很多，可能是上下文也被翻译了
        // 简单处理：如果翻译长度 > 原文长度 * 2，取最后部分
        val sentences = translated.split(Regex("[。！？.!?]"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return if (sentences.size > 1 && translated.length > original.length * 2) {
            sentences.last()
        } else {
            translated
        }
    }

    // ==================== 引擎管理 ====================

    private fun releaseEngines() {
        asrEngineRef.getAndSet(null)?.let {
            runCatching { kotlinx.coroutines.runBlocking { it.release() } }
        }
        translateEngineRef.getAndSet(null)?.let {
            runCatching { kotlinx.coroutines.runBlocking { it.release() } }
        }
        languageId?.close()
        languageId = null
    }

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

    /** 通过渲染器构建 Cue 并更新 StateFlow */
    private fun emitCues() {
        val cues = subtitleRenderer.buildCues()
        scope.launch(Dispatchers.Main) {
            _cues.value = cues
        }
    }

    private fun pcmBytesToFloats(pcmBytes: ByteArray): FloatArray {
        val floatCount = pcmBytes.size / 2
        val floats = FloatArray(floatCount)
        val buf = java.nio.ByteBuffer.wrap(pcmBytes)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until floatCount) {
            floats[i] = buf.get(i) / 32768f
        }
        return floats
    }

    private fun floatsToPcmBytes(floats: FloatArray): ByteArray {
        val result = ByteArray(floats.size * 2)
        val buf = java.nio.ByteBuffer.wrap(result)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in floats.indices) {
            val sample = (floats[i] * 32768f).toInt().coerceIn(-32768, 32767).toShort()
            buf.put(i, sample)
        }
        return result
    }
}
