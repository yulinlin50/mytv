package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.sync.CloudSyncProvider
import top.yogiczy.mytv.tv.ui.screen.settings.components.SelectionOption
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsSelectionScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme

@Composable
fun SettingsCloudSyncProviderScreen(
    modifier: Modifier = Modifier,
    providerProvider: () -> CloudSyncProvider = { CloudSyncProvider.GITHUB_GIST },
    onProviderChanged: (CloudSyncProvider) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    SettingsSelectionScreen(
        modifier = modifier.padding(top = 10.dp),
        title = "设置 / 云同步 / 服务商",
        options = CloudSyncProvider.entries.map { provider ->
            SelectionOption(value = provider, label = provider.label)
        },
        selectedProvider = providerProvider,
        onSelected = onProviderChanged,
        onBackPressed = onBackPressed,
        extraTrailingContent = { provider ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (provider.supportPull) {
                    Text("支持拉取")
                } else {
                    Text("不支持拉取", color = MaterialTheme.colorScheme.error)
                }

                if (provider.supportPush) {
                    Text("支持推送")
                } else {
                    Text("不支持推送", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsCloudSyncProviderScreenPreview() {
    MyTvTheme {
        SettingsCloudSyncProviderScreen()
    }
}
