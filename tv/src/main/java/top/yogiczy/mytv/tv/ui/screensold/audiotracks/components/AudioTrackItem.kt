package top.yogiczy.mytv.tv.ui.screensold.audiotracks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.PlayerMetadata
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunchedSaveable
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse

@Composable
fun AudioTrackItem(
    modifier: Modifier = Modifier,
    trackProvider: () -> PlayerMetadata.AudioTrack = { PlayerMetadata.AudioTrack() },
    onSelected: () -> Unit = {},
) {
    val track = trackProvider()

    ListItem(
        modifier = modifier
            .ifElse(track.isSelected == true, Modifier.focusOnLaunchedSaveable())
            .handleKeyEvents(onSelect = onSelected),
        selected = false,
        onClick = {},
        headlineContent = { 
            Text(
                text = buildString {
                    append(track.shortLabel)
                    if (track.isSelected == true) {
                        append(" ✓")
                    }
                    if (!track.isSupported) {
                        append(" [不支持]")
                    }
                },
                color = if (!track.isSupported) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    Color.Unspecified
                },
                textDecoration = if (!track.isSupported) {
                    TextDecoration.LineThrough
                } else {
                    null
                }
            ) 
        },
        trailingContent = {
            RadioButton(selected = track.isSelected == true, onClick = {})
        },
    )
}

@Preview
@Composable
private fun AudioTrackItemPreview() {
    MyTvTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AudioTrackItem(
                trackProvider = {
                    PlayerMetadata.AudioTrack(
                        channels = 2,
                        bitrate = 128000,
                    )
                },
            )

            AudioTrackItem(
                trackProvider = {
                    PlayerMetadata.AudioTrack(
                        channels = 10,
                        bitrate = 128000,
                        isSelected = true,
                    )
                },
            )
        }
    }
}