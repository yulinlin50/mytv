package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.tv.ui.screen.settings.components.SelectionOption
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsSelectionScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.Configs

@Composable
fun SettingsVideoPlayerCoreScreen(
    modifier: Modifier = Modifier,
    coreProvider: () -> Configs.VideoPlayerCore = { Configs.VideoPlayerCore.MEDIA3 },
    onCoreChanged: (Configs.VideoPlayerCore) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    SettingsSelectionScreen(
        modifier = modifier.padding(top = 10.dp),
        title = "设置 / 播放器 / 内核",
        options = Configs.VideoPlayerCore.entries.map { core ->
            SelectionOption(
                value = core,
                label = core.label,
                supportingContent = when (core) {
                    Configs.VideoPlayerCore.MEDIA3 -> "支持全部功能"
                    Configs.VideoPlayerCore.IJK -> "部分功能可能无法正常使用，仅支持armeabi-v7a、arm64-v8a"
                },
            )
        },
        selectedProvider = coreProvider,
        onSelected = onCoreChanged,
        columns = 2,
        onBackPressed = onBackPressed,
    )
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsVideoPlayerCoreScreenPreview() {
    MyTvTheme {
        SettingsVideoPlayerCoreScreen()
    }
}
