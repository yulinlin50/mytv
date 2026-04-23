package top.yogiczy.mytv.tv.ui.screensold.audiotracks

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.material.Drawer
import top.yogiczy.mytv.tv.ui.material.DrawerPosition
import top.yogiczy.mytv.tv.ui.screensold.audiotracks.components.AudioTrackItemList
import top.yogiczy.mytv.tv.ui.screensold.components.rememberScreenAutoCloseState
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.PlayerMetadata
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.backHandler
import top.yogiczy.mytv.tv.ui.utils.gridColumns

@Composable
fun AudioTracksScreen(
    modifier: Modifier = Modifier,
    trackListProvider: () -> List<PlayerMetadata.AudioTrack> = { emptyList() },
    onTrackChanged: (PlayerMetadata.AudioTrack?) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val screenAutoCloseState = rememberScreenAutoCloseState(onTimeout = onClose)

    Drawer(
        modifier = modifier.backHandler { onClose() },
        onDismissRequest = onClose,
        position = DrawerPosition.End,
        header = { Text("音轨") },
    ) {
        AudioTrackItemList(
            modifier = Modifier.width(4.5f.gridColumns()),
            trackListProvider = trackListProvider,
            onSelected = onTrackChanged,
            onUserAction = { screenAutoCloseState.active() },
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun AudioTracksScreenPreview() {
    MyTvTheme {
        PreviewWithLayoutGrids {
            AudioTracksScreen(
                trackListProvider = {
                    listOf(
                        PlayerMetadata.AudioTrack(
                            channels = 2,
                            bitrate = 128000,
                        ),
                        PlayerMetadata.AudioTrack(
                            channels = 10,
                            bitrate = 567000,
                            isSelected = true,
                        )
                    )
                },
            )
        }
    }
}