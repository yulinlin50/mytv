package top.yogiczy.mytv.core.data.utils

import androidx.collection.LruCache
import java.util.concurrent.ConcurrentHashMap

/**
 * LRU 缓存，支持时间戳过期机制
 */
class LruMutableCache<K : Any, V : Any>(maxSize: Int) : LruCache<K, V>(maxSize) {

    // 存储值及其时间戳，使用ConcurrentHashMap保证线程安全
    private val timestampMap = ConcurrentHashMap<K, Long>()

    // 缓存有效期（毫秒），默认 30 分钟
    var expiryTimeMs: Long = 30 * 60 * 1000

    fun putTimestamped(key: K, value: V): V? {
        timestampMap[key] = System.currentTimeMillis()
        return super.put(key, value)
    }

    fun getTimestamped(key: K): V? {
        val timestamp = timestampMap[key]
        if (timestamp != null) {
            val now = System.currentTimeMillis()
            if (now - timestamp >= expiryTimeMs) {
                removeTimestamped(key)
                return null
            }
        }
        return super.get(key)
    }

    fun removeTimestamped(key: K): V? {
        timestampMap.remove(key)
        return super.remove(key)
    }

    override fun entryRemoved(evicted: Boolean, key: K, oldValue: V, newValue: V?) {
        if (!evicted) {
            timestampMap.remove(key)
        }
        super.entryRemoved(evicted, key, oldValue, newValue)
    }

    fun getOrPut(key: K, defaultValue: () -> V): V {
        val cachedValue = getTimestamped(key)
        if (cachedValue != null) {
            return cachedValue
        }

        return defaultValue().also { newValue ->
            putTimestamped(key, newValue)
        }
    }

    /**
     * 清除所有缓存
     */
    fun clearAll() {
        timestampMap.clear()
        evictAll()
    }

    /**
     * 获取缓存大小（不检查过期）
     */
    fun getTimestampedSize(): Int {
        return timestampMap.size
    }
}
