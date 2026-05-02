package top.yogiczy.mytv.tv.ui.screen.push

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import top.yogiczy.mytv.core.util.utils.actionView
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.components.Qrcode
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.clickableNoIndication
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.saveRequestFocus
import top.yogiczy.mytv.tv.utlis.HttpServer
import top.yogiczy.mytv.tv.utlis.HttpServerSecurity

@Composable
fun PushScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    onBackPressed: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        while (true) {
            settingsViewModel.refresh()
            delay(1000)
        }
    }

    AppScreen(
        modifier = modifier,
        header = { Text("数据推送") },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        PushContent()
    }
}

@Composable
fun PushContent(
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val serverUrl = HttpServer.serverUrl
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val accessToken = HttpServerSecurity.getAccessToken(context)
    val serverUrlWithToken = "$serverUrl#token=$accessToken"

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        focusRequester.saveRequestFocus()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Qrcode(
                modifier = Modifier
                    .width(200.dp)
                    .height(200.dp)
                    .clickableNoIndication { context.actionView(serverUrlWithToken) },
                text = serverUrlWithToken,
            )

            Spacer(Modifier.height(20.dp))
            Text("服务已启动：${serverUrl}")
            Text("请扫描二维码或输入IP地址进行连接")

            onDismiss?.let { dismiss ->
                Spacer(Modifier.height(24.dp))
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .focusRequester(focusRequester)
                        .handleKeyEvents(onSelect = dismiss),
                    headlineContent = { 
                        Text(
                            "关闭",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    selected = false,
                    onClick = {},
                )
            }
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun PushScreenPreview() {
    MyTvTheme {
        PushScreen()
    }
}