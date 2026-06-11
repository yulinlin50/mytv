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
fun SettingsVideoPlayerRenderModeScreen(
    modifier: Modifier = Modifier,
    renderModeProvider: () -> Configs.VideoPlayerRenderMode = { Configs.VideoPlayerRenderMode.SURFACE_VIEW },
    onRenderModeChanged: (Configs.VideoPlayerRenderMode) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    SettingsSelectionScreen(
        modifier = modifier.padding(top = 10.dp),
        title = "设置 / 播放器 / 渲染方式",
        options = Configs.VideoPlayerRenderMode.entries.map { SelectionOption(value = it, label = it.label) },
        selectedProvider = renderModeProvider,
        onSelected = onRenderModeChanged,
        columns = 4,
        onBackPressed = onBackPressed,
    )
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsVideoPlayerRenderModeScreenPreview() {
    MyTvTheme {
        SettingsVideoPlayerRenderModeScreen()
    }
}
