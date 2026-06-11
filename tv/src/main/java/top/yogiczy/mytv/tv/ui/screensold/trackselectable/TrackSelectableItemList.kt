package top.yogiczy.mytv.tv.ui.screensold.trackselectable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.PlayerMetadata
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunchedSaveable
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse
import kotlin.math.max

@Composable
fun <T : PlayerMetadata.TrackSelectable> TrackSelectableItemList(
    modifier: Modifier = Modifier,
    trackListProvider: () -> List<T> = { emptyList() },
    onSelected: (T?) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    val trackList = trackListProvider()
    val noneSelected = trackList.all { it.trackIsSelected != true }

    val listState =
        rememberLazyListState(max(0, trackList.indexOfFirst { it.trackIsSelected == true } - 2))

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { _ -> onUserAction() }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ListItem(
                modifier = Modifier
                    .ifElse(noneSelected, Modifier.focusOnLaunchedSaveable())
                    .handleKeyEvents(onSelect = { onSelected(null) }),
                selected = false,
                onClick = {},
                headlineContent = { Text("关闭") },
                trailingContent = {
                    RadioButton(selected = noneSelected, onClick = {})
                },
            )
        }

        items(
            trackList,
            key = { it.trackIndex ?: 0 },
            contentType = { "track" }
        ) { track ->
            val isSelected = track.trackIsSelected == true
            val isSupported = track.trackIsSupported

            ListItem(
                modifier = Modifier
                    .ifElse(isSelected, Modifier.focusOnLaunchedSaveable())
                    .handleKeyEvents(onSelect = { onSelected(track) }),
                selected = false,
                onClick = {},
                headlineContent = {
                    Text(
                        text = buildString {
                            append(track.trackLabel)
                            if (isSelected) append(" ✓")
                            if (!isSupported) append(" [不支持]")
                        },
                        color = if (!isSupported) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        } else {
                            Color.Unspecified
                        },
                        textDecoration = if (!isSupported) {
                            TextDecoration.LineThrough
                        } else {
                            null
                        }
                    )
                },
                trailingContent = {
                    RadioButton(selected = isSelected, onClick = {})
                },
            )
        }
    }
}
