package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.util.utils.humanizeMs
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse

@Composable
fun SettingsUiScreenAutoCloseScreen(
    modifier: Modifier = Modifier,
    delayProvider: () -> Long = { 0 },
    onDelayChanged: (Long) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val currentDelay = delayProvider()
    val delayList = listOf(5, 10, 15, 20, 25, 30).map { it.toLong() * 1000 } + Long.MAX_VALUE

    val childPadding = rememberChildPadding()
    val gridState = rememberLazyGridState()
    val firstItemFocusRequester = remember { FocusRequester() }

    AppScreen(
        modifier = Modifier.padding(top = 10.dp),
        header = { Text("设置 / 界面 / 超时自动关闭界面") },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(6),
            contentPadding = childPadding.copy(top = 10.dp).paddingValues,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(delayList) { index, delay ->
                ListItem(
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .handleKeyEvents(onSelect = { onDelayChanged(delay) }),
                    headlineContent = {
                        Text(
                            text = when (delay) {
                                Long.MAX_VALUE -> "不关闭"
                                else -> delay.humanizeMs()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    },
                    trailingContent = {
                        if (currentDelay == delay) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(0.1f),
                    ),
                    selected = false,
                    onClick = {},
                )
            }
        }
    }
}


@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsUiScreenAutoCloseScreenPreview() {
    MyTvTheme {
        SettingsUiScreenAutoCloseScreen()
    }
}