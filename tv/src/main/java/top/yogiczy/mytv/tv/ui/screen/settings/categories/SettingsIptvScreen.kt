package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.utils.ChannelAlias
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.Tag
import top.yogiczy.mytv.tv.ui.material.TagDefaults
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme

@Composable
fun SettingsIptvScreen(
    modifier: Modifier = Modifier,
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    settingsViewModel: SettingsViewModel = settingsVM,
    toIptvSourceScreen: () -> Unit = {},
    toChannelGroupVisibilityScreen: () -> Unit = {},
    toIptvHybridModeScreen: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val enabledSourceCount = settingsViewModel.iptvSourceList.count { it.enabled }
    val totalSourceCount = settingsViewModel.iptvSourceList.size

    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 直播源") },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                headlineContent = "自定义直播源",
                supportingContent = "支持多订阅源同时使用、本地文件、自定义UA",
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (totalSourceCount > 0) {
                            Tag(
                                "已启用${enabledSourceCount}/${totalSourceCount}",
                                colors = TagDefaults.colors(
                                    containerColor = LocalContentColor.current.copy(0.1f)
                                ),
                            )
                        }
                    }
                },
                onSelect = toIptvSourceScreen,
                link = true,
            )
        }

        item {
            val allCount = channelGroupListProvider().size
            val hiddenCount = settingsViewModel.iptvChannelGroupHiddenList.size

            SettingsListItem(
                headlineContent = "频道分组管理",
                trailingContent = {
                    if (hiddenCount == 0) {
                        Text("共${allCount}个分组")
                    } else {
                        Text("共${allCount}个分组，已隐藏${hiddenCount}个分组")
                    }
                },
                onSelect = toChannelGroupVisibilityScreen,
                link = true,
            )
        }

        item {
            val alias = ChannelAlias.aliasMap

            SettingsListItem(
                headlineContent = "频道别名",
                trailingContent = {
                    Text("共${alias.size}个频道，${alias.values.sumOf { it.size }}个别名")
                },
                remoteConfig = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "相似频道合并",
                supportingContent = "相同频道别名将进行合并",
                trailingContent = {
                    Switch(settingsViewModel.iptvSimilarChannelMerge, null)
                },
                onSelect = {
                    settingsViewModel.iptvSimilarChannelMerge =
                        !settingsViewModel.iptvSimilarChannelMerge
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "频道图标提供",
                trailingContent = settingsViewModel.iptvChannelLogoProvider,
                remoteConfig = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "频道图标覆盖",
                supportingContent = "使用频道图标提供覆盖直播源中定义的频道图标",
                trailingContent = {
                    Switch(settingsViewModel.iptvChannelLogoOverride, null)
                },
                onSelect = {
                    settingsViewModel.iptvChannelLogoOverride =
                        !settingsViewModel.iptvChannelLogoOverride
                },
            )
        }

        item {
            val hybridMode = settingsViewModel.iptvHybridMode

            SettingsListItem(
                headlineContent = "混合模式",
                trailingContent = { Text(hybridMode.label) },
                onSelect = toIptvHybridModeScreen,
                link = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "直播源缓存",
                supportingContent = "缓存直播源数据以加快加载速度",
                trailingContent = {
                    Switch(settingsViewModel.iptvSourceCacheEnable, null)
                },
                onSelect = {
                    settingsViewModel.iptvSourceCacheEnable =
                        !settingsViewModel.iptvSourceCacheEnable
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "直播源缓存时间",
                supportingContent = "直播源数据本地缓存时间",
                trailingContent = { Text("${settingsViewModel.iptvSourceCacheTimeHours}小时") },
                onSelect = {
                    val hours = listOf(1, 6, 12, 24, 48, 72, 168)
                    val currentIndex = hours.indexOf(settingsViewModel.iptvSourceCacheTimeHours)
                    val nextIndex = (currentIndex + 1) % hours.size
                    settingsViewModel.iptvSourceCacheTimeHours = hours[nextIndex]
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "频道图标缓存",
                supportingContent = "缓存频道图标以加快加载速度",
                trailingContent = {
                    Switch(settingsViewModel.channelLogoCacheEnable, null)
                },
                onSelect = {
                    settingsViewModel.channelLogoCacheEnable =
                        !settingsViewModel.channelLogoCacheEnable
                },
            )
        }


        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            SettingsListItem(
                headlineContent = "清除频道图标缓存",
                supportingContent = "清除所有已缓存的频道图标",
                onSelect = {
                    (context.applicationContext as? top.yogiczy.mytv.tv.MyTVApplication)?.clearImageCache()
                    Snackbar.show("频道图标缓存已清除")
                },
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsIptvScreenPreview() {
    MyTvTheme {
        SettingsIptvScreen()
    }
}