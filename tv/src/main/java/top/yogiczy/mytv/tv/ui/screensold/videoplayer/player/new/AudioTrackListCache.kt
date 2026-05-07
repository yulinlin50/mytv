package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import android.util.LruCache
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 音轨列表缓存
 * 
 * 使用内存缓存存储频道的音轨列表，避免重复解析。
 * 缓存大小根据设备内存动态调整：
 * - 设备内存 >= 512MB: 缓存50个频道
 * - 设备内存 >= 256MB: 缓存30个频道
 * - 设备内存 < 256MB: 缓存20个频道
 */
class AudioTrackListCache {
    
    private val cache: LruCache<String, List<PlayerMetadata.AudioTrack>> = LruCache(calculateOptimalCacheSize())
    private val lock = ReentrantLock()
    
    /**
     * 获取缓存的音轨列表
     * 
     * @param url 频道URL
     * @return 音轨列表，如果未缓存则返回null
     */
    fun get(url: String): List<PlayerMetadata.AudioTrack>? {
        return lock.withLock {
            cache.get(url)
        }
    }
    
    /**
     * 缓存音轨列表
     * 
     * @param url 频道URL
     * @param tracks 音轨列表
     */
    fun put(url: String, tracks: List<PlayerMetadata.AudioTrack>) {
        lock.withLock {
            cache.put(url, tracks)
        }
    }
    
    /**
     * 移除缓存的音轨列表
     * 
     * @param url 频道URL
     */
    fun remove(url: String) {
        lock.withLock {
            cache.remove(url)
        }
    }
    
    /**
     * 清空缓存
     */
    fun clear() {
        lock.withLock {
            cache.evictAll()
        }
    }
    
    /**
     * 获取缓存大小
     */
    fun size(): Int {
        return lock.withLock {
            cache.size()
        }
    }
    
    companion object {
        /**
         * 根据设备内存计算最优缓存大小
         */
        private fun calculateOptimalCacheSize(): Int {
            val maxMemoryMB = Runtime.getRuntime().maxMemory() / 1024 / 1024
            return when {
                maxMemoryMB >= 512 -> 50
                maxMemoryMB >= 256 -> 30
                else -> 20
            }
        }
    }
}
