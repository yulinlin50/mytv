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
        val (epgList, stats) = EpgParser.fromXmlWithStats(xml)
        if (epgList.isEmpty()) throw Exception("获取节目单为空")

        log.i("节目单解析统计: 频道=${stats.totalChannels}, 节目=${stats.totalProgrammes}, " +
              "无效=${stats.invalidProgrammes}, 修复=${stats.fixedProgrammes}")

        return Globals.json.encodeToString(epgList)
    }

    suspend fun getEpgList(): EpgList = withContext(Dispatchers.Default) {
        val effectiveExpireHours = source.expireHours ?: cacheExpireHours
        try {
            val jsonData = getOrRefresh({ lastModified, data ->
                if (source.isLocal) false
                else if (data.isNullOrBlank()) true
                else isExpired(lastModified, effectiveExpireHours)
            }) { refresh() }

            Globals.json.decodeFromString<EpgList>(jsonData).also { epgList ->
                log.i("加载节目单（${source.name}）：${epgList.size}个频道，${epgList.sumOf { it.programmeList.size }}个节目")
            }
        } catch (ex: Exception) {
            log.e("加载节目单（${source.name}）失败", ex)
            throw ex
        }
    }

    suspend fun forceRefresh(): EpgList = withContext(Dispatchers.Default) {
        log.i("强制刷新节目单（${source.name}）")
        epgXmlRepository.clearCache()
        clearCache()
        getEpgList()
    }

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
        private fun getSemaphoreForFile(filePath: String) = xmlSemaphores.getOrPut(filePath) { Semaphore(1) }
    }

    private val metaFile: File by lazy { File(getCacheFile().parent, "${getCacheFile().name}.meta") }

    private data class CacheMeta(
        val etag: String? = null,
        val lastModified: String? = null,
        val cachedAt: Long = 0
    )

    private fun readMeta(): CacheMeta = runCatching {
        if (!metaFile.exists()) return CacheMeta()
        val lines = metaFile.readLines()
        CacheMeta(
            etag = lines.getOrNull(0)?.ifBlank { null },
            lastModified = lines.getOrNull(1)?.ifBlank { null },
            cachedAt = lines.getOrNull(2)?.toLongOrNull() ?: 0
        )
    }.getOrElse { CacheMeta() }

    private fun writeMeta(meta: CacheMeta) = runCatching {
        metaFile.parentFile?.mkdirs()
        metaFile.writeText("${meta.etag ?: ""}\n${meta.lastModified ?: ""}\n${meta.cachedAt}")
    }.onFailure { log.w("写入缓存元数据失败: ${it.message}") }

    private fun isExpired(lastModified: Long, expireHours: Int): Boolean {
        val expireMs = expireHours * 60 * 60 * 1000L
        return System.currentTimeMillis() - lastModified >= expireMs
    }

    suspend fun getXml(): InputStream = getSemaphoreForFile(getCacheFile().absolutePath).withPermit { getXmlInternal() }

    private suspend fun getXmlInternal(): InputStream {
        val meta = readMeta()
        val effectiveExpireHours = source.expireHours ?: cacheExpireHours
        val cacheFile = getCacheFile()

        if (!cacheFile.exists() && metaFile.exists()) metaFile.delete()
        val canUseCache = cacheFile.exists() && !isExpired(cacheFile.lastModified(), effectiveExpireHours)

        // 如果缓存有效且非本地源，尝试条件请求（ETag/Last-Modified）
        if (canUseCache && !source.isLocal) {
            try {
                val t = measureTimedValue {
                    source.url.requestEpg(
                        builder = { builder ->
                            meta.etag?.let { builder.header("If-None-Match", it) }
                            meta.lastModified?.let { builder.header("If-Modified-Since", it) }
                            builder
                        }
                    ) { response: Response, request ->
                        if (response.code == 304) {
                            writeMeta(meta.copy(cachedAt = System.currentTimeMillis()))
                            return@requestEpg null
                        }
                        writeMeta(CacheMeta(
                            etag = response.header("ETag"),
                            lastModified = response.header("Last-Modified"),
                            cachedAt = System.currentTimeMillis()
                        ))
                        fetchEpgBody(response, request)
                    }
                }

                t.value?.let { responseValue ->
                    log.i("获取节目单（${source.name}）xml成功", null, t.duration)
                    setCacheInputStream(responseValue)
                    return getCacheInputStream() ?: throw HttpException("缓存数据不存在", null)
                } ?: run {
                    // 304 Not Modified
                    return getCacheInputStream() ?: throw HttpException("缓存数据不存在", null)
                }
            } catch (ex: Exception) {
                log.w("获取节目单失败，尝试使用缓存: ${ex.message}")
                getCacheInputStream()?.let { return it }
                throw HttpException("获取节目单xml失败，请检查网络连接", ex)
            }
        }

        // 缓存过期或本地源：走标准刷新流程
        return getOrRefreshInputStream(
            { lastModified, _ ->
                if (source.isLocal) false else isExpired(lastModified, effectiveExpireHours)
            }
        ) {
            log.i("开始获取节目单（${source.name}）xml: ${source.url}")
            try {
                val t = measureTimedValue {
                    source.url.requestEpg { response, request ->
                        writeMeta(CacheMeta(
                            etag = response.header("ETag"),
                            lastModified = response.header("Last-Modified"),
                            cachedAt = System.currentTimeMillis()
                        ))
                        fetchEpgBody(response, request)
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

    private suspend fun fetchEpgBody(response: Response, request: okhttp3.Request): InputStream {
        val fetcher = EpgFetcher.instances.firstOrNull { it.isSupport(request.url.toString()) }
            ?: DefaultEpgFetcher()
        val body = response.body ?: throw HttpException("节目单响应体为空", null)
        return fetcher.fetch(body)
    }

    override suspend fun clearCache() {
        if (source.isLocal) return
        runCatching { if (metaFile.exists()) metaFile.delete() }
        super.clearCache()
    }
}
