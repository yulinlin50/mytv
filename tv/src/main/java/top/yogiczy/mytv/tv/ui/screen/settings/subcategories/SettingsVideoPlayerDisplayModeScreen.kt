package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.tv.ui.screen.settings.components.SelectionOption
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsSelectionScreen
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.VideoPlayerDisplayMode
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme

@Composable
fun SettingsVideoPlayerDisplayModeScreen(
    modifier: Modifier = Modifier,
    displayModeProvider: () -> VideoPlayerDisplayMode = { VideoPlayerDisplayMode.ORIGINAL },
    onDisplayModeChanged: (VideoPlayerDisplayMode) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    SettingsSelectionScreen(
        modifier = modifier.padding(top = 10.dp),
        title = "设置 / 播放器 / 全局显示模式",
        options = VideoPlayerDisplayMode.entries.map { SelectionOption(value = it, label = it.label) },
        selectedProvider = displayModeProvider,
        onSelected = onDisplayModeChanged,
        columns = 6,
        centered = true,
        onBackPressed = onBackPressed,
    )
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsVideoPlayerDisplayModeScreenPreview() {
    MyTvTheme {
        SettingsVideoPlayerDisplayModeScreen()
    }
}
