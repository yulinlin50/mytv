package top.yogiczy.mytv.core.data.entities.iptvsource

import kotlinx.serialization.Serializable
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Globals
import java.io.File
import java.security.MessageDigest

/**
 *  直播源
 */
@Serializable
data class IptvSource(
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
     * 转换js
     */
    val transformJs: String? = null,

    /**
     * 自定义User-Agent
     */
    val userAgent: String? = null,

    /**
     * 缓存时间（毫秒），null表示使用全局设置
     */
    val cacheTime: Long? = null,

    /**
     * 关联的节目单源
     */
    val epgSource: EpgSource? = null,

    /**
     * 是否启用
     */
    val enabled: Boolean = true,

    /**
     * 唯一标识符
     */
    val id: String = generateCacheHash("$name@$url"),
) {
    /**
     * 生成缓存文件名，使用SHA-256哈希避免冲突
     */
    fun cacheFileName(ext: String): String {
        val hash = generateCacheHash(url)
        return "${cacheDir.name}/iptv_source_$hash.$ext"
    }

    fun getEffectiveCacheTime(): Long {
        return cacheTime ?: Constants.IPTV_SOURCE_CACHE_TIME
    }

    fun stableId(): String {
        return id
    }

    companion object {
        val cacheDir by lazy { File(Globals.cacheDir, "iptv_source_cache") }

        /**
         * 使用SHA-256生成可靠的缓存哈希值
         * 相比hashCode()，SHA-256具有极低的碰撞概率
         */
        fun generateCacheHash(url: String): String {
            return MessageDigest.getInstance("SHA-256")
                .digest(url.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(16) // 取前16位，既保证唯一性又控制文件名长度
        }

        val EXAMPLE = IptvSource(
            name = "测试直播源1",
            url = "http://1.2.3.4/tv.txt",
            transformJs = "",
        )

        val EXAMPLE_LOCAL = IptvSource(
            name = "测试本地直播源",
            url = "/path/Download/tv.txt",
            isLocal = true,
        )

        val EXAMPLE_WITH_EPG = IptvSource(
            name = "测试直播源(带节目单)",
            url = "http://1.2.3.4/tv.txt",
            epgSource = EpgSource(
                name = "配套节目单",
                url = "http://1.2.3.4/epg.xml",
            ),
        )

        fun IptvSource.needExternalStoragePermission(): Boolean {
            return this.isLocal && !this.url.startsWith(Globals.fileDir.path)
        }
    }
}