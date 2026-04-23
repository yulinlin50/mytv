package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.material.Slider
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme

@Composable
fun SettingsThemeTextureAlphaScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    onBackPressed: () -> Unit = {},
) {
    val currentTextureAlpha = settingsViewModel.themeTextureAlpha
    var textureAlpha by remember(currentTextureAlpha) { mutableFloatStateOf(currentTextureAlpha) }
    
    AppScreen(
        modifier = modifier.padding(top = 10.dp),
        header = { Text("设置 / 主题 / 纹理透明度") },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 38.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "调整主题背景纹理的透明度，数值越小纹理越淡，文字可读性越高。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "透明度",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "${(textureAlpha * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            
            Slider(
                value = textureAlpha,
                onValueChange = { textureAlpha = it },
                onValueChangeFinished = {
                    settingsViewModel.themeTextureAlpha = textureAlpha
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                tvStep = 0.05f,
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "淡",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Text(
                    text = "浓",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            
            Text(
                text = "提示：如果主题背景纹理影响文字可读性，可以降低透明度。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsThemeTextureAlphaScreenPreview() {
    MyTvTheme {
        SettingsThemeTextureAlphaScreen()
    }
}
