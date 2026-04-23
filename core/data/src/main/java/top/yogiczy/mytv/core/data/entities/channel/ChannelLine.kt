package top.yogiczy.mytv.core.data.entities.channel

import kotlinx.serialization.Serializable

/**
 * 频道线路
 */
@Serializable
data class ChannelLine(
    val url: String = "",
    val httpUserAgent: String? = null,
    val name: String? = if (url.contains("$")) url.split("$").lastOrNull() else null,
    val manifestType: String? = null,
    val licenseType: String? = null,
    val licenseKey: String? = null,
    // 回放相关属性（KODI/APTV标准）
    val catchup: String? = null,
    val catchupSource: String? = null,
    val catchupDays: Int? = null,
    val timeshift: Int? = null,
    // 时区偏移（小时），用于EPG时间校正
    val tvgShift: Double? = null,
) {

    val playableUrl: String
        get() = url.substringBefore("$").let {
            // 修复部分链接无法播放，实际请求时?将去掉，导致链接无法访问，因此加上一个t
            if (url.endsWith("?")) "${it}t" else it
        }

    /**
     * 检查是否支持回看（综合检查所有回看相关属性）
     */
    fun hasCatchupSupport(): Boolean {
        return !catchup.isNullOrBlank() ||
                !catchupSource.isNullOrBlank() ||
                (catchupDays != null && catchupDays > 0) ||
                (timeshift != null && timeshift > 0)
    }

    companion object {
        val EXAMPLE =
            ChannelLine(
                url = "http://1.2.3.4\$LR•IPV6『线路1』",
                httpUserAgent = "okhttp",
            )
    }
}