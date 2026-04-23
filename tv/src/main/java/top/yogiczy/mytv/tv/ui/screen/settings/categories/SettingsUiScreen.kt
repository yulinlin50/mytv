package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.util.utils.humanizeMs
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import java.text.DecimalFormat

@Composable
fun SettingsUiScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    toUiTimeShowModeScreen: () -> Unit = {},
    toUiScreenAutoCloseDelayScreen: () -> Unit = {},
    toUiDensityScaleRatioScreen: () -> Unit = {},
    toUiFontScaleRatioScreen: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 界面") },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                headlineContent = "节目进度",
                supportingContent = "在频道底部显示当前节目进度条",
                trailingContent = { Switch(settingsViewModel.uiShowEpgProgrammeProgress, null) },
                onSelect = {
                    settingsViewModel.uiShowEpgProgrammeProgress = !settingsViewModel.uiShowEpgProgrammeProgress
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "常驻节目进度",
                supportingContent = "在播放器底部显示当前节目进度条",
                trailingContent = {
                    Switch(settingsViewModel.uiShowEpgProgrammePermanentProgress, null)
                },
                onSelect = {
                    settingsViewModel.uiShowEpgProgrammePermanentProgress = !settingsViewModel.uiShowEpgProgrammePermanentProgress
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "台标显示",
                trailingContent = {
                    Switch(settingsViewModel.uiShowChannelLogo, null)
                },
                onSelect = {
                    settingsViewModel.uiShowChannelLogo = !settingsViewModel.uiShowChannelLogo
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "频道预览",
                trailingContent = {
                    Switch(settingsViewModel.uiShowChannelPreview, null)
                },
                onSelect = {
                    settingsViewModel.uiShowChannelPreview = !settingsViewModel.uiShowChannelPreview
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "经典选台界面",
                supportingContent = "将选台界面替换为经典三段式结构",
                trailingContent = {
                    Switch(settingsViewModel.uiUseClassicPanelScreen, null)
                },
                onSelect = {
                    settingsViewModel.uiUseClassicPanelScreen = !settingsViewModel.uiUseClassicPanelScreen
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "时间显示",
                trailingContent = settingsViewModel.uiTimeShowMode.label,
                onSelect = toUiTimeShowModeScreen,
                link = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "超时自动关闭界面",
                trailingContent = when (val delay = settingsViewModel.uiScreenAutoCloseDelay) {
                    Long.MAX_VALUE -> "不关闭"
                    else -> delay.humanizeMs()
                },
                onSelect = toUiScreenAutoCloseDelayScreen,
                link = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "界面整体缩放比例",
                trailingContent = when (val scaleRatio = settingsViewModel.uiDensityScaleRatio) {
                    0f -> "自适应"
                    else -> "×${DecimalFormat("#.#").format(scaleRatio)}"
                },
                onSelect = toUiDensityScaleRatioScreen,
                link = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "界面字体缩放比例",
                trailingContent = "×${DecimalFormat("#.#").format(settingsViewModel.uiFontScaleRatio)}",
                onSelect = toUiFontScaleRatioScreen,
                link = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "焦点优化",
                supportingContent = "关闭后可解决触摸设备在部分场景下闪退",
                trailingContent = {
                    Switch(settingsViewModel.uiFocusOptimize, null)
                },
                onSelect = {
                    settingsViewModel.uiFocusOptimize = !settingsViewModel.uiFocusOptimize
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "低性能模式",
                supportingContent = "为低配置设备优化界面性能，减少动画和渲染开销",
                trailingContent = {
                    Switch(settingsViewModel.uiLowPerformanceMode, null)
                },
                onSelect = {
                    settingsViewModel.uiLowPerformanceMode = !settingsViewModel.uiLowPerformanceMode
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "简化频道项",
                supportingContent = "减少频道项的视觉元素，提升滚动性能",
                trailingContent = {
                    Switch(settingsViewModel.uiSimplifyChannelItem, null)
                },
                onSelect = {
                    settingsViewModel.uiSimplifyChannelItem = !settingsViewModel.uiSimplifyChannelItem
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "频道网格列数",
                supportingContent = "调整频道网格的列数，减少列数可提升性能",
                trailingContent = "${settingsViewModel.uiChannelGridColumns} 列",
                onSelect = {
                    val columns = listOf(3, 4, 5, 6)
                    val currentIndex = columns.indexOf(settingsViewModel.uiChannelGridColumns).coerceAtLeast(0)
                    val nextIndex = (currentIndex + 1) % columns.size
                    settingsViewModel.uiChannelGridColumns = columns[nextIndex]
                },
            )
        }

        item {
            val seconds = settingsViewModel.uiEpgUpdateIntervalMs / 1000
            SettingsListItem(
                headlineContent = "EPG更新间隔",
                supportingContent = "调整节目信息更新频率，更长的间隔可减少资源消耗",
                trailingContent = if (seconds >= 60) "${seconds / 60}分钟" else "${seconds}秒",
                onSelect = {
                    val intervals = listOf(10_000L, 30_000L, 60_000L, 120_000L, 300_000L)
                    val currentIndex = intervals.indexOf(settingsViewModel.uiEpgUpdateIntervalMs).coerceAtLeast(0)
                    val nextIndex = (currentIndex + 1) % intervals.size
                    settingsViewModel.uiEpgUpdateIntervalMs = intervals[nextIndex]
                },
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsUiScreenPreview() {
    MyTvTheme {
        SettingsUiScreen()
    }
}