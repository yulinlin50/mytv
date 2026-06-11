package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.tv.ui.screen.settings.components.SelectionOption
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsSelectionScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.Configs

@Composable
fun SettingsIptvHybridModeScreen(
    modifier: Modifier = Modifier,
    hybridModeProvider: () -> Configs.IptvHybridMode = { Configs.IptvHybridMode.DISABLE },
    onHybridModeChanged: (Configs.IptvHybridMode) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    SettingsSelectionScreen(
        modifier = modifier.padding(top = 10.dp),
        title = "设置 / 直播源 / 混合模式",
        options = Configs.IptvHybridMode.entries.map { mode ->
            SelectionOption(
                value = mode,
                label = mode.label,
                supportingContent = when (mode) {
                    Configs.IptvHybridMode.DISABLE -> ""
                    Configs.IptvHybridMode.IPTV_FIRST -> "优先尝试播放直播源中线路，若所有直播源线路不可用，则尝试混合线路"
                    Configs.IptvHybridMode.HYBRID_FIRST -> "优先尝试播放混合线路，若混合线路不可用，则播放直播源中线路"
                },
            )
        },
        selectedProvider = hybridModeProvider,
        onSelected = onHybridModeChanged,
        onBackPressed = onBackPressed,
    )
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsIptvHybridModeScreenPreview() {
    MyTvTheme {
        SettingsIptvHybridModeScreen()
    }
}
