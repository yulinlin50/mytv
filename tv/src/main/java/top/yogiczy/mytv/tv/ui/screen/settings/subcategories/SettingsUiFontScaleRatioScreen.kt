package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.tv.ui.screen.settings.components.SelectionOption
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsSelectionScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import java.text.DecimalFormat

@Composable
fun SettingsUiFontScaleRatioScreen(
    modifier: Modifier = Modifier,
    scaleRatioProvider: () -> Float = { 0f },
    onScaleRatioChanged: (Float) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val scaleRatioList = (5..20).map { it * 0.1f }

    SettingsSelectionScreen(
        modifier = modifier.padding(top = 10.dp),
        title = "设置 / 界面 / 界面字体缩放比例",
        options = scaleRatioList.map { scaleRatio ->
            SelectionOption(value = scaleRatio, label = "×${DecimalFormat("#.#").format(scaleRatio)}")
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
private fun SettingsUiFontScaleRatioScreenPreview() {
    MyTvTheme {
        SettingsUiFontScaleRatioScreen()
    }
}
