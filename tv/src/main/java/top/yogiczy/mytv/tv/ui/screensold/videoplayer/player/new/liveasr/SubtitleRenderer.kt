package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import androidx.media3.common.text.Cue

/**
 * 字幕渲染器
 *
 * 管理字幕的显示逻辑：
 * - 双行字幕：原文 + 译文
 * - 自动消失：final 字幕显示指定时长后自动清除
 * - Partial → Final 过渡：流式识别时实时更新 partial，final 后替换为翻译结果
 */
class SubtitleRenderer(
    /** Final 字幕显示时长（毫秒） */
    private val displayDurationMs: Long = 5000L,
) {
    /** 字幕条目 */
    data class SubtitleEntry(
        val originalText: String,
        val translatedText: String? = null,
        val isFinal: Boolean = true,
        val startTimeMs: Long = System.currentTimeMillis(),
        val endTimeMs: Long = System.currentTimeMillis() + displayDurationMs,
    )

    /** 当前显示的字幕列表 */
    private val entries = mutableListOf<SubtitleEntry>()

    /** 当前 partial 文本 */
    private var partialText: String? = null

    /**
     * 更新 partial 结果（流式识别中间结果）
     */
    fun updatePartial(text: String) {
        partialText = text
    }

    /**
     * 添加 final 字幕
     */
    fun addFinal(originalText: String, translatedText: String?) {
        partialText = null
        entries.add(
            SubtitleEntry(
                originalText = originalText,
                translatedText = translatedText,
                isFinal = true,
                startTimeMs = System.currentTimeMillis(),
                endTimeMs = System.currentTimeMillis() + displayDurationMs,
            )
        )

        // 限制最大显示条数
        while (entries.size > 3) {
            entries.removeAt(0)
        }
    }

    /**
     * 生成当前应显示的 Cue 列表
     */
    fun buildCues(): List<Cue> {
        val now = System.currentTimeMillis()

        // 清除过期字幕
        entries.removeAll { it.isFinal && now > it.endTimeMs }

        val cues = mutableListOf<Cue>()

        // 添加 final 字幕
        for (entry in entries) {
            val displayText = if (entry.translatedText != null) {
                "${entry.originalText}\n${entry.translatedText}"
            } else {
                entry.originalText
            }
            cues.add(Cue.Builder().setText(displayText).build())
        }

        // 添加 partial 文本
        partialText?.let { partial ->
            cues.add(Cue.Builder().setText(partial).build())
        }

        return cues
    }

    /**
     * 清除所有字幕
     */
    fun clear() {
        entries.clear()
        partialText = null
    }
}
