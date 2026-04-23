package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.ifElse
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SettingsLogScreen(
    modifier: Modifier = Modifier,
    historyItemList: List<Logger.HistoryItem> = Logger.history,
    settingsViewModel: SettingsViewModel = settingsVM,
    onBackPressed: () -> Unit = {},
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    val filteredHistoryItemList = if (settingsViewModel.debugDeveloperMode) {
        historyItemList
    } else {
        historyItemList.filter { item ->
            !item.tag.contains("Media3VideoPlayer") && 
            !item.tag.contains("IjkVideoPlayer") &&
            !item.message.contains("音轨")
        }
    }

    SettingsCategoryScreen(
        modifier = modifier,
        header = { 
            Text(
                if (settingsViewModel.debugDeveloperMode) 
                    "设置 / 日志 (开发者模式)" 
                else 
                    "设置 / 日志"
            ) 
        },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        if (!settingsViewModel.debugDeveloperMode) {
            item {
                ListItem(
                    modifier = Modifier.focusRequester(firstItemFocusRequester),
                    headlineContent = { Text("提示") },
                    supportingContent = { Text("开启开发者模式后可查看音轨调试日志") },
                    selected = false,
                    onClick = {},
                )
            }
        }
        
        itemsIndexed(filteredHistoryItemList.reversed()) { index, log ->
            ListItem(
                modifier = Modifier.ifElse(
                    index == 0 && settingsViewModel.debugDeveloperMode,
                    Modifier.focusRequester(firstItemFocusRequester)
                ),
                leadingContent = { Text(log.level.toString()[0].toString()) },
                headlineContent = { Text(log.tag) },
                supportingContent = { Text("${log.message} ${log.cause ?: ""}") },
                trailingContent = { Text(timeFormat.format(log.time)) },
                selected = false,
                onClick = {},
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun Preview() {
    MyTvTheme {
        SettingsLogScreen(
            historyItemList = listOf(
                Logger.HistoryItem(
                    level = Logger.LevelType.DEBUG,
                    tag = "SettingsLogScreen",
                    message = "message level = Logger.LevelType.INFO,",
                    time = System.currentTimeMillis(),
                ),
                Logger.HistoryItem(
                    level = Logger.LevelType.INFO,
                    tag = "SettingsLogScreen",
                    message = "message level = Logger.LevelType.INFO,",
                    time = System.currentTimeMillis(),
                ),
                Logger.HistoryItem(
                    level = Logger.LevelType.WARN,
                    tag = "SettingsLogScreen",
                    message = "message level = Logger.LevelType.INFO,",
                    time = System.currentTimeMillis(),
                ),
                Logger.HistoryItem(
                    level = Logger.LevelType.ERROR,
                    tag = "SettingsLogScreen",
                    message = "message level = Logger.LevelType.INFO,",
                    cause = "java.lang.Exception: 401: Unauthorized",
                    time = System.currentTimeMillis(),
                ),
            )
        )
    }
}