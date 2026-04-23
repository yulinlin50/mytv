package top.yogiczy.mytv.tv.ui.screen.main

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.entities.channel.ChannelFavoriteList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.channel.ChannelLineList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.match
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.network.HttpException
import top.yogiczy.mytv.core.data.repositories.epg.EpgRepositoryOptimized
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.utils.ChannelAlias
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.sync.CloudSync
import top.yogiczy.mytv.tv.sync.CloudSyncData
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.util.Calendar

class MainViewModel : ViewModel() {
    private val log = Logger.create("MainViewModel")

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var _lastJob: Job? = null

    var needRefresh: () -> Unit = {}

    init {
        viewModelScope.launch {
            pullCloudSyncData()
            init()
            _lastJob?.join()
            refreshOtherIptvSource()
        }
    }

    override fun onCleared() {
        super.onCleared()
        _lastJob?.cancel()
        instance = null
    }

    fun init() {
        _lastJob?.cancel()
        _lastJob = viewModelScope.launch {
            ChannelAlias.refresh()
            refreshChannel()
            refreshEpg()
            mergeEpgMetadata()
        }
    }

    private suspend fun pullCloudSyncData() {
        if (!Configs.cloudSyncAutoPull) return

        _uiState.value = MainUiState.Loading("拉取云端数据")
        runCatching {
            val syncData = CloudSync.pull()

            if (syncData != CloudSyncData.EMPTY) {
                syncData.apply()
                needRefresh()
            }
        }
    }

    private fun getEnabledIptvSourceList(): IptvSourceList {
        val customSources = Configs.iptvSourceList.filter { it.enabled }
        val defaultSources = Constants.IPTV_SOURCE_LIST
        return IptvSourceList((defaultSources + customSources).distinctBy { it.hashCode() })
    }

    private suspend fun refreshChannel() {
        _uiState.value = MainUiState.Loading("加载直播源")

        val enabledSources = getEnabledIptvSourceList()
        if (enabledSources.isEmpty()) {
            _uiState.value = MainUiState.Error("没有启用的直播源")
            return
        }

        flow {
            val results = withContext(Dispatchers.IO) {
                enabledSources.mapIndexed { index, source ->
                    async {
                        try {
                            val cacheTimeMs = if (Configs.iptvSourceCacheEnable) {
                                Configs.iptvSourceCacheTimeHours * 60 * 60 * 1000L
                            } else 0L
                            Pair(source, IptvRepository(source).getChannelGroupList(cacheTimeMs))
                        } catch (e: Exception) {
                            log.e("加载直播源（${source.name}）失败", e)
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            val mergedGroupList = mergeChannelGroupLists(results)
            emit(mergedGroupList)
        }
            .catch {
                _uiState.value = MainUiState.Error(it.message)
            }
            .map { mergeSimilarChannel(it) }
            .map { hybridChannel(it) }
            .map { groupList ->
                _uiState.value = MainUiState.Ready(
                    channelGroupList = groupList,
                    filteredChannelGroupList = withContext(Dispatchers.Default) {
                        ChannelGroupList(groupList.filter { it.name !in Configs.iptvChannelGroupHiddenList })
                            .withMetadata()
                    }
                )
                groupList
            }
            .map { refreshChannelFavoriteList(enabledSources, it) }
            .collect()
    }

    private fun mergeChannelGroupLists(results: List<Pair<IptvSource, ChannelGroupList>>): ChannelGroupList {
        val groupMap = mutableMapOf<String, MutableList<top.yogiczy.mytv.core.data.entities.channel.Channel>>()

        results.forEach { (source, groupList) ->
            groupList.forEach { group ->
                val existingChannels = groupMap.getOrPut(group.name) { mutableListOf() }
                group.channelList.forEach { channel ->
                    val existingChannel = existingChannels.find { it.name == channel.name }
                    if (existingChannel != null) {
                        existingChannels[existingChannels.indexOf(existingChannel)] = existingChannel.copy(
                            lineList = ChannelLineList(existingChannel.lineList + channel.lineList)
                        )
                    } else {
                        existingChannels.add(channel.copy(iptvSourceId = source.id))
                    }
                }
            }
        }

        return ChannelGroupList(groupMap.map { (name, channels) ->
            top.yogiczy.mytv.core.data.entities.channel.ChannelGroup(
                name = name,
                channelList = ChannelList(channels.mapIndexed { index, channel -> 
                    channel.copy(index = index, iptvSourceId = channel.iptvSourceId)
                })
            )
        })
    }

    private suspend fun mergeSimilarChannel(channelGroupList: ChannelGroupList): ChannelGroupList =
        withContext(Dispatchers.Default) {
            if (!Configs.iptvSimilarChannelMerge) return@withContext channelGroupList

            _uiState.value = MainUiState.Loading("合并相似频道")

            return@withContext ChannelGroupList(channelGroupList.map { group ->
                group.copy(
                    channelList = ChannelList(group.channelList
                        .groupBy { channel -> channel.standardName }
                        .map { (standardName, similarChannels) ->
                            if (similarChannels.size == 1) {
                                similarChannels.first()
                            } else {
                                val firstChannel = similarChannels.first()
                                val mergedLineList = similarChannels
                                    .asSequence()
                                    .flatMap { channel ->
                                        channel.lineList.asSequence().map { line ->
                                            line.copy(name = channel.name)
                                        }
                                    }
                                    .distinctBy { it.url }
                                    .toList()

                                firstChannel.copy(
                                    name = standardName,
                                    lineList = ChannelLineList(mergedLineList)
                                )
                            }
                        }
                    )
                )
            })
        }

    private suspend fun hybridChannel(channelGroupList: ChannelGroupList) =
        withContext(Dispatchers.Default) {
            if (Configs.iptvHybridMode != Configs.IptvHybridMode.DISABLE) {
                _uiState.value = MainUiState.Loading("混合直播源")
            }

            return@withContext when (Configs.iptvHybridMode) {
                Configs.IptvHybridMode.DISABLE -> channelGroupList
                Configs.IptvHybridMode.IPTV_FIRST -> {
                    ChannelGroupList(channelGroupList.map { group ->
                        group.copy(channelList = ChannelList(group.channelList.map { channel ->
                            channel.copy(
                                lineList = ChannelLineList(
                                    channel.lineList + ChannelUtil.getHybridWebViewLines(channel.epgName)
                                )
                            )
                        }))
                    })
                }

                Configs.IptvHybridMode.HYBRID_FIRST -> {
                    ChannelGroupList(channelGroupList.map { group ->
                        group.copy(channelList = ChannelList(group.channelList.map { channel ->
                            channel.copy(
                                lineList = ChannelLineList(
                                    ChannelUtil.getHybridWebViewLines(channel.epgName) + channel.lineList
                                )
                            )
                        }))
                    })
                }
            }
        }

    private suspend fun refreshEpg() {
        if (!Configs.epgEnable) return

        if (_uiState.value is MainUiState.Ready) {
            // EPG数据更新时，必须清空匹配缓存，否则将一直返回旧的节目单数据
            EpgList.clearCache()
            val channelGroupList = (_uiState.value as MainUiState.Ready).channelGroupList
            val enabledSources = getEnabledIptvSourceList()

            // 第一步：收集所有当前频道的相关信息，用于后续过滤
            val allChannels = channelGroupList.channelList
            val channelNameSet = allChannels.flatMap { channel ->
                listOfNotNull(
                    channel.name.lowercase(),
                    channel.epgName?.lowercase(),
                    channel.standardName.lowercase(),
                    channel.epgId?.lowercase()
                )
            }.toSet()

            // 第二步：收集每个 sourceId 对应的频道名称集合
            val sourceToChannelNames = allChannels
                .groupBy { it.iptvSourceId }
                .mapValues { (_, channels) ->
                    channels.flatMap { channel ->
                        listOfNotNull(
                            channel.name.lowercase(),
                            channel.epgName?.lowercase(),
                            channel.standardName.lowercase(),
                            channel.epgId?.lowercase()
                        )
                    }.toSet()
                }

            flow {
                val failedSources = mutableListOf<String>()
                
                // 第三步：准备所有需要加载的节目单源，按URL去重
                data class EpgLoadTask(
                    val epgSource: top.yogiczy.mytv.core.data.entities.epgsource.EpgSource,
                    val sourceIds: List<String> // 关联的直播源ID列表
                )
                
                val epgLoadTasks = mutableMapOf<String, EpgLoadTask>() // key: epgUrl
                
                enabledSources.forEach { source ->
                    val epgSource = source.epgSource ?: return@forEach
                    val finalEpgSource = if (Configs.epgSourceFollowIptv) {
                        val epgUrl = IptvRepository(source).getEpgUrl()
                        if (epgUrl != null) {
                            epgSource.copy(url = epgUrl)
                        } else {
                            epgSource
                        }
                    } else {
                        epgSource
                    }
                    
                    val epgUrl = finalEpgSource.url
                    epgLoadTasks[epgUrl]?.let { existingTask ->
                        // 已存在，添加当前sourceId到列表
                        epgLoadTasks[epgUrl] = existingTask.copy(
                            sourceIds = existingTask.sourceIds + source.id
                        )
                    } ?: run {
                        // 不存在，创建新任务
                        epgLoadTasks[epgUrl] = EpgLoadTask(
                            epgSource = finalEpgSource,
                            sourceIds = listOf(source.id)
                        )
                    }
                }

                // 第四步：并行加载去重后的节目单源，并添加并发控制
                val semaphore = Semaphore(2) // 限制并发数为2，避免低性能设备过载
                val epgLists = withContext(Dispatchers.IO) {
                    epgLoadTasks.values.map { task ->
                        async {
                            semaphore.withPermit {
                                try {
                                    val fullEpgList = EpgRepositoryOptimized(
                                        task.epgSource,
                                        if (Configs.epgCacheEnable) Configs.epgCacheTimeHours else 0
                                    ).getEpgList()
                                    
                                    // 第五步：过滤节目单，只保留与当前频道相关的
                                    // 注意：为了安全起见，如果过滤后为空，则使用完整列表
                                    var filteredEpgList = fullEpgList.filter { epg ->
                                        epg.channelList.any { epgChannelName ->
                                            val lowerName = epgChannelName.lowercase()
                                            // 快速检查：该节目单频道是否在任何关联的 sourceId 的频道列表中
                                            task.sourceIds.any { sourceId ->
                                                sourceToChannelNames[sourceId]?.contains(lowerName) ?: false
                                            } ||
                                            // 兜底检查：是否在全局频道列表中
                                            channelNameSet.contains(lowerName)
                                        }
                                    }
                                    
                                    // 安全检查：如果过滤后为空，回退到完整列表
                                    if (filteredEpgList.isEmpty() && fullEpgList.isNotEmpty()) {
                                        log.w("节目单过滤（${task.epgSource.name}）：过滤后为空，回退到完整列表")
                                        filteredEpgList = fullEpgList
                                    }
                                    
                                    log.i("节目单过滤（${task.epgSource.name}）：原始=${fullEpgList.size}个，过滤后=${filteredEpgList.size}个")
                                    
                                    Pair(task.sourceIds, EpgList(filteredEpgList))
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) {
                                        throw e
                                    }
                                    log.e("加载节目单（${task.epgSource.name}）失败", e)
                                    failedSources.add(task.epgSource.name)
                                    null
                                }
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
                
                if (failedSources.isNotEmpty() && epgLists.isEmpty()) {
                    throw Exception("所有节目单源加载失败: ${failedSources.joinToString(", ")}")
                }
                
                if (failedSources.isNotEmpty()) {
                    log.w("部分节目单加载失败: ${failedSources.joinToString(", ")}")
                }

                val mergedEpgList = EpgList(epgLists.flatMap { (sourceIds, epgList) ->
                    sourceIds.flatMap { sourceId ->
                        epgList.map { it.copy(sourceId = sourceId) }
                    }
                })
                emit(mergedEpgList)
            }
                .catch { e ->
                    // 协程取消时不更新节目单，保留原有数据
                    if (e is kotlinx.coroutines.CancellationException) {
                        // 重新抛出，让协程正常结束
                        throw e
                    }
                    // 其他错误时，显示错误提示并发出空的 EpgList
                    Snackbar.show("节目单获取失败，请检查网络连接", type = SnackbarType.ERROR)
                    emit(EpgList())
                }
                .map { epgList ->
                    _uiState.value = (_uiState.value as MainUiState.Ready).copy(epgList = epgList)
                }
                .collect()
        }
    }

    private suspend fun mergeEpgMetadata() = withContext(Dispatchers.Default) {
        if (_uiState.value is MainUiState.Ready) {
            val channelGroupList = (_uiState.value as MainUiState.Ready).channelGroupList
            val epgList = (_uiState.value as MainUiState.Ready).epgList

            // 预热 EPG 匹配缓存，防止在 UI 渲染阶段(Composition)阻塞主线程
            val hasLogo = epgList.any { epg -> epg.logo != null }
            
            val newChannelGroupList = ChannelGroupList(channelGroupList.map { group ->
                group.copy(channelList = ChannelList(group.channelList.map { channel ->
                    val matchedEpg = epgList.match(channel)
                    // 修改：如果 matchedEpg?.logo 为空，应保留频道原有的 logo 引用，避免 EPG 加载失败导致频道图标丢失
                    if (hasLogo && matchedEpg?.logo != null) {
                        channel.copy(logo = matchedEpg.logo)
                    } else {
                        channel.copy(logo = channel.logo) // 显式保留原有logo
                    }
                }))
            })

            _uiState.value = (_uiState.value as MainUiState.Ready).copy(
                channelGroupList = newChannelGroupList,
                filteredChannelGroupList = ChannelGroupList(newChannelGroupList.filter { it.name !in Configs.iptvChannelGroupHiddenList }).withMetadata(),
                epgList = epgList,
            )
        }
    }

    private suspend fun refreshOtherIptvSource() {
        val enabledSources = getEnabledIptvSourceList()
        val needRefreshNames = Configs.iptvChannelFavoriteList.map { it.iptvSourceName }.distinct()
            .filter { sourceName -> enabledSources.none { it.name == sourceName } }

        enabledSources
            .filter { it.name in needRefreshNames }
            .forEach { iptvSource ->
                runCatching {
                    val channelGroupList =
                        IptvRepository(iptvSource).getChannelGroupList(0)

                    refreshChannelFavoriteList(enabledSources, channelGroupList)
                }
            }
    }

    private suspend fun refreshChannelFavoriteList(
        iptvSourceList: IptvSourceList,
        channelGroupList: ChannelGroupList,
    ) = withContext(Dispatchers.Default) {
        Configs.iptvChannelFavoriteList =
            ChannelFavoriteList(Configs.iptvChannelFavoriteList.map { channelFavorite ->
                if (iptvSourceList.none { it.name == channelFavorite.iptvSourceName }) return@map channelFavorite

                val newChannel = channelGroupList
                    .firstOrNull { group -> group.name == channelFavorite.groupName }?.channelList
                    ?.firstOrNull { channel -> channel.name == channelFavorite.channel.name }

                channelFavorite.copy(channel = newChannel ?: channelFavorite.channel)
            })
        needRefresh()
    }

    companion object {
        var instance: MainViewModel? = null
    }
}

sealed interface MainUiState {
    data class Loading(val message: String? = null) : MainUiState
    data class Error(val message: String? = null) : MainUiState
    data class Ready(
        val channelGroupList: ChannelGroupList = ChannelGroupList(),
        val filteredChannelGroupList: ChannelGroupList = ChannelGroupList(),
        val epgList: EpgList = EpgList(),
    ) : MainUiState
}

val mainVM: MainViewModel
    @Composable get() = MainViewModel.instance ?: viewModel<MainViewModel>().also {
        MainViewModel.instance = it
    }
