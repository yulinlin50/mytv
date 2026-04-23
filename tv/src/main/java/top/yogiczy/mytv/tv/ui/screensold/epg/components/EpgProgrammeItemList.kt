package top.yogiczy.mytv.tv.ui.screensold.epg.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.epg.Epg
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunchedSaveable
import kotlin.math.max

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EpgProgrammeItemList(
    modifier: Modifier = Modifier,
    epgProgrammeListProvider: () -> EpgProgrammeList = { EpgProgrammeList() },
    epgProgrammeReserveListProvider: () -> EpgProgrammeReserveList = { EpgProgrammeReserveList() },
    supportPlaybackProvider: () -> Boolean = { false },
    canPlaybackProvider: (EpgProgramme) -> Boolean = { false },
    currentPlaybackProvider: () -> EpgProgramme? = { null },
    hasCatchupTagProvider: () -> Boolean = { false },
    hasEpgDataProvider: () -> Boolean = { false },
    onPlayback: (EpgProgramme) -> Unit = {},
    onReserve: (EpgProgramme) -> Unit = {},
    focusOnLive: Boolean = true,
    onUserAction: () -> Unit = {},
) {
    val epgProgrammeList = epgProgrammeListProvider()
    
    // 只计算一次当前直播节目的索引
    val now = System.currentTimeMillis()
    val liveIndex = remember(epgProgrammeList) {
        epgProgrammeList.indexOfFirst { now >= it.startAt && now < it.endAt }
    }

    val listState = remember { 
        LazyListState(max(0, liveIndex - 2)) 
    }
    
    LaunchedEffect(epgProgrammeList) {
        if (liveIndex != -1) {
            listState.scrollToItem(max(0, liveIndex - 2))
        }
    }
    
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { _ -> onUserAction() }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(
            epgProgrammeList,
            key = { index, programme -> "${programme.startAt}-${programme.endAt}-${programme.title}-$index" },
        ) { index, programme ->
            val isPlayback = remember(currentPlaybackProvider(), programme) {
                currentPlaybackProvider() == programme
            }
            val hasReserved = remember(epgProgrammeReserveListProvider(), programme.title) {
                epgProgrammeReserveListProvider().firstOrNull { it.programme == programme.title } != null
            }
            val itemFocusRequester = remember { FocusRequester() }
            val isLiveItem = remember(programme.startAt, programme.endAt) { 
                now >= programme.startAt && now < programme.endAt 
            }
            
            EpgProgrammeItem(
                modifier = if (isLiveItem && focusOnLive) {
                    Modifier.focusRequester(itemFocusRequester).focusOnLaunchedSaveable(programme.startAt)
                } else {
                    Modifier.focusRequester(itemFocusRequester)
                },
                epgProgrammeProvider = { programme },
                supportPlaybackProvider = supportPlaybackProvider,
                canPlaybackProvider = canPlaybackProvider,
                isPlaybackProvider = { isPlayback },
                hasCatchupTagProvider = hasCatchupTagProvider,
                hasEpgDataProvider = hasEpgDataProvider,
                hasReservedProvider = { hasReserved },
                onPlayback = { onPlayback(programme) },
                onReserve = { onReserve(programme) },
                focusOnLive = focusOnLive,
            )
        }
    }
}

@Preview
@Composable
private fun EpgProgrammeItemListPreview() {
    MyTvTheme {
        EpgProgrammeItemList(
            epgProgrammeListProvider = { EpgProgrammeList(Epg.example(Channel.EXAMPLE).programmeList) }
        )
    }
}
