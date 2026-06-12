package top.yogiczy.mytv.core.data.entities.epgsource

import kotlinx.serialization.Serializable
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Globals
import java.io.File
import java.security.MessageDigest

/**
 * 节目单来源
 */
@Serializable
data class EpgSource(
    /**
     * 名称
     */
    val name: String = "",

    /**
     * 链接
     */
    val url: String = "",

    /**
     * 是否本地
     */
    val isLocal: Boolean = false,

    /**
     * 过期时间（小时），null表示使用全局设置
     */
    val expireHours: Int? = null,
) {

    fun cacheFileName(ext: String): String {
        val hash = generateCacheHash(url)
        return "${cacheDir.name}/epg_source_$hash.$ext"
    }

    fun getEffectiveExpireHours(): Int {
        return expireHours ?: Constants.EPG_REFRESH_TIME_THRESHOLD
    }

    companion object {
        val cacheDir by lazy { File(Globals.cacheDir, "epg_source_cache") }

        fun generateCacheHash(url: String): String {
            return MessageDigest.getInstance("SHA-256")
                .digest(url.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(16)
        }

        val EXAMPLE = EpgSource(
            name = "测试节目单1",
            url = "http://1.2.3.4/all.xml",
        )

        val EXAMPLE_LOCAL = EpgSource(
            name = "测试本地节目单",
            url = "/path/Download/epg.xml",
            isLocal = true,
        )

        fun EpgSource.needExternalStoragePermission(): Boolean {
            return this.isLocal && !this.url.startsWith(Globals.fileDir.path)
        }
    }
}