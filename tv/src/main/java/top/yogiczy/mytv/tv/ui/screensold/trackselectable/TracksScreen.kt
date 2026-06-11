package top.yogiczy.mytv.tv.ui.screensold.trackselectable

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.material.Drawer
import top.yogiczy.mytv.tv.ui.material.DrawerPosition
import top.yogiczy.mytv.tv.ui.screensold.components.rememberScreenAutoCloseState
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.PlayerMetadata
import top.yogiczy.mytv.tv.ui.utils.backHandler
import top.yogiczy.mytv.tv.ui.utils.gridColumns

@Composable
fun <T : PlayerMetadata.TrackSelectable> TracksScreen(
    modifier: Modifier = Modifier,
    title: String = "",
    trackListProvider: () -> List<T> = { emptyList() },
    onTrackChanged: (T?) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val screenAutoCloseState = rememberScreenAutoCloseState(onTimeout = onClose)

    Drawer(
        modifier = modifier.backHandler { onClose() },
        onDismissRequest = onClose,
        position = DrawerPosition.End,
        header = { Text(title) },
    ) {
        TrackSelectableItemList(
            modifier = Modifier.width(4.5f.gridColumns()),
            trackListProvider = trackListProvider,
            onSelected = onTrackChanged,
            onUserAction = { screenAutoCloseState.active() },
        )
    }
}
