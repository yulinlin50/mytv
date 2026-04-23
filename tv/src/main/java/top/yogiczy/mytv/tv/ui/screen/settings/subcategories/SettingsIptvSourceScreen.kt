package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSourceList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.util.utils.humanizeMs
import top.yogiczy.mytv.tv.ui.material.CircularProgressIndicator
import top.yogiczy.mytv.tv.ui.material.Drawer
import top.yogiczy.mytv.tv.ui.material.DrawerPosition
import top.yogiczy.mytv.tv.ui.material.EditableField
import top.yogiczy.mytv.tv.ui.material.PopupContent
import top.yogiczy.mytv.tv.ui.material.SaveCancelButtons
import top.yogiczy.mytv.tv.ui.material.SettingsActionItem
import top.yogiczy.mytv.tv.ui.material.SimplePopup
import top.yogiczy.mytv.tv.ui.material.Tag
import top.yogiczy.mytv.tv.ui.material.TagDefaults
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.components.AppScaffoldHeaderBtn
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.push.PushContent
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched
import top.yogiczy.mytv.tv.ui.utils.gridColumns
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse
import top.yogiczy.mytv.tv.ui.utils.saveRequestFocus

@Composable
fun SettingsIptvSourceScreen(
    modifier: Modifier = Modifier,
    iptvSourceListProvider: () -> IptvSourceList = { IptvSourceList() },
    epgSourceListProvider: () -> EpgSourceList = { EpgSourceList() },
    onIptvSourceListChange: (IptvSourceList) -> Unit = {},
    onAddIptvSource: (IptvSource) -> Unit = {},
    onUpdateIptvSource: (IptvSource) -> Unit = {},
    onDeleteIptvSource: (IptvSource) -> Unit = {},
    onClearCache: (IptvSource) -> Unit = {},
    onReload: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val iptvSourceList = IptvSourceList(Constants.IPTV_SOURCE_LIST + iptvSourceListProvider())

    val coroutineScope = rememberCoroutineScope()
    val iptvSourceDetails = remember { mutableStateMapOf<Int, IptvSourceDetail>() }

    suspend fun refreshAll() {
        if (iptvSourceDetails.values.any { it == IptvSourceDetail.Loading }) return

        iptvSourceDetails.clear()
        iptvSourceList.forEach { source ->
            iptvSourceDetails[source.stableId().hashCode()] = IptvSourceDetail.Loading
        }

        iptvSourceList.forEach { iptvSource ->
            try {
                val channelGroupList = IptvRepository(iptvSource).getChannelGroupList(0)
                iptvSourceDetails[iptvSource.stableId().hashCode()] = IptvSourceDetail.Ready(
                    channelGroupCount = channelGroupList.size,
                    channelCount = channelGroupList.channelList.size,
                    lineCount = channelGroupList.channelList.sumOf { it.lineList.size },
                )
            } catch (_: Exception) {
                iptvSourceDetails[iptvSource.stableId().hashCode()] = IptvSourceDetail.Error
            }
        }
    }

    AppScreen(
        modifier = modifier,
        header = { Text("设置 / 直播源 / 自定义直播源") },
        headerExtra = {
            AppScaffoldHeaderBtn(
                title = "刷新全部",
                imageVector = Icons.Default.Refresh,
                onSelect = {
                    coroutineScope.launch {
                        refreshAll()
                    }
                },
            )
        },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        SettingsIptvSourceContent(
            iptvSourceListProvider = { iptvSourceList },
            epgSourceListProvider = epgSourceListProvider,
            iptvSourceDetailsProvider = { iptvSourceDetails },
            onAddIptvSource = onAddIptvSource,
            onUpdateIptvSource = onUpdateIptvSource,
            onDeleteIptvSource = onDeleteIptvSource,
            onClearCache = onClearCache,
            onReload = onReload,
        )
    }
}

@Composable
private fun SettingsIptvSourceContent(
    modifier: Modifier = Modifier,
    iptvSourceListProvider: () -> IptvSourceList = { IptvSourceList() },
    epgSourceListProvider: () -> EpgSourceList = { EpgSourceList() },
    iptvSourceDetailsProvider: () -> Map<Int, IptvSourceDetail> = { emptyMap() },
    onAddIptvSource: (IptvSource) -> Unit = {},
    onUpdateIptvSource: (IptvSource) -> Unit = {},
    onDeleteIptvSource: (IptvSource) -> Unit = {},
    onClearCache: (IptvSource) -> Unit = {},
    onReload: () -> Unit = {},
) {
    val iptvSourceList = iptvSourceListProvider()

    val childPadding = rememberChildPadding()
    val firstItemFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // 当列表数据变化且有数据时，请求焦点到第一个项目
    LaunchedEffect(iptvSourceList.size) {
        if (iptvSourceList.isNotEmpty()) {
            kotlinx.coroutines.delay(100)
            firstItemFocusRequester.saveRequestFocus()
        }
    }

    LazyColumn(
        modifier = modifier
            .padding(top = 10.dp),
        state = listState,
        contentPadding = childPadding.copy(top = 10.dp).paddingValues,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(
            items = iptvSourceList,
            key = { index, it -> "${it.stableId()}_$index" },
        ) { _, iptvSource ->
            IptvSourceItem(
                modifier = Modifier.ifElse(
                    iptvSourceList.first() == iptvSource,
                    Modifier.focusRequester(firstItemFocusRequester),
                ),
                iptvSourceProvider = { iptvSource },
                epgSourceListProvider = epgSourceListProvider,
                iptvSourceDetailProvider = {
                    iptvSourceDetailsProvider()[iptvSource.stableId().hashCode()] ?: IptvSourceDetail.None
                },
                onUpdate = { onUpdateIptvSource(it) },
                onUpdateWithReload = { 
                    onUpdateIptvSource(it)
                    onReload()
                },
                onDelete = { onDeleteIptvSource(iptvSource) },
                onClearCache = { onClearCache(iptvSource) },
                onReload = onReload,
            )
        }

        item {
            var addPopupVisible by remember { mutableStateOf(false) }

            ListItem(
                modifier = Modifier.handleKeyEvents(onSelect = { addPopupVisible = true }),
                headlineContent = { Text("添加直播源") },
                leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                selected = false,
                onClick = {},
            )

            SimplePopup(
                visibleProvider = { addPopupVisible },
                onDismissRequest = { addPopupVisible = false },
            ) {
                AddIptvSourceContent(
                    epgSourceListProvider = epgSourceListProvider,
                    onAdd = { source ->
                        onAddIptvSource(source)
                        addPopupVisible = false
                    },
                    onDismiss = { addPopupVisible = false },
                )
            }
        }
    }
}

@Composable
private fun IptvSourceItem(
    modifier: Modifier = Modifier,
    iptvSourceProvider: () -> IptvSource = { IptvSource() },
    epgSourceListProvider: () -> EpgSourceList = { EpgSourceList() },
    iptvSourceDetailProvider: () -> IptvSourceDetail = { IptvSourceDetail.Loading },
    onUpdate: (IptvSource) -> Unit = {},
    onUpdateWithReload: (IptvSource) -> Unit = {},
    onDelete: () -> Unit = {},
    onClearCache: () -> Unit = {},
    onReload: () -> Unit = {},
) {
    val iptvSource = iptvSourceProvider()
    val iptvSourceDetail = iptvSourceDetailProvider()

    var actionsVisible by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier
            .handleKeyEvents(
                onSelect = { actionsVisible = true },
                onLongSelect = { actionsVisible = true },
            ),
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(iptvSource.name)

                Tag(
                    if (iptvSource.isLocal) "本地" else "远程",
                    colors = TagDefaults.colors(
                        containerColor = LocalContentColor.current.copy(0.1f)
                    ),
                )

                if (!iptvSource.transformJs.isNullOrEmpty()) {
                    Tag(
                        "转换JS",
                        colors = TagDefaults.colors(
                            containerColor = LocalContentColor.current.copy(0.1f)
                        ),
                    )
                }

                if (!iptvSource.userAgent.isNullOrEmpty()) {
                    Tag(
                        "自定义UA",
                        colors = TagDefaults.colors(
                            containerColor = LocalContentColor.current.copy(0.1f)
                        ),
                    )
                }

                if (iptvSource.epgSource != null) {
                    Tag(
                        "节目单",
                        colors = TagDefaults.colors(
                            containerColor = LocalContentColor.current.copy(0.1f)
                        ),
                    )
                }
            }
        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(iptvSource.url)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    iptvSource.cacheTime?.let { cacheTime ->
                        Text("缓存: ${cacheTime.humanizeMs()}")
                    }

                    if (iptvSourceDetail is IptvSourceDetail.Ready) {
                        Text(
                            listOf(
                                "共${iptvSourceDetail.channelGroupCount}个分组",
                                "${iptvSourceDetail.channelCount}个频道",
                                "${iptvSourceDetail.lineCount}条线路"
                            ).joinToString("，")
                        )
                    }
                }
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = iptvSource.enabled,
                    onCheckedChange = { enabled ->
                        onUpdate(iptvSource.copy(enabled = enabled))
                        onReload()
                    },
                    modifier = Modifier.focusable(false)
                )

                when (iptvSourceDetail) {
                    is IptvSourceDetail.Loading -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 3.dp,
                        color = LocalContentColor.current,
                        trackColor = MaterialTheme.colorScheme.surface.copy(0.1f),
                    )

                    is IptvSourceDetail.Error -> Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )

                    else -> Unit
                }
            }
        },
        selected = false,
        onClick = {},
    )

    SimplePopup(
        visibleProvider = { actionsVisible },
        onDismissRequest = { actionsVisible = false },
    ) {
        SettingsIptvSourceActions(
            iptvSourceProvider = { iptvSource },
            onDismissRequest = { actionsVisible = false },
            onEdit = {
                actionsVisible = false
                showEdit = true
            },
            onDelete = {
                onDelete()
                actionsVisible = false
            },
            onClearCache = {
                onClearCache()
                actionsVisible = false
            },
            onUpdate = { source ->
                onUpdate(source)
                actionsVisible = false
            },
            onReload = {
                onReload()
                actionsVisible = false
            },
        )
    }

    SimplePopup(
        visibleProvider = { showEdit },
        onDismissRequest = { showEdit = false },
    ) {
        IptvSourceEditContent(
            initialSource = iptvSource,
            epgSourceListProvider = epgSourceListProvider,
            onSave = { updatedSource ->
                val shouldReload = iptvSource.enabled || updatedSource.url != iptvSource.url
                val sourceToSave = updatedSource.copy(
                    enabled = true
                )
                onUpdate(sourceToSave)
                if (shouldReload) {
                    onReload()
                }
                showEdit = false
            },
            onDismiss = { showEdit = false },
        )
    }
}

@Composable
private fun SettingsIptvSourceActions(
    modifier: Modifier = Modifier,
    iptvSourceProvider: () -> IptvSource = { IptvSource() },
    onDismissRequest: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onClearCache: () -> Unit = {},
    onUpdate: (IptvSource) -> Unit = {},
    onReload: () -> Unit = {},
) {
    val iptvSource = iptvSourceProvider()

    Drawer(
        modifier = modifier.width(5.gridColumns()),
        onDismissRequest = onDismissRequest,
        position = DrawerPosition.Center,
        header = {
            Text(
                iptvSource.name,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                        SettingsActionItem(
                            title = if (iptvSource.enabled) "禁用" else "启用",
                            imageVector = if (iptvSource.enabled) Icons.Outlined.ClearAll else Icons.Default.CheckCircle,
                            onSelected = { 
                                onUpdate(iptvSource.copy(enabled = !iptvSource.enabled))
                                onReload()
                            },
                            modifier = Modifier.focusOnLaunched(),
                        )
                    }

                    item {
                        SettingsActionItem(
                            title = "编辑",
                            imageVector = Icons.Outlined.Edit,
                            onSelected = onEdit,
                        )
                    }

                    item {
                        SettingsActionItem(
                            title = "删除",
                            imageVector = Icons.Outlined.DeleteOutline,
                            onSelected = onDelete,
                        )
                    }

                    item {
                        SettingsActionItem(
                            title = "清除缓存",
                            imageVector = Icons.Outlined.ClearAll,
                            onSelected = onClearCache,
                        )
                    }

                    item {
                        SettingsActionItem(
                            title = "返回",
                            imageVector = Icons.Outlined.ArrowBackIosNew,
                            onSelected = onDismissRequest,
                        )
                    }
        }
    }
}

@Composable
private fun AddIptvSourceContent(
    modifier: Modifier = Modifier,
    epgSourceListProvider: () -> EpgSourceList = { EpgSourceList() },
    onAdd: (IptvSource) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    var showRemoteInput by remember { mutableStateOf(false) }
    var showLocalInput by remember { mutableStateOf(false) }
    var showPushQr by remember { mutableStateOf(false) }

    val remoteInputFocusRequester = remember { FocusRequester() }
    val localInputFocusRequester = remember { FocusRequester() }
    val pushQrFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showRemoteInput) {
        if (showRemoteInput) {
            kotlinx.coroutines.delay(100)
            remoteInputFocusRequester.saveRequestFocus()
        }
    }

    LaunchedEffect(showLocalInput) {
        if (showLocalInput) {
            kotlinx.coroutines.delay(100)
            localInputFocusRequester.saveRequestFocus()
        }
    }

    LaunchedEffect(showPushQr) {
        if (showPushQr) {
            kotlinx.coroutines.delay(100)
            pushQrFocusRequester.saveRequestFocus()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (!showRemoteInput && !showLocalInput && !showPushQr) {
            Box(
                modifier = modifier
                    .width(5.gridColumns())
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                    .padding(20.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier.padding(
                            top = 8.dp,
                            bottom = 16.dp,
                            start = 16.dp,
                            end = 16.dp
                        )
                    ) {
                        Text(
                            "添加直播源",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            SettingsActionItem(
                                title = "远程订阅",
                                imageVector = Icons.Outlined.Link,
                                onSelected = { showRemoteInput = true },
                                modifier = Modifier.focusOnLaunched(),
                            )
                        }

                        item {
                            SettingsActionItem(
                                title = "本地文件",
                                imageVector = Icons.Outlined.Folder,
                                onSelected = { showLocalInput = true },
                            )
                        }

                        item {
                            SettingsActionItem(
                                title = "扫码推送",
                                imageVector = Icons.Outlined.Add,
                                onSelected = { showPushQr = true },
                            )
                        }

                        item {
                            SettingsActionItem(
                                title = "返回",
                                imageVector = Icons.Outlined.ArrowBackIosNew,
                                onSelected = onDismiss,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRemoteInput) {
        IptvSourceEditContent(
            initialSource = IptvSource(isLocal = false),
            epgSourceListProvider = epgSourceListProvider,
            onSave = onAdd,
            onDismiss = { showRemoteInput = false },
        )
    }

    if (showLocalInput) {
        IptvSourceEditContent(
            initialSource = IptvSource(isLocal = true),
            epgSourceListProvider = epgSourceListProvider,
            onSave = onAdd,
            onDismiss = { showLocalInput = false },
        )
    }

    if (showPushQr) {
        PushQrContent(
            focusRequester = pushQrFocusRequester,
            onDismiss = { showPushQr = false },
        )
    }
}

@Composable
private fun PushQrContent(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onDismiss: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        focusRequester.saveRequestFocus()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = modifier
                .width(6.gridColumns())
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                .padding(20.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier.padding(
                        top = 8.dp,
                        bottom = 16.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
                ) {
                    Text(
                        "扫码推送",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PushContent()
                    Spacer(Modifier.height(16.dp))
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .focusRequester(focusRequester)
                            .handleKeyEvents(onSelect = onDismiss),
                        headlineContent = { Text("关闭", textAlign = TextAlign.Center) },
                        selected = false,
                        onClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun IptvSourceEditContent(
    modifier: Modifier = Modifier,
    initialSource: IptvSource = IptvSource(),
    epgSourceListProvider: () -> EpgSourceList = { EpgSourceList() },
    onSave: (IptvSource) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val epgSourceList = epgSourceListProvider()
    
    var name by remember { mutableStateOf(initialSource.name) }
    var url by remember { mutableStateOf(initialSource.url) }
    var userAgent by remember { mutableStateOf(initialSource.userAgent ?: "") }
    var cacheTime by remember { mutableStateOf(initialSource.cacheTime?.toString() ?: "") }
    var transformJs by remember { mutableStateOf(initialSource.transformJs ?: "") }
    var epgSourceName by remember { mutableStateOf(initialSource.epgSource?.name ?: "") }
    var epgSourceUrl by remember { mutableStateOf(initialSource.epgSource?.url ?: "") }
    var showEpgSourceSelector by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val nameFocusRequester = remember { FocusRequester() }
    val urlFocusRequester = remember { FocusRequester() }
    val userAgentFocusRequester = remember { FocusRequester() }
    val cacheTimeFocusRequester = remember { FocusRequester() }
    val transformJsFocusRequester = remember { FocusRequester() }
    val epgSourceSelectFocusRequester = remember { FocusRequester() }
    val epgSourceNameFocusRequester = remember { FocusRequester() }
    val epgSourceUrlFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        nameFocusRequester.saveRequestFocus()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = modifier
                .width(6.gridColumns())
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                .padding(20.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier.padding(
                        top = 8.dp,
                        bottom = 16.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
                ) {
                    Text(
                        text = if (initialSource.name.isNotEmpty()) "编辑直播源" else if (initialSource.isLocal) "添加本地直播源" else "添加远程直播源",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("名称", style = MaterialTheme.typography.labelMedium)
                    EditableField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = nameFocusRequester,
                        nextFocusRequester = urlFocusRequester,
                    )

                    Text("地址", style = MaterialTheme.typography.labelMedium)
                    EditableField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = urlFocusRequester,
                        nextFocusRequester = userAgentFocusRequester,
                    )

                    Text("自定义UA（可选）", style = MaterialTheme.typography.labelMedium)
                    EditableField(
                        value = userAgent,
                        onValueChange = { userAgent = it },
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = userAgentFocusRequester,
                        nextFocusRequester = cacheTimeFocusRequester,
                    )

                    Text("缓存时间（毫秒，可选）", style = MaterialTheme.typography.labelMedium)
                    EditableField(
                        value = cacheTime,
                        onValueChange = { cacheTime = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = cacheTimeFocusRequester,
                        nextFocusRequester = transformJsFocusRequester,
                    )

                    Text("转换JS（可选）", style = MaterialTheme.typography.labelMedium)
                    EditableField(
                        value = transformJs,
                        onValueChange = { transformJs = it },
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = transformJsFocusRequester,
                        nextFocusRequester = if (epgSourceList.isNotEmpty()) epgSourceSelectFocusRequester else epgSourceNameFocusRequester,
                    )

                    // 如果有预定义的节目单源，显示选择器
                    if (epgSourceList.isNotEmpty()) {
                        Text("选择节目单源（可选）", style = MaterialTheme.typography.labelMedium)
                        
                        // 显示当前选择的节目单
                        val selectedEpgSource = epgSourceList.find { it.url == epgSourceUrl && it.name == epgSourceName }
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .focusRequester(epgSourceSelectFocusRequester)
                                .handleKeyEvents(onSelect = { showEpgSourceSelector = true }),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.primary.copy(0.2f),
                            ),
                            onClick = { showEpgSourceSelector = true },
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = selectedEpgSource?.name ?: "点击选择节目单源",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (selectedEpgSource != null) LocalContentColor.current 
                                            else LocalContentColor.current.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                        }
                        
                        // 显示手动输入的切换选项
                        PopupContent(
                            visibleProvider = { showEpgSourceSelector },
                            onDismissRequest = { 
                                showEpgSourceSelector = false
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(100)
                                    epgSourceSelectFocusRequester.saveRequestFocus()
                                }
                            },
                            withBackground = true,
                        ) {
                            EpgSourceSelectorContent(
                                epgSourceList = epgSourceList,
                                currentSelection = selectedEpgSource,
                                onSelect = { epgSource ->
                                    if (epgSource != null) {
                                        epgSourceName = epgSource.name
                                        epgSourceUrl = epgSource.url
                                    } else {
                                        epgSourceName = ""
                                        epgSourceUrl = ""
                                    }
                                    showEpgSourceSelector = false
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(100)
                                        epgSourceSelectFocusRequester.saveRequestFocus()
                                    }
                                },
                                onDismiss = { 
                                    showEpgSourceSelector = false
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(100)
                                        epgSourceSelectFocusRequester.saveRequestFocus()
                                    }
                                },
                            )
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        Text("或手动输入节目单：", style = MaterialTheme.typography.labelSmall)
                    }

                    Text("关联节目单名称（可选）", style = MaterialTheme.typography.labelMedium)
                    EditableField(
                        value = epgSourceName,
                        onValueChange = { epgSourceName = it },
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = epgSourceNameFocusRequester,
                        nextFocusRequester = epgSourceUrlFocusRequester,
                    )

                    Text("关联节目单地址（可选）", style = MaterialTheme.typography.labelMedium)
                    EditableField(
                        value = epgSourceUrl,
                        onValueChange = { epgSourceUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = epgSourceUrlFocusRequester,
                        nextFocusRequester = saveFocusRequester,
                    )

                    Spacer(Modifier.height(16.dp))

                    val doSave = {
                        val epgSource = if (epgSourceUrl.isNotBlank()) {
                            EpgSource(name = epgSourceName.ifBlank { "节目单" }, url = epgSourceUrl)
                        } else null

                        onSave(
                            initialSource.copy(
                                name = name.ifBlank { "未命名直播源" },
                                url = url,
                                userAgent = userAgent.ifBlank { null },
                                cacheTime = cacheTime.toLongOrNull(),
                                transformJs = transformJs.ifBlank { null },
                                epgSource = epgSource,
                            )
                        )
                        onDismiss()
                    }

                    SaveCancelButtons(
                        onSave = doSave,
                        onCancel = onDismiss,
                        saveFocusRequester = saveFocusRequester,
                        cancelFocusRequester = cancelFocusRequester,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgSourceSelectorContent(
    modifier: Modifier = Modifier,
    epgSourceList: EpgSourceList = EpgSourceList(),
    currentSelection: EpgSource? = null,
    onSelect: (EpgSource?) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        firstItemFocusRequester.saveRequestFocus()
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = modifier
                .width(5.gridColumns())
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                .padding(20.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier.padding(
                        top = 8.dp,
                        bottom = 16.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
                ) {
                    Text(
                        "选择节目单源",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 不关联选项
                    item {
                        val isSelected = currentSelection == null
                        ListItem(
                            modifier = Modifier
                                .focusRequester(firstItemFocusRequester)
                                .handleKeyEvents(onSelect = { onSelect(null) }),
                            headlineContent = { Text("不关联节目单") },
                            trailingContent = {
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(0.05f),
                            ),
                            selected = isSelected,
                            onClick = {},
                        )
                    }
                    
                    items(epgSourceList) { epgSource ->
                        val isSelected = currentSelection?.url == epgSource.url && 
                                        currentSelection?.name == epgSource.name
                        ListItem(
                            modifier = Modifier
                                .handleKeyEvents(onSelect = { onSelect(epgSource) }),
                            headlineContent = { Text(epgSource.name) },
                            supportingContent = { 
                                Text(
                                    epgSource.url,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(0.05f),
                            ),
                            selected = isSelected,
                            onClick = {},
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .handleKeyEvents(onSelect = onDismiss),
                    headlineContent = { 
                        Text(
                            "取消",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(0.1f),
                    ),
                    selected = false,
                    onClick = {},
                )
            }
        }
    }
}



private sealed interface IptvSourceDetail {
    data object None : IptvSourceDetail
    data object Loading : IptvSourceDetail
    data object Error : IptvSourceDetail
    data class Ready(
        val channelGroupCount: Int,
        val channelCount: Int,
        val lineCount: Int,
    ) : IptvSourceDetail
}

@Preview
@Composable
private fun SettingsIptvSourceItemPreview() {
    MyTvTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IptvSourceItem(
                iptvSourceProvider = { IptvSource.EXAMPLE },
            )

            IptvSourceItem(
                iptvSourceProvider = { IptvSource.EXAMPLE },
                iptvSourceDetailProvider = { IptvSourceDetail.Ready(10, 100, lineCount = 1000) },
            )

            IptvSourceItem(
                iptvSourceProvider = { IptvSource.EXAMPLE },
                iptvSourceDetailProvider = { IptvSourceDetail.Error },
            )

            IptvSourceItem(
                iptvSourceProvider = { IptvSource.EXAMPLE.copy(enabled = true) },
            )

            IptvSourceItem(
                modifier = Modifier.focusOnLaunched(),
                iptvSourceProvider = { IptvSource.EXAMPLE },
                iptvSourceDetailProvider = { IptvSourceDetail.Error },
            )
        }
    }
}

@Preview
@Composable
private fun SettingsIptvSourceActionsPreview() {
    MyTvTheme {
        SettingsIptvSourceActions()
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsIptvSourceScreenPreview() {
    MyTvTheme {
        SettingsIptvSourceScreen(
            iptvSourceListProvider = { IptvSourceList.EXAMPLE },
        )
    }
}
