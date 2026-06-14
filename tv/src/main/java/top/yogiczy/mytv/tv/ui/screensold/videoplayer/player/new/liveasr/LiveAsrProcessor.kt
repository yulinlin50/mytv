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
 * 实时字幕核心调度器（低延迟版）
 *
 * 核心优化策略（参考主流实时字幕方案）：
 * 1. 滑动窗口推理：语音进行中每 STEP_MS 触发一次推理，输出部分结果
 *    - 不等语音段完全结束再推理，边说边出字幕
 *    - 参考 WhisperPipe / WhisperLiveKit / WhisperFlow
 * 2. 流水线并行：ASR 和翻译重叠执行
 *    - 当前段翻译时，下一段 ASR 可同时进行
 *    - 参考 ASR+VLLM Pipeline 方案
 * 3. Channel 队列：段排队顺序处理，不丢失语音
 * 4. VAD 超时截断 + 立即 flush：防止段合并导致延迟
 *
 * 数据流：
 * feedAudio → RingBuffer → VAD → [滑动窗口/段提取] → Channel → ASR → 翻译 → Cue
 */
class LiveAsrProcessor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onCues: (List<Cue>) -> Unit,
) {
    private val running = AtomicBoolean(false)

    // 会话代次：每次 start() 递增，用于防止旧 processJob 的 finally 覆盖新会话状态
    private val generation = java.util.concurrent.atomic.AtomicInteger(0)

    // 基础设施
    private val ringBuffer = AudioRingBuffer(capacitySeconds = 30, sampleRate = 16000)
    private val vadDetector = VadDetector(
        thresholdDb = -40f,
        minSpeechMs = 150,
        minSilenceMs = 250,
        maxSpeechMs = 3000,
        sampleRate = 16000,
    )
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

    // 滑动窗口步长：每 STEP_MS 对当前语音段做一次推理，输出部分结果
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

    init {
        segmentExtractor.onSegment = { segment ->
            // 将段发送到 Channel，不丢弃
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

                // 启动段消费协程：流水线并行处理 ASR + 翻译
                recognizeJob = scope.launch(Dispatchers.IO) {
                    pipelineProcessSegments()
                }

                // 滑动窗口推理循环 + 定时刷新
                while (isActive && running.get() && generation.get() == gen) {
                    // 滑动窗口：语音进行中，每 STEP_MS 推理一次
                    if (isInSpeech && speechStartPtsUs > 0) {
                        val currentPts = ringBuffer.getCurrentPtsUs()
                        val speechDurationMs = (currentPts - speechStartPtsUs) / 1000L
                        val sinceLastPartialMs = (currentPts - lastPartialPtsUs) / 1000L

                        if (speechDurationMs >= MIN_PARTIAL_MS && sinceLastPartialMs >= STEP_MS) {
                            // 读取从语音开始到当前的音频，做部分推理
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
        // 在 IO 线程释放引擎
        cleanupJob = scope.launch(Dispatchers.IO) {
            releaseEngines()
        }
        ringBuffer.clear()
        vadDetector.reset()
        segmentExtractor.reset()
    }

    private suspend fun releaseEngines() {
        asrEngineRef.getAndSet(null)?.let {
            runCatching { it.release() }
        }
        translateEngineRef.getAndSet(null)?.let {
            runCatching { it.release() }
        }
        languageId?.close()
        languageId = null
        LiveAsrLogger.i("LiveAsrProcessor: 引擎已释放")
    }

    fun isRunning(): Boolean = running.get()

    /**
     * 接收音频数据并写入环形缓冲区 + VAD 检测
     */
    fun feedAudio(data: ByteArray, ptsUs: Long) {
        if (!running.get()) return

        val samples = pcmBytesToFloats(data)
        ringBuffer.write(samples, ptsUs)

        val vadEvent = vadDetector.process(samples, ptsUs)
        when (vadEvent) {
            is VadDetector.Event.SpeechStart -> {
                LiveAsrLogger.d("VAD: 语音开始 @ ${vadEvent.ptsUs}us")
                isInSpeech = true
                speechStartPtsUs = vadEvent.ptsUs
                lastPartialPtsUs = vadEvent.ptsUs
            }
            is VadDetector.Event.SpeechEnd -> {
                val isTimeout = vadDetector.wasTimeout()
                LiveAsrLogger.d("VAD: 语音结束 @ ${vadEvent.ptsUs}us${if (isTimeout) " (超时截断)" else ""}")
                isInSpeech = false
                val speechStartPts = vadDetector.getSpeechStartPtsUs()

                // 超时截断时立即 flush，防止与下一段合并
                if (isTimeout) {
                    segmentExtractor.flush()
                }
                segmentExtractor.onSpeechEnd(speechStartPts, vadEvent.ptsUs)
            }
            null -> { /* 无事件 */ }
        }
    }

    /**
     * 流水线并行处理段：ASR 和翻译重叠执行
     *
     * 核心思路：当段 N 在翻译时，段 N+1 可以同时进行 ASR
     * 参考 ASR+VLLM Pipeline 方案
     */
    private suspend fun pipelineProcessSegments() {
        var pendingTranslation: Job? = null  // 上一个段的翻译 Job

        for (segment in segmentChannel) {
            if (!running.get()) break

            // 等上一个段的翻译完成（流水线：翻译和下一个ASR并行）
            pendingTranslation?.join()

            // ASR 识别
            val asrResult = recognizeSegment(segment) ?: continue

            // 启动翻译（异步），不阻塞下一个段的 ASR
            pendingTranslation = scope.launch(Dispatchers.IO) {
                translateAndShow(asrResult, segment)
            }
        }

        // 等最后一个翻译完成
        pendingTranslation?.join()
    }

    /**
     * ASR 识别一个音频段
     * 优化：对 Whisper 引擎直接传 FloatArray，避免 Float→Byte→Float 重复转换
     */
    private suspend fun recognizeSegment(segment: AudioSegment): String? {
        val asrEngine = asrEngineRef.get() ?: return null

        val recognizedText = if (asrEngine is WhisperAsrEngine) {
            // Whisper 引擎：直接传 FloatArray，省去 Float→Byte→Float 转换
            asrEngine.recognizeFloats(segment.pcmData)?.trim()?.takeIf { it.isNotBlank() }
        } else {
            // 其他引擎：转 PCM 字节
            val pcmBytes = floatsToPcmBytes(segment.pcmData)
            asrEngine.recognize(pcmBytes)?.trim()?.takeIf { it.isNotBlank() }
        }

        if (recognizedText == null) {
            LiveAsrLogger.d("ASR: 识别结果为空 [${segment.durationMs}ms]")
            return null
        }

        LiveAsrLogger.d("ASR识别: \"$recognizedText\" [${segment.durationMs}ms]")
        return recognizedText
    }

    /**
     * 翻译并显示字幕
     */
    private suspend fun translateAndShow(recognizedText: String, segment: AudioSegment) {
        try {
            // 语言检测：如果用户已配置语言，直接使用
            val configuredLang = Configs.subtitleLiveAsrLanguage
            val sourceLang = if (configuredLang.isNotBlank() && configuredLang != "auto") {
                configuredLang
            } else {
                detectLanguage(recognizedText)
            }

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

            if (!running.get()) return

            // 去重：滑动窗口推理可能产生重复结果
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
            else -> WhisperAsrEngine()  // 默认回退到 Whisper（JNA 兼容性更好）
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
