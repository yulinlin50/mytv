package top.yogiczy.mytv.core.data.repositories.epg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.Response
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.network.HttpException
import top.yogiczy.mytv.core.data.network.requestEpg
import top.yogiczy.mytv.core.data.repositories.FileCacheRepository
import top.yogiczy.mytv.core.data.repositories.epg.fetcher.DefaultEpgFetcher
import top.yogiczy.mytv.core.data.repositories.epg.fetcher.EpgFetcher
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Logger
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTimedValue

/**
 * 优化的节目单仓库
 * 支持 ETag、Last-Modified 等缓存优化策略
 */
class EpgRepositoryOptimized(
    private val source: EpgSource,
    private val cacheExpireHours: Int = Constants.EPG_REFRESH_TIME_THRESHOLD
) : FileCacheRepository(source.cacheFileName("json")) {

    private val log = Logger.create("EpgRepositoryOptimized")
    private val epgXmlRepository = EpgXmlRepositoryOptimized(source, cacheExpireHours)

    private fun isExpired(lastModified: Long, expireHours: Int): Boolean {
        val expireMs = expireHours * 60 * 60 * 1000L
        return System.currentTimeMillis() - lastModified >= expireMs
    }

    private suspend fun refresh(): String {
        log.i("开始刷新节目单缓存（${source.name}）")
        val xml = epgXmlRepository.getXml()
        
        // 使用带统计信息的解析
        val (epgList, stats) = EpgParser.fromXmlWithStats(xml)
        
        if (epgList.isEmpty()) throw Exception("获取节目单为空")
        
        // 记录解析统计
        log.i("节目单解析统计: 频道=${stats.totalChannels}, 节目=${stats.totalProgrammes}, " +
              "无效=${stats.invalidProgrammes}, 修复=${stats.fixedProgrammes}")

        return Globals.json.encodeToString(epgList)
    }

    /**
     * 获取节目单列表
     */
    suspend fun getEpgList(): EpgList = withContext(Dispatchers.Default) {
        val effectiveExpireHours = source.expireHours ?: cacheExpireHours
        try {
            val jsonData = getOrRefresh({ lastModified, data ->
                if (source.isLocal) false
                else if (data.isNullOrBlank()) true
                else isExpired(lastModified, effectiveExpireHours)
            }) { refresh() }

            return@withContext Globals.json.decodeFromString<EpgList>(jsonData).also { epgList ->
                log.i("加载节目单（${source.name}）：${epgList.size}个频道，${epgList.sumOf { it.programmeList.size }}个节目")
            }
        } catch (ex: Exception) {
            log.e("加载节目单（${source.name}）失败", ex)
            throw ex
        }
    }
    
    /**
     * 强制刷新节目单
     */
    suspend fun forceRefresh(): EpgList = withContext(Dispatchers.Default) {
        log.i("强制刷新节目单（${source.name}）")
        epgXmlRepository.clearCache()
        clearCache()
        getEpgList()
    }
    
    /**
     * 检查是否需要刷新
     */
    fun needsRefresh(): Boolean {
        if (source.isLocal) return false
        val effectiveExpireHours = source.expireHours ?: cacheExpireHours
        return isExpired(lastModified(), effectiveExpireHours)
    }

    override suspend fun clearCache() {
        epgXmlRepository.clearCache()
        super.clearCache()
    }
}

/**
 * 优化的节目单 XML 获取
 * 支持 HTTP 缓存头（ETag、Last-Modified）
 */
private class EpgXmlRepositoryOptimized(
    private val source: EpgSource,
    private val cacheExpireHours: Int
) : FileCacheRepository(
    if (source.isLocal) source.url else source.cacheFileName("xml"),
    source.isLocal,
) {

    private val log = Logger.create("EpgXmlRepositoryOptimized")
    
    companion object {
        private val xmlSemaphores = ConcurrentHashMap<String, Semaphore>()
        
        private fun getSemaphoreForFile(filePath: String): Semaphore {
            return xmlSemaphores.getOrPut(filePath) { Semaphore(1) }
        }
    }
    
    private val metaFile: File by lazy {
        File(getCacheFile().parent, "${getCacheFile().name}.meta")
    }
    
    private data class CacheMeta(
        val etag: String? = null,
        val lastModified: String? = null,
        val cachedAt: Long = 0
    )
    
    private fun readMeta(): CacheMeta {
        return try {
            if (metaFile.exists()) {
                val lines = metaFile.readLines()
                CacheMeta(
                    etag = lines.getOrNull(0)?.ifBlank { null },
                    lastModified = lines.getOrNull(1)?.ifBlank { null },
                    cachedAt = lines.getOrNull(2)?.toLongOrNull() ?: 0
                )
            } else {
                CacheMeta()
            }
        } catch (e: Exception) {
            CacheMeta()
        }
    }
    
    private fun writeMeta(meta: CacheMeta) {
        try {
            metaFile.parentFile?.mkdirs()
            metaFile.writeText("${meta.etag ?: ""}\n${meta.lastModified ?: ""}\n${meta.cachedAt}")
        } catch (e: Exception) {
            log.w("写入缓存元数据失败: ${e.message}")
        }
    }

    private fun isExpired(lastModified: Long, expireHours: Int): Boolean {
        val expireMs = expireHours * 60 * 60 * 1000L
        return System.currentTimeMillis() - lastModified >= expireMs
    }

    suspend fun getXml(): InputStream {
        val semaphore = getSemaphoreForFile(getCacheFile().absolutePath)
        
        return semaphore.withPermit {
            getXmlInternal()
        }
    }
    
    private suspend fun getXmlInternal(): InputStream {
        val meta = readMeta()
        val effectiveExpireHours = source.expireHours ?: cacheExpireHours
        
        val cacheFile = getCacheFile()
        if (!cacheFile.exists() && metaFile.exists()) {
            metaFile.delete()
        }
        val canUseCache = cacheFile.exists() && 
            !isExpired(cacheFile.lastModified(), effectiveExpireHours)
        
        if (canUseCache && !source.isLocal) {
            try {
                val t = measureTimedValue {
                    source.url.requestEpg(
                        builder = { builder ->
                            var newBuilder = builder
                            meta.etag?.let {
                                newBuilder = newBuilder.header("If-None-Match", it)
                                log.d("使用 ETag: $it")
                            }
                            meta.lastModified?.let {
                                newBuilder = newBuilder.header("If-Modified-Since", it)
                                log.d("使用 Last-Modified: $it")
                            }
                            newBuilder
                        }
                    ) { response: Response, request ->
                        if (response.code == 304) {
                            log.i("节目单未修改（304），使用缓存")
                            writeMeta(meta.copy(cachedAt = System.currentTimeMillis()))
                            return@requestEpg null
                        }
                        
                        val newMeta = CacheMeta(
                            etag = response.header("ETag"),
                            lastModified = response.header("Last-Modified"),
                            cachedAt = System.currentTimeMillis()
                        )
                        writeMeta(newMeta)
                        
                        log.d("收到响应: ETag=${newMeta.etag}, Last-Modified=${newMeta.lastModified}")
                        
                        val fetcher = EpgFetcher.instances.firstOrNull { 
                            it.isSupport(request.url.toString()) 
                        } ?: DefaultEpgFetcher()
                        val body = response.body ?: throw HttpException("节目单响应体为空", null)
                        fetcher.fetch(body)
                    }
                }
                
                val responseValue = t.value
                if (responseValue == null) {
                    return getCacheInputStream()
                        ?: throw HttpException("缓存数据不存在", null)
                }

                log.i("获取节目单（${source.name}）xml成功", null, t.duration)

                setCacheInputStream(responseValue)
                return getCacheInputStream()
                    ?: throw HttpException("缓存数据不存在", null)
                    
            } catch (ex: Exception) {
                log.w("获取节目单失败，尝试使用缓存: ${ex.message}")
                val cachedStream = getCacheInputStream()
                if (cachedStream != null) {
                    log.i("使用缓存的节目单数据")
                    return cachedStream
                }
                throw HttpException("获取节目单xml失败，请检查网络连接", ex)
            }
        }
        
        return getOrRefreshInputStream(
            { lastModified, _ -> 
                if (source.isLocal) false 
                else isExpired(lastModified, effectiveExpireHours) 
            }
        ) {
            log.i("开始获取节目单（${source.name}）xml: ${source.url}")

            try {
                val t = measureTimedValue {
                    source.url.requestEpg { response, request ->
                        val newMeta = CacheMeta(
                            etag = response.header("ETag"),
                            lastModified = response.header("Last-Modified"),
                            cachedAt = System.currentTimeMillis()
                        )
                        writeMeta(newMeta)
                        
                        val fetcher = EpgFetcher.instances.firstOrNull { 
                            it.isSupport(request.url.toString()) 
                        } ?: DefaultEpgFetcher()
                        val body = response.body ?: throw HttpException("节目单响应体为空", null)
                        fetcher.fetch(body)
                    }
                }
                log.i("获取节目单（${source.name}）xml成功", null, t.duration)
                t.value
            } catch (ex: Exception) {
                log.e("获取节目单（${source.name}）xml失败", ex)
                throw HttpException("获取节目单xml失败，请检查网络连接", ex)
            }
        }
    }

    override suspend fun clearCache() {
        if (source.isLocal) return
        try {
            if (metaFile.exists()) {
                metaFile.delete()
            }
        } catch (e: Exception) {
            log.w("删除缓存元数据失败: ${e.message}")
        }
        super.clearCache()
    }
}
