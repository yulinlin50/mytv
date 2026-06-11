package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.tv.ui.screen.settings.components.SelectionOption
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsSelectionScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.Configs

@Composable
fun SettingsUiTimeShowModeScreen(
    modifier: Modifier = Modifier,
    timeShowModeProvider: () -> Configs.UiTimeShowMode = { Configs.UiTimeShowMode.HIDDEN },
    onTimeShowModeChanged: (Configs.UiTimeShowMode) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val timeShowRangeSeconds = Constants.UI_TIME_SCREEN_SHOW_DURATION / 1000

    SettingsSelectionScreen(
        modifier = modifier.padding(top = 1.dp),
        title = "设置 / 界面 / 时间显示",
        options = Configs.UiTimeShowMode.entries.map { mode ->
            SelectionOption(
                value = mode,
                label = mode.label,
                supportingContent = when (mode) {
                    Configs.UiTimeShowMode.HIDDEN -> "不显示时间"
                    Configs.UiTimeShowMode.ALWAYS -> "总是显示时间"
                    Configs.UiTimeShowMode.EVERY_HOUR -> "整点前后${timeShowRangeSeconds}s显示时间"
                    Configs.UiTimeShowMode.HALF_HOUR -> "半点前后${timeShowRangeSeconds}s显示时间"
                },
            )
        },
        selectedProvider = timeShowModeProvider,
        onSelected = onTimeShowModeChanged,
        columns = 4,
        onBackPressed = onBackPressed,
    )
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsUiTimeShowModeScreenPreview() {
    MyTvTheme {
        SettingsUiTimeShowModeScreen()
    }
}
