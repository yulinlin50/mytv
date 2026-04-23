package top.yogiczy.mytv.tv.ui.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.components.AppThemeDef
import top.yogiczy.mytv.tv.ui.screen.components.AppThemeWrapper
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.clickableNoIndication
import top.yogiczy.mytv.tv.ui.utils.gridColumns

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Drawer(
    modifier: Modifier = Modifier,
    showProvider: () -> Boolean = { true },
    onDismissRequest: (() -> Unit)? = null,
    position: DrawerPosition = DrawerPosition.End,
    header: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    if (!showProvider()) return

    val alignment = when (position) {
        DrawerPosition.Start -> Alignment.TopStart
        DrawerPosition.End -> Alignment.TopEnd
        DrawerPosition.Top -> Alignment.TopStart
        DrawerPosition.Bottom -> Alignment.BottomStart
        DrawerPosition.Center -> Alignment.Center
    }

    val positionModifier = when (position) {
        DrawerPosition.Start -> Modifier.fillMaxHeight()
        DrawerPosition.End -> Modifier.fillMaxHeight()
        DrawerPosition.Top -> Modifier.fillMaxWidth()
        DrawerPosition.Bottom -> Modifier.fillMaxWidth()
        DrawerPosition.Center -> Modifier
    }

    val appThemeDef = settingsVM.themeAppCurrent
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val maxHeightModifier = when (position) {
        DrawerPosition.Center -> Modifier.height(screenHeight * 0.8f)
        else -> Modifier
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (showProvider()) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .clickableNoIndication { onDismissRequest?.invoke() },
    ) {
        Box(
            modifier = modifier
                .align(alignment)
                .then(positionModifier)
                .then(maxHeightModifier)
                .clip(MaterialTheme.shapes.large)
                .clickableNoIndication { }
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                .padding(20.dp)
                .focusRequester(focusRequester),
        ) {
            Column {
                header?.let { nnHeader ->
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.titleLarge
                    ) {
                        Box(
                            modifier = Modifier.padding(
                                top = 8.dp,
                                bottom = 16.dp,
                                start = 16.dp,
                                end = 16.dp
                            )
                        ) {
                            nnHeader()
                        }
                    }
                }

                content()
            }
        }
    }
}

enum class DrawerPosition {
    Start, End, Top, Bottom, Center
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun DrawerPreviewStart() {
    MyTvTheme {
        PreviewWithLayoutGrids {
            Drawer(
                position = DrawerPosition.Start,
                header = { Text("Header") },
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun DrawerPreviewEnd() {
    MyTvTheme {
        PreviewWithLayoutGrids {
            Drawer(
                position = DrawerPosition.End,
                header = { Text("Header") },
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun DrawerPreviewTop() {
    MyTvTheme {
        PreviewWithLayoutGrids {
            Drawer(
                position = DrawerPosition.Top,
                header = { Text("Header") },
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun DrawerPreviewBottom() {
    MyTvTheme {
        PreviewWithLayoutGrids {
            Drawer(
                position = DrawerPosition.Bottom,
                header = { Text("Header") },
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun DrawerPreviewCenter() {
    MyTvTheme {
        PreviewWithLayoutGrids {
            Drawer(
                position = DrawerPosition.Center,
                header = { Text("Header") },
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun DrawerPreviewBottomNoHeader() {
    MyTvTheme {
        PreviewWithLayoutGrids {
            Drawer(
                position = DrawerPosition.Bottom,
            ) {
                Text("Content")
            }
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun DrawerPreview() {
    MyTvTheme {
        AppScreen {
            Drawer(
                position = DrawerPosition.End,
                header = { Text("Header") },
            ) {
                Text("Content")
            }
        }
    }
}
