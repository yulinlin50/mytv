package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import kotlin.math.min

/**
 * 固定大小环形音频缓冲区
 *
 * 设计目标：
 * - 零 GC 压力：预分配固定大小 FloatArray，写入时无内存分配
 * - 线程安全：synchronized 保护读写
 * - PTS 索引：支持按时间范围读取音频段
 * - 自动覆盖：写入超过容量时自动覆盖最旧数据
 *
 * 容量：默认 30s × 16000Hz = 480,000 samples ≈ 1.92MB
 */
class AudioRingBuffer(
    private val capacitySeconds: Int = 30,
    private val sampleRate: Int = 16000,
) {
    private val capacity = capacitySeconds * sampleRate
    private val buffer = FloatArray(capacity)

    // 环形写入位置
    private var writePos = 0
    // 写入位置对应的 PTS（微秒）
    private var writePtsUs: Long = 0L
    // 已写入的总样本数（用于判断缓冲区是否已满）
    private var totalWritten = 0L

    // PTS 索引：记录每个写入位置的 PTS，用于按时间范围读取
    // 稀疏索引：每 INDEX_INTERVAL_SAMPLES 个样本记录一个 PTS
    private val indexInterval = sampleRate / 2 // 每 0.5s 一个索引点
    private val ptsIndex = LongArray(capacity / indexInterval + 2)
    private val posIndex = IntArray(capacity / indexInterval + 2)
    private var indexCount = 0

    /**
     * 写入音频数据到环形缓冲区
     *
     * @param samples PCM 浮点数据（归一化到 [-1, 1]）
     * @param ptsUs 当前数据的展示时间戳（微秒）
     */
    @Synchronized
    fun write(samples: FloatArray, ptsUs: Long) {
        if (samples.isEmpty()) return

        val writeLen = min(samples.size, capacity)
        val srcOffset = samples.size - writeLen // 如果数据超过容量，只保留最新的

        // 分段写入（处理环形回绕）
        var srcPos = srcOffset
        var remaining = writeLen

        while (remaining > 0) {
            val chunkLen = min(remaining, capacity - writePos)
            System.arraycopy(samples, srcPos, buffer, writePos, chunkLen)
            srcPos += chunkLen
            writePos = (writePos + chunkLen) % capacity
            remaining -= chunkLen
        }

        // 更新 PTS 索引
        writePtsUs = ptsUs
        totalWritten += writeLen.toLong()

        // 记录索引点
        addIndexPoint(writePos, ptsUs)
    }

    /**
     * 按时间范围读取音频数据
     *
     * @param fromUs 起始 PTS（微秒）
     * @param toUs 结束 PTS（微秒）
     * @return 音频段数据，如果范围无效返回 null
     */
    @Synchronized
    fun readRange(fromUs: Long, toUs: Long): AudioSegment? {
        if (toUs <= fromUs || totalWritten == 0L) return null

        // 通过 PTS 索引找到起止位置
        val startPos = findPositionForPts(fromUs) ?: return null
        val endPos = findPositionForPts(toUs) ?: return null

        // 计算样本数
        val sampleCount = if (endPos >= startPos) {
            endPos - startPos
        } else {
            capacity - startPos + endPos
        }

        if (sampleCount <= 0 || sampleCount > capacity) return null

        // 从环形缓冲区读取连续数据
        val result = FloatArray(sampleCount)
        var readPos = startPos
        var destPos = 0
        var remaining = sampleCount

        while (remaining > 0) {
            val chunkLen = min(remaining, capacity - readPos)
            System.arraycopy(buffer, readPos, result, destPos, chunkLen)
            readPos = (readPos + chunkLen) % capacity
            destPos += chunkLen
            remaining -= chunkLen
        }

        return AudioSegment(
            pcmData = result,
            startTimeUs = fromUs,
            endTimeUs = toUs,
            sampleRate = sampleRate,
        )
    }

    /**
     * 读取最近指定时长的音频数据
     *
     * @param durationUs 时长（微秒）
     * @return 音频段数据
     */
    @Synchronized
    fun readRecent(durationUs: Long): AudioSegment? {
        if (totalWritten == 0L) return null

        val endPts = writePtsUs
        val startPts = endPts - durationUs

        return readRange(maxOf(startPts, 0L), endPts)
    }

    /**
     * 获取当前缓冲区中最早可用的 PTS
     */
    @Synchronized
    fun getEarliestPtsUs(): Long {
        if (totalWritten < capacity) return 0L
        return writePtsUs - (capacity.toLong() * 1_000_000L / sampleRate)
    }

    /**
     * 获取当前写入位置对应的 PTS
     */
    @Synchronized
    fun getCurrentPtsUs(): Long = writePtsUs

    /**
     * 清空缓冲区
     */
    @Synchronized
    fun clear() {
        writePos = 0
        writePtsUs = 0L
        totalWritten = 0L
        indexCount = 0
    }

    // ==================== 私有方法 ====================

    private fun addIndexPoint(pos: Int, ptsUs: Long) {
        if (indexCount < ptsIndex.size) {
            ptsIndex[indexCount] = ptsUs
            posIndex[indexCount] = pos
            indexCount++
        }
    }

    private fun findPositionForPts(targetPtsUs: Long): Int? {
        if (indexCount == 0) return null

        // 二分查找最近的索引点
        var lo = 0
        var hi = indexCount - 1

        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (ptsIndex[mid] < targetPtsUs) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }

        // 找到最接近的位置
        if (lo > 0 && (ptsIndex[lo] - targetPtsUs) > (targetPtsUs - ptsIndex[lo - 1])) {
            lo--
        }

        // 线性插值计算精确位置
        val indexPts = ptsIndex[lo]
        val indexPos = posIndex[lo]

        val ptsDiff = targetPtsUs - indexPts
        val samplesOffset = (ptsDiff * sampleRate / 1_000_000L).toInt()

        return (indexPos + samplesOffset) % capacity
    }
}

/**
 * 音频段（带 PTS 时间戳）
 */
data class AudioSegment(
    val pcmData: FloatArray,
    val startTimeUs: Long,
    val endTimeUs: Long,
    val sampleRate: Int = 16000,
) {
    /** 音频时长（毫秒） */
    val durationMs: Long get() = (endTimeUs - startTimeUs) / 1000L

    /** 音频时长（微秒） */
    val durationUs: Long get() = endTimeUs - startTimeUs

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
