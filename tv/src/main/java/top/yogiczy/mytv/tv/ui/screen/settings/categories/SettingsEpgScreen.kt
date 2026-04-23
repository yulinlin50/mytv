package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme

@Composable
fun SettingsEpgScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    toIptvSourceScreen: () -> Unit = {},
    toEpgSourceScreen: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val sourceWithEpgCount = settingsViewModel.iptvSourceList.count { it.enabled && it.epgSource != null }

    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 节目单") },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                headlineContent = "节目单启用",
                supportingContent = "首次加载时可能会较为缓慢",
                trailingContent = { Switch(settingsViewModel.epgEnable, null) },
                onSelect = { settingsViewModel.epgEnable = !settingsViewModel.epgEnable },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "跟随直播源",
                supportingContent = "优先使用直播源中定义的节目单（每个直播源可单独设置节目单源）",
                trailingContent = { Switch(settingsViewModel.epgSourceFollowIptv, null) },
                onSelect = {
                    settingsViewModel.epgSourceFollowIptv = !settingsViewModel.epgSourceFollowIptv
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "节目单源管理",
                supportingContent = if (settingsViewModel.epgSourceList.isNotEmpty()) {
                    "已配置${settingsViewModel.epgSourceList.size}个节目单源"
                } else {
                    "配置可复用的节目单源，供多个直播源选择"
                },
                onSelect = toEpgSourceScreen,
                link = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "直播源节目单配置",
                supportingContent = if (sourceWithEpgCount > 0) {
                    "${sourceWithEpgCount}个直播源已配置节目单"
                } else {
                    "为每个直播源选择或配置节目单源"
                },
                onSelect = toIptvSourceScreen,
                link = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "节目单缓存",
                supportingContent = "缓存节目单数据以加快加载速度",
                trailingContent = { Switch(settingsViewModel.epgCacheEnable, null) },
                onSelect = { settingsViewModel.epgCacheEnable = !settingsViewModel.epgCacheEnable },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "节目单缓存时间",
                supportingContent = "节目单数据本地缓存时间",
                trailingContent = { Text("${settingsViewModel.epgCacheTimeHours}小时") },
                onSelect = {
                    val hours = listOf(1, 6, 12, 24, 48, 72, 168)
                    val currentIndex = hours.indexOf(settingsViewModel.epgCacheTimeHours)
                    val nextIndex = (currentIndex + 1) % hours.size
                    settingsViewModel.epgCacheTimeHours = hours[nextIndex]
                },
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsEpgScreenPreview() {
    MyTvTheme {
        SettingsEpgScreen()
    }
}