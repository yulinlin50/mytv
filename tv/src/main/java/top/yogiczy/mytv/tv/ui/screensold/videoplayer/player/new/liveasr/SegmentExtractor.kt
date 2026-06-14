package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

/**
 * 语音段提取器
 *
 * 从环形缓冲区中提取有效语音段，附带 PTS 时间戳。
 * 工作流程：
 * 1. VAD 检测到语音结束 → 调用 onSpeechEnd(startPts, endPts)
 * 2. 从环形缓冲区读取 [startPts, endPts] 范围的音频
 * 3. 合并过短的相邻段（间隔 < mergeGapMs）
 * 4. 输出 AudioSegment 供 ASR 引擎识别
 *
 * 设计原则：
 * - 仅在 VAD 检测到有效语音时才提取，静音段完全跳过
 * - 提取的段带精确 PTS 时间戳，用于字幕同步
 * - 自动合并短间隔的相邻语音段，避免断句过碎
 */
class SegmentExtractor(
    private val ringBuffer: AudioRingBuffer,
    /** 合并间隔阈值（毫秒），间隔小于此值的相邻语音段合并 */
    private val mergeGapMs: Long = 500,
    /** 最小段时长（毫秒），短于此的段不输出 */
    private val minSegmentMs: Long = 500,
    /** 最大段时长（毫秒），超过此值强制截断输出 */
    private val maxSegmentMs: Long = 30000,
) {
    /** 提取出的音频段回调 */
    var onSegment: ((AudioSegment) -> Unit)? = null

    // 待合并的段列表
    private val pendingSegments = mutableListOf<AudioSegment>()

    /**
     * 处理 VAD 语音结束事件
     *
     * @param speechStartPtsUs 语音开始的 PTS（微秒）
     * @param speechEndPtsUs 语音结束的 PTS（微秒）
     */
    fun onSpeechEnd(speechStartPtsUs: Long, speechEndPtsUs: Long) {
        if (speechEndPtsUs <= speechStartPtsUs) return

        val segment = ringBuffer.readRange(speechStartPtsUs, speechEndPtsUs) ?: run {
            LiveAsrLogger.w("SegmentExtractor: 无法读取音频段 [$speechStartPtsUs, $speechEndPtsUs]")
            return
        }

        tryMergeOrEmit(segment)
    }

    /**
     * 强制刷新当前待合并的段（例如 VAD 长时间无事件时）
     */
    fun flush() {
        for (segment in pendingSegments) {
            emitSegment(segment)
        }
        pendingSegments.clear()
    }

    /**
     * 重置提取器状态
     */
    fun reset() {
        pendingSegments.clear()
    }

    // ==================== 私有方法 ====================

    private fun tryMergeOrEmit(segment: AudioSegment) {
        if (pendingSegments.isEmpty()) {
            pendingSegments.add(segment)
            return
        }

        val lastSegment = pendingSegments.last()
        val gapMs = (segment.startTimeUs - lastSegment.endTimeUs) / 1000L

        if (gapMs < mergeGapMs) {
            // 间隔小于阈值，合并段
            val merged = mergeSegments(lastSegment, segment)
            pendingSegments[pendingSegments.lastIndex] = merged
        } else {
            // 间隔超过阈值，先输出之前的段
            for (pending in pendingSegments) {
                emitSegment(pending)
            }
            pendingSegments.clear()
            pendingSegments.add(segment)
        }

        // 检查当前累积段是否超过最大时长
        val current = pendingSegments.last()
        if (current.durationMs >= maxSegmentMs) {
            emitSegment(current)
            pendingSegments.clear()
        }
    }

    private fun mergeSegments(a: AudioSegment, b: AudioSegment): AudioSegment {
        val mergedData = FloatArray(a.pcmData.size + b.pcmData.size)
        System.arraycopy(a.pcmData, 0, mergedData, 0, a.pcmData.size)
        System.arraycopy(b.pcmData, 0, mergedData, a.pcmData.size, b.pcmData.size)

        return AudioSegment(
            pcmData = mergedData,
            startTimeUs = a.startTimeUs,
            endTimeUs = b.endTimeUs,
            sampleRate = a.sampleRate,
        )
    }

    private fun emitSegment(segment: AudioSegment) {
        if (segment.durationMs < minSegmentMs) {
            LiveAsrLogger.d("SegmentExtractor: 段过短(${segment.durationMs}ms)，跳过")
            return
        }

        LiveAsrLogger.d("SegmentExtractor: 输出段 ${segment.durationMs}ms, PTS=[${segment.startTimeUs}, ${segment.endTimeUs}]")
        onSegment?.invoke(segment)
    }
}
