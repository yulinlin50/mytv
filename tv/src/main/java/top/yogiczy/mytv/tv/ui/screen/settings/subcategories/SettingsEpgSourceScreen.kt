package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
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
import kotlinx.coroutines.delay
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSourceList
import top.yogiczy.mytv.tv.ui.material.EditableField
import top.yogiczy.mytv.tv.ui.material.SaveCancelButtons
import top.yogiczy.mytv.tv.ui.material.SettingsActionItem
import top.yogiczy.mytv.tv.ui.material.SimplePopup
import top.yogiczy.mytv.tv.ui.material.Tag
import top.yogiczy.mytv.tv.ui.material.TagDefaults
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched
import top.yogiczy.mytv.tv.ui.utils.gridColumns
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse
import top.yogiczy.mytv.tv.ui.utils.saveRequestFocus

@Composable
fun SettingsEpgSourceScreen(
    modifier: Modifier = Modifier,
    epgSourceListProvider: () -> EpgSourceList = { EpgSourceList() },
    onEpgSourceListChange: (EpgSourceList) -> Unit = {},
    onAddEpgSource: (EpgSource) -> Unit = {},
    onUpdateEpgSource: (EpgSource) -> Unit = {},
    onDeleteEpgSource: (EpgSource) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val epgSourceList = epgSourceListProvider()

    AppScreen(
        modifier = modifier,
        header = { Text("设置 / 节目单 / 节目单源管理") },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        SettingsEpgSourceContent(
            epgSourceListProvider = { epgSourceList },
            onAddEpgSource = onAddEpgSource,
            onUpdateEpgSource = onUpdateEpgSource,
            onDeleteEpgSource = onDeleteEpgSource,
        )
    }
}

@Composable
private fun SettingsEpgSourceContent(
    modifier: Modifier = Modifier,
    epgSourceListProvider: () -> EpgSourceList = { EpgSourceList() },
    onAddEpgSource: (EpgSource) -> Unit = {},
    onUpdateEpgSource: (EpgSource) -> Unit = {},
    onDeleteEpgSource: (EpgSource) -> Unit = {},
) {
    val epgSourceList = epgSourceListProvider()

    val childPadding = rememberChildPadding()
    val firstItemFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(epgSourceList.size) {
        if (epgSourceList.isNotEmpty()) {
            delay(100)
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
            items = epgSourceList,
            key = { index, it -> "${it.url}_${it.name}_$index" },
        ) { _, epgSource ->
            EpgSourceItem(
                modifier = Modifier.ifElse(
                    epgSourceList.first() == epgSource,
                    Modifier.focusRequester(firstItemFocusRequester),
                ),
                epgSourceProvider = { epgSource },
                onUpdate = { onUpdateEpgSource(it) },
                onDelete = { onDeleteEpgSource(epgSource) },
            )
        }

        item {
            var addPopupVisible by remember { mutableStateOf(false) }

            ListItem(
                modifier = Modifier.handleKeyEvents(onSelect = { addPopupVisible = true }),
                headlineContent = { Text("添加节目单源") },
                leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                selected = false,
                onClick = {},
            )

            SimplePopup(
                visibleProvider = { addPopupVisible },
                onDismissRequest = { addPopupVisible = false },
            ) {
                AddEpgSourceContent(
                    onAdd = { source ->
                        onAddEpgSource(source)
                        addPopupVisible = false
                    },
                    onDismiss = { addPopupVisible = false },
                )
            }
        }
    }
}

@Composable
private fun EpgSourceItem(
    modifier: Modifier = Modifier,
    epgSourceProvider: () -> EpgSource = { EpgSource() },
    onUpdate: (EpgSource) -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val epgSource = epgSourceProvider()

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
                Text(epgSource.name)

                Tag(
                    if (epgSource.isLocal) "本地" else "远程",
                    colors = TagDefaults.colors(
                        containerColor = LocalContentColor.current.copy(0.1f)
                    ),
                )
            }
        },
        supportingContent = {
            Text(epgSource.url)
        },
        trailingContent = {
            Switch(
                checked = true,
                onCheckedChange = { },
                modifier = Modifier.focusable(false)
            )
        },
        selected = false,
        onClick = {},
    )

    SimplePopup(
        visibleProvider = { actionsVisible },
        onDismissRequest = { actionsVisible = false },
    ) {
        SettingsEpgSourceActions(
            epgSourceProvider = { epgSource },
            onDismissRequest = { actionsVisible = false },
            onEdit = {
                actionsVisible = false
                showEdit = true
            },
            onDelete = {
                onDelete()
                actionsVisible = false
            },
        )
    }

    SimplePopup(
        visibleProvider = { showEdit },
        onDismissRequest = { showEdit = false },
    ) {
        EpgSourceEditContent(
            initialSource = epgSource,
            onSave = { updatedSource ->
                onUpdate(updatedSource)
                showEdit = false
            },
            onDismiss = { showEdit = false },
        )
    }
}

@Composable
private fun SettingsEpgSourceActions(
    modifier: Modifier = Modifier,
    epgSourceProvider: () -> EpgSource = { EpgSource() },
    onDismissRequest: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val epgSource = epgSourceProvider()

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
                        epgSource.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                            title = "编辑",
                            imageVector = Icons.Outlined.Edit,
                            onSelected = onEdit,
                            modifier = Modifier.focusOnLaunched(),
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
                            title = "返回",
                            imageVector = Icons.Outlined.ArrowBackIosNew,
                            onSelected = onDismissRequest,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddEpgSourceContent(
    modifier: Modifier = Modifier,
    onAdd: (EpgSource) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    var showRemoteInput by remember { mutableStateOf(false) }
    var showLocalInput by remember { mutableStateOf(false) }

    val remoteInputFocusRequester = remember { FocusRequester() }
    val localInputFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showRemoteInput) {
        if (showRemoteInput) {
            delay(100)
            remoteInputFocusRequester.saveRequestFocus()
        }
    }

    LaunchedEffect(showLocalInput) {
        if (showLocalInput) {
            delay(100)
            localInputFocusRequester.saveRequestFocus()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (!showRemoteInput && !showLocalInput) {
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
                            "添加节目单源",
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
        EpgSourceEditContent(
            initialSource = EpgSource(isLocal = false),
            onSave = onAdd,
            onDismiss = { showRemoteInput = false },
        )
    }

    if (showLocalInput) {
        EpgSourceEditContent(
            initialSource = EpgSource(isLocal = true),
            onSave = onAdd,
            onDismiss = { showLocalInput = false },
        )
    }
}

@Composable
private fun EpgSourceEditContent(
    modifier: Modifier = Modifier,
    initialSource: EpgSource = EpgSource(),
    onSave: (EpgSource) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    var name by remember { mutableStateOf(initialSource.name) }
    var url by remember { mutableStateOf(initialSource.url) }
    var expireHours by remember { mutableStateOf(initialSource.expireHours?.toString() ?: "") }

    val scrollState = rememberScrollState()

    val nameFocusRequester = remember { FocusRequester() }
    val urlFocusRequester = remember { FocusRequester() }
    val expireHoursFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
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
                        text = if (initialSource.name.isNotEmpty()) "编辑节目单源" else if (initialSource.isLocal) "添加本地节目单源" else "添加远程节目单源",
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
                        nextFocusRequester = expireHoursFocusRequester,
                    )

                    Text("过期时间（小时，可选）", style = MaterialTheme.typography.labelMedium)
                    EditableField(
                        value = expireHours,
                        onValueChange = { expireHours = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = expireHoursFocusRequester,
                        nextFocusRequester = saveFocusRequester,
                    )

                    Spacer(Modifier.height(16.dp))

                    val doSave = {
                        onSave(
                            initialSource.copy(
                                name = name.ifBlank { "未命名节目单源" },
                                url = url,
                                expireHours = expireHours.toIntOrNull(),
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



@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsEpgSourceScreenPreview() {
    MyTvTheme {
        SettingsEpgSourceScreen(
            epgSourceListProvider = { EpgSourceList.EXAMPLE },
        )
    }
}
