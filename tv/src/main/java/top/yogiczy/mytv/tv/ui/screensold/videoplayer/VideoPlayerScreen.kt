package top.yogiczy.mytv.tv.ui.screensold.videoplayer

import android.graphics.Typeface
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import top.yogiczy.mytv.tv.ui.material.Visibility
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.components.VideoPlayerError
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.components.VideoPlayerMetadata
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.Media3VideoPlayerNew
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.PlayerMetadata
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.VideoPlayerStateNew
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.rememberVideoPlayerStateNew
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.Configs

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    modifier: Modifier = Modifier,
    state: VideoPlayerStateNew = rememberVideoPlayerStateNew(),
    showMetadataProvider: () -> Boolean = { false },
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        val displayModeModifier = when (state.displayMode) {
            VideoPlayerDisplayMode.ORIGINAL -> Modifier.aspectRatio(state.aspectRatio)
            VideoPlayerDisplayMode.FILL -> Modifier.fillMaxSize()
            VideoPlayerDisplayMode.CROP -> Modifier
                .fillMaxWidth()
                .aspectRatio(state.aspectRatio)

            VideoPlayerDisplayMode.FOUR_THREE -> Modifier.aspectRatio(4f / 3)
            VideoPlayerDisplayMode.SIXTEEN_NINE -> Modifier.aspectRatio(16f / 9)
            VideoPlayerDisplayMode.TWO_THIRTY_FIVE_ONE -> Modifier.aspectRatio(2.35f / 1)
        }

        when (settingsVM.videoPlayerRenderMode) {
            Configs.VideoPlayerRenderMode.SURFACE_VIEW -> {
                AndroidView(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .then(displayModeModifier),
                    factory = { SurfaceView(context) },
                    update = { state.setVideoSurfaceView(it) },
                )
            }

            Configs.VideoPlayerRenderMode.TEXTURE_VIEW -> {
                AndroidView(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .then(displayModeModifier),
                    factory = { TextureView(context) },
                    update = { state.setVideoTextureView(it) },
                )
            }
        }

        if (state.instance is Media3VideoPlayerNew) {
            val cues by state.instance.cues.collectAsState()
            val position = Configs.subtitleLivePosition
            val fontSize = Configs.subtitleLiveFontSize
            val textColor = Configs.subtitleLiveTextColor
            val bgColor = Configs.subtitleLiveBgColor

            // setBottomPaddingFraction: 从底部留出的空间比例
            // bottom → 0f (字幕在底部), center → 0.4f (中间偏下), top → 0.75f (上方)
            val bottomPaddingFraction = when (position) {
                "top" -> 0.75f
                "center" -> 0.4f
                else -> 0f
            }

            val fontSizeFraction = when (fontSize) {
                "small" -> 0.04f
                "large" -> 0.08f
                else -> 0.06f
            }

            val captionStyle = CaptionStyleCompat(
                when (textColor) {
                    "yellow" -> android.graphics.Color.YELLOW
                    "green" -> android.graphics.Color.GREEN
                    "cyan" -> android.graphics.Color.CYAN
                    else -> android.graphics.Color.WHITE
                },
                when (bgColor) {
                    "black" -> android.graphics.Color.BLACK
                    "none" -> android.graphics.Color.TRANSPARENT
                    else -> android.graphics.Color.argb(180, 0, 0, 0) // semi-transparent
                },
                android.graphics.Color.TRANSPARENT, // windowColor
                CaptionStyleCompat.EDGE_TYPE_NONE,
                android.graphics.Color.TRANSPARENT, // edgeColor
                Typeface.DEFAULT,
            )

            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomCenter),
                factory = { ctx ->
                    SubtitleView(ctx).apply {
                        setApplyEmbeddedStyles(false)
                        setStyle(captionStyle)
                        setFractionalTextSize(fontSizeFraction)
                        setBottomPaddingFraction(bottomPaddingFraction)
                    }
                },
                update = { view ->
                    view.setCues(cues)
                    view.setStyle(captionStyle)
                    view.setFractionalTextSize(fontSizeFraction)
                    view.setBottomPaddingFraction(bottomPaddingFraction)
                },
            )
        }
    }

    VideoPlayerScreenCover(
        showMetadataProvider = showMetadataProvider,
        metadataProvider = state::metadata,
        errorProvider = state::error,
    )
}

@Composable
private fun VideoPlayerScreenCover(
    modifier: Modifier = Modifier,
    showMetadataProvider: () -> Boolean = { false },
    metadataProvider: () -> PlayerMetadata = { PlayerMetadata() },
    errorProvider: () -> String? = { null },
) {
    val childPadding = rememberChildPadding()

    Box(modifier = modifier.fillMaxSize()) {
        Visibility(showMetadataProvider) {
            VideoPlayerMetadata(
                modifier = Modifier.padding(start = childPadding.start, top = childPadding.top),
                metadataProvider = metadataProvider,
            )
        }

        VideoPlayerError(
            modifier = Modifier.align(Alignment.Center),
            errorProvider = errorProvider,
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun VideoPlayerScreenCoverPreview() {
    MyTvTheme {
        PreviewWithLayoutGrids {
            VideoPlayerScreenCover(
                showMetadataProvider = { true },
                metadataProvider = {
                    PlayerMetadata(
                        video = PlayerMetadata.VideoTrack(
                            width = 1920,
                            height = 1080,
                            color = "BT2020/Limited range/HLG/8/8",
                            bitrate = 10605096,
                            mimeType = "video/hevc",
                            decoder = "c2.goldfish.h264.decoder",
                        ),

                        audio = PlayerMetadata.AudioTrack(
                            channels = 2,
                            sampleRate = 32000,
                            bitrate = 256 * 1024,
                            mimeType = "audio/mp4a-latm",
                        ),
                    )
                },
                errorProvider = { "ERROR_CODE_BEHIND_LIVE_WINDOW" },
            )
        }
    }
}