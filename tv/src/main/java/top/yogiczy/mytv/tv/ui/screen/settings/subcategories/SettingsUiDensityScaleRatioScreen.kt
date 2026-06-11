package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.tv.ui.screen.settings.components.SelectionOption
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsSelectionScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import java.text.DecimalFormat

@Composable
fun SettingsUiDensityScaleRatioScreen(
    modifier: Modifier = Modifier,
    scaleRatioProvider: () -> Float = { 0f },
    onScaleRatioChanged: (Float) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val scaleRatioList = listOf(0f) + (5..20).map { it * 0.1f }

    SettingsSelectionScreen(
        modifier = modifier.padding(top = 10.dp),
        title = "设置 / 界面 / 界面整体缩放比例",
        options = scaleRatioList.map { scaleRatio ->
            SelectionOption(
                value = scaleRatio,
                label = when (scaleRatio) {
                    0f -> "自适应"
                    else -> "×${DecimalFormat("#.#").format(scaleRatio)}"
                },
            )
        },
        selectedProvider = scaleRatioProvider,
        onSelected = onScaleRatioChanged,
        columns = 6,
        centered = true,
        onBackPressed = onBackPressed,
    )
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsUiDensityScaleRatioScreenPreview() {
    MyTvTheme {
        SettingsUiDensityScaleRatioScreen()
    }
}
