package top.yogiczy.mytv.core.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.channel.ChannelLineList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.network.HttpException
import top.yogiczy.mytv.core.data.network.request
import top.yogiczy.mytv.core.data.repositories.FileCacheRepository
import top.yogiczy.mytv.core.data.repositories.iptv.parser.IptvParser
import top.yogiczy.mytv.core.data.repositories.iptv.parser.IptvParser.ChannelItem.Companion.toChannelGroupList
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Logger
import kotlin.time.measureTimedValue

/**
 * 直播源数据获取
 */
class IptvRepository(private val source: IptvSource) :
    FileCacheRepository(source.cacheFileName("json")) {

    private val log = Logger.create("IptvRepository")
    private val rawRepository = IptvRawRepository(source)

    /**
     * 检查缓存是否过期
     * @param lastModified 缓存文件最后修改时间
     * @param cacheTime 缓存有效期（毫秒）
     * @return true表示缓存已过期需要刷新
     */
    private fun isExpired(lastModified: Long, cacheTime: Long): Boolean {
        // 本地文件永不过期（除非文件本身被修改）
        if (source.isLocal) {
            return lastModified < rawRepository.lastModified()
        }

        // 网络源：检查是否超时或原始数据是否变更
        val isTimeout = System.currentTimeMillis() - lastModified >= cacheTime
        val isRawChanged = lastModified < rawRepository.lastModified()

        return isTimeout || isRawChanged
    }

    private suspend fun refresh(
        transform: suspend ((List<IptvParser.ChannelItem>) -> List<IptvParser.ChannelItem>) = { it -> it },
    ): String {
        val raw = rawRepository.getRaw()
        val parser = IptvParser.instances.first { it.isSupport(source.url, raw) }

        log.d("开始解析直播源（${source.name}）...")
        return measureTimedValue {
            val list = parser.parse(raw)
            Globals.json.encodeToString(withContext(Dispatchers.Default) {
                runCatching { transform(list) }
                    .getOrDefault(list)
                    .toChannelGroupList()
            })
        }.let {
            log.i("解析直播源（${source.name}）完成", null, it.duration)
            it.value
        }
    }

    private suspend fun transform(channelList: List<IptvParser.ChannelItem>): List<IptvParser.ChannelItem> =
        withContext(Dispatchers.IO) {
            if (source.transformJs.isNullOrBlank()) return@withContext channelList

            // 使用try-finally确保Context.exit()一定会被调用，避免上下文泄漏
            val context = Context.enter()
            context.optimizationLevel = -1

            try {
                val scope = createSafeScope(context)
                val channelListJson = Globals.json.encodeToString(channelList)

                val result = runCatching {
                    context.evaluateString(
                        scope, """
                        (function() {
                            var channelList = $channelListJson;
                            ${source.transformJs}
                            return JSON.stringify(main(channelList));
                        })();
                        """.trimIndent(), "JavaScript", 1, null
                    ) as String
                }

                if (result.isFailure) {
                    log.e("转换直播源（${source.name}）错误: ${result.exceptionOrNull()}")
                }

                result.getOrNull()?.let { Globals.json.decodeFromString(it) } ?: channelList
            } finally {
                // 确保在任何情况下都退出Rhino上下文
                Context.exit()
            }
        }

    /**
     * 创建安全的JavaScript执行环境，移除危险对象
     * 限制JavaScript代码只能访问标准JavaScript对象，防止执行恶意代码
     */
    private fun createSafeScope(context: Context): Scriptable {
        val scope = context.initStandardObjects()

        // 移除可能用于访问Java内部类的危险对象，增强安全性
        scope.delete("Packages")
        scope.delete("getClass")
        scope.delete("java")
        scope.delete("org")
        scope.delete("com")
        scope.delete("edu")
        scope.delete("net")
        scope.delete("javax")
        scope.delete("android")

        return scope
    }

    /**
     * 获取直播源分组列表
     */
    suspend fun getChannelGroupList(cacheTime: Long): ChannelGroupList {
        val effectiveCacheTime = source.cacheTime ?: cacheTime
        try {
            val json = getOrRefresh({ lastModified, _ -> isExpired(lastModified, effectiveCacheTime) }) {
                refresh { transform(it) }
            }

            val (groupList, duration) = measureTimedValue {
                Globals.json.decodeFromString<ChannelGroupList>(json)
            }
            log.i("加载直播源（${source.name}）：${groupList.size}个分组，${groupList.sumOf { it.channelList.size }}个频道: $duration")

            // 将直播源级别的 userAgent 应用到没有自定义 UA 的频道线路
            return if (!source.userAgent.isNullOrBlank()) {
                ChannelGroupList(
                    groupList.map { group ->
                        group.copy(
                            channelList = ChannelList(
                                group.channelList.map { channel ->
                                    channel.copy(
                                        lineList = ChannelLineList(
                                            channel.lineList.map { line ->
                                                if (line.httpUserAgent.isNullOrBlank()) {
                                                    line.copy(httpUserAgent = source.userAgent)
                                                } else {
                                                    line
                                                }
                                            }
                                        )
                                    )
                                }
                            )
                        )
                    }
                )
            } else {
                groupList
            }
        } catch (ex: Exception) {
            log.e("加载直播源（${source.name}）失败", ex)
            throw ex
        }
    }

    suspend fun getEpgUrl(): String? {
        return runCatching {
            val sourceData = rawRepository.getRaw(Long.MAX_VALUE)
            val parser = IptvParser.instances.first { it.isSupport(source.url, sourceData) }
            parser.getEpgUrl(sourceData)
        }.getOrNull()
    }

    override suspend fun clearCache() {
        rawRepository.clearCache()
        super.clearCache()
    }

    companion object {
        suspend fun clearAllCache() = withContext(Dispatchers.IO) {
            IptvSource.cacheDir.deleteRecursively()
        }
    }
}

private class IptvRawRepository(private val source: IptvSource) : FileCacheRepository(
    if (source.isLocal) source.url else source.cacheFileName("txt"),
    source.isLocal,
) {

    private val log = Logger.create("IptvRawRepository")

    suspend fun getRaw(cacheTime: Long = 0): String {
        return getOrRefresh(if (source.isLocal) Long.MAX_VALUE else cacheTime) {
            log.d("获取直播源: $source")

            try {
                source.url.request(
                    builder = { builder ->
                        if (!source.userAgent.isNullOrBlank()) {
                            builder.header("User-Agent", source.userAgent)
                        }
                        builder
                    }
                ) { body -> body.string() } ?: ""
            } catch (ex: Exception) {
                log.e("获取直播源（${source.name}）失败", ex)
                throw HttpException("获取直播源失败，请检查网络连接", ex)
            }
        }
    }

    override suspend fun clearCache() {
        if (source.isLocal) return
        super.clearCache()
    }
}