package top.yogiczy.mytv.tv.ui.screensold.channelline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.tv.ui.material.Drawer
import top.yogiczy.mytv.tv.ui.material.DrawerPosition
import top.yogiczy.mytv.tv.ui.screensold.channelline.components.ChannelLineItem
import top.yogiczy.mytv.tv.ui.screensold.components.rememberScreenAutoCloseState
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.backHandler
import kotlin.math.max

@Composable
fun ChannelLineScreen(
    modifier: Modifier = Modifier,
    channelProvider: () -> Channel = { Channel() },
    currentLineProvider: () -> ChannelLine = { ChannelLine() },
    onLineSelected: (ChannelLine) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val screenAutoCloseState = rememberScreenAutoCloseState(onTimeout = onClose)
    val lineList = channelProvider().lineList
    val currentLine = currentLineProvider()
    val listState = rememberLazyListState(max(0, lineList.indexOf(currentLine) - 2))

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { _ -> screenAutoCloseState.active() }
    }

    Drawer(
        modifier = modifier.backHandler { onClose() },
        onDismissRequest = onClose,
        position = DrawerPosition.End,
        header = { Text("多线路") },
    ) {
        LazyColumn(
            modifier = Modifier.width(268.dp),
            state = listState,
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(lineList) { index, line ->
                ChannelLineItem(
                    lineProvider = { line },
                    lineIdxProvider = { index },
                    isSelectedProvider = { line == currentLine },
                    onSelected = { onLineSelected(line) },
                )
            }
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun ChannelLineScreenPreview() {
    MyTvTheme {
        PreviewWithLayoutGrids {
            ChannelLineScreen(
                channelProvider = { Channel.EXAMPLE },
            )
        }
    }
}
