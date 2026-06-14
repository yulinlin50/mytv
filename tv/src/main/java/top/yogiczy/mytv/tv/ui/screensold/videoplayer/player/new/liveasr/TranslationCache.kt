package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import java.util.concurrent.ConcurrentHashMap

/**
 * 翻译结果缓存
 *
 * 避免重复翻译相同文本，节省 API 调用和计算资源。
 * - LRU 策略：缓存满时淘汰最久未使用的条目
 * - 线程安全：ConcurrentHashMap
 * - 过期清理：缓存条目超过 TTL 后自动失效
 */
class TranslationCache(
    /** 最大缓存条目数 */
    private val maxSize: Int = 200,
    /** 缓存有效期（毫秒），默认 30 分钟 */
    private val ttlMs: Long = 30 * 60 * 1000L,
) {
    private data class CacheEntry(
        val translatedText: String,
        val timestampMs: Long = System.currentTimeMillis(),
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val accessOrder = LinkedHashSet<String>()

    /**
     * 获取翻译结果
     *
     * @return 缓存的翻译结果，未命中返回 null
     */
    fun get(text: String, sourceLang: String, targetLang: String): String? {
        val key = buildKey(text, sourceLang, targetLang)
        val entry = cache[key] ?: return null

        // 检查是否过期
        if (System.currentTimeMillis() - entry.timestampMs > ttlMs) {
            cache.remove(key)
            accessOrder.remove(key)
            return null
        }

        // 更新访问顺序
        synchronized(accessOrder) {
            accessOrder.remove(key)
            accessOrder.add(key)
        }

        return entry.translatedText
    }

    /**
     * 存储翻译结果
     */
    fun put(text: String, sourceLang: String, targetLang: String, translatedText: String) {
        val key = buildKey(text, sourceLang, targetLang)

        // 淘汰策略
        synchronized(accessOrder) {
            if (cache.size >= maxSize && !cache.containsKey(key)) {
                val oldest = accessOrder.firstOrNull()
                if (oldest != null) {
                    cache.remove(oldest)
                    accessOrder.remove(oldest)
                }
            }
            accessOrder.add(key)
        }

        cache[key] = CacheEntry(translatedText)
    }

    /**
     * 清空缓存
     */
    fun clear() {
        cache.clear()
        accessOrder.clear()
    }

    /**
     * 当前缓存大小
     */
    fun size(): Int = cache.size

    private fun buildKey(text: String, sourceLang: String, targetLang: String): String {
        return "${sourceLang}:${targetLang}:${text}"
    }
}
