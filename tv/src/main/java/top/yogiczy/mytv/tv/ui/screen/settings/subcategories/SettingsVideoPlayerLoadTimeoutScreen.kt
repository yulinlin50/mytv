package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.core.util.utils.humanizeMs
import top.yogiczy.mytv.tv.ui.screen.settings.components.SelectionOption
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsSelectionScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme

@Composable
fun SettingsVideoPlayerLoadTimeoutScreen(
    modifier: Modifier = Modifier,
    timeoutProvider: () -> Long = { 0 },
    onTimeoutChanged: (Long) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val timeoutList = listOf(3, 5, 10, 15, 20, 25, 30, 60).map { it.toLong() * 1000 }

    SettingsSelectionScreen(
        modifier = modifier.padding(top = 10.dp),
        title = "设置 / 播放器 / 加载超时",
        options = timeoutList.map { SelectionOption(value = it, label = it.humanizeMs()) },
        selectedProvider = timeoutProvider,
        onSelected = onTimeoutChanged,
        columns = 6,
        centered = true,
        onBackPressed = onBackPressed,
    )
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsVideoPlayerLoadTimeoutScreenPreview() {
    MyTvTheme {
        SettingsVideoPlayerLoadTimeoutScreen()
    }
}
