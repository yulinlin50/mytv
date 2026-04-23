package top.yogiczy.mytv.tv.ui.screen.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.tv.BuildConfig
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.components.QrcodePopup
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
) {
    val childPadding = rememberChildPadding()

    AppScreen(
        modifier = modifier,
        header = { Text("关于") },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        LazyColumn(
            modifier = Modifier.padding(top = 10.dp),
            contentPadding = childPadding.copy(top = 10.dp).paddingValues,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ListItem(
                    headlineContent = { Text("应用标识") },
                    trailingContent = {
                        Text(
                            listOf(
                                BuildConfig.APPLICATION_ID,
                                BuildConfig.FLAVOR,
                                BuildConfig.BUILD_TYPE,
                                "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
                            ).joinToString("_")
                        )
                    },
                    selected = false,
                    onClick = {},
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("设备名称") },
                    trailingContent = { Text(Globals.deviceName) },
                    selected = false,
                    onClick = {},
                )
            }

            item {
                var visible by remember { mutableStateOf(false) }

                ListItem(
                    modifier = Modifier.handleKeyEvents(onSelect = {
                        visible = true
                    }),
                    headlineContent = { Text("设备ID") },
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Globals.deviceId)

                            Icon(
                                Icons.AutoMirrored.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    selected = false,
                    onClick = {},
                )

                QrcodePopup(
                    visibleProvider = { visible },
                    onDismissRequest = { visible = false },
                    text = Globals.deviceId,
                )
            }
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun AboutScreenPreview() {
    MyTvTheme {
        AboutScreen()
    }
}