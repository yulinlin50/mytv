package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.core.util.utils.humanizeMs
import top.yogiczy.mytv.tv.ui.screen.settings.components.SelectionOption
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsSelectionScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme

@Composable
fun SettingsUiScreenAutoCloseScreen(
    modifier: Modifier = Modifier,
    delayProvider: () -> Long = { 0 },
    onDelayChanged: (Long) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val delayList = listOf(5, 10, 15, 20, 25, 30).map { it.toLong() * 1000 } + Long.MAX_VALUE

    SettingsSelectionScreen(
        modifier = modifier.padding(top = 10.dp),
        title = "设置 / 界面 / 超时自动关闭界面",
        options = delayList.map { delay ->
            SelectionOption(
                value = delay,
                label = when (delay) {
                    Long.MAX_VALUE -> "不关闭"
                    else -> delay.humanizeMs()
                },
            )
        },
        selectedProvider = delayProvider,
        onSelected = onDelayChanged,
        columns = 6,
        centered = true,
        onBackPressed = onBackPressed,
    )
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsUiScreenAutoCloseScreenPreview() {
    MyTvTheme {
        SettingsUiScreenAutoCloseScreen()
    }
}
