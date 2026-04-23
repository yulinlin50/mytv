package top.yogiczy.mytv.tv.ui.material

import androidx.compose.foundation.background
import androidx.compose.foundation.border

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

/**
 * TV 专用的 Slider 组件
 * 支持遥控器方向键控制和触摸/鼠标拖动
 *
 * @param value 当前值 (0f - 1f)
 * @param onValueChange 值变化回调
 * @param onValueChangeFinished 值变化结束回调（用户停止拖动或长按确认键时）
 * @param modifier 修饰符
 * @param colors 颜色配置
 * @param thumbSize 滑块大小
 * @param trackHeight 轨道高度
 * @param tvStep TV遥控器步进值（默认0.02，即2%）
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    colors: SliderColors = SliderDefaults.colors(),
    thumbSize: Dp = 16.dp,
    trackHeight: Dp = 4.dp,
    tvStep: Float = 0.02f,
) {
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    var isDragging by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(value.coerceIn(0f, 1f)) }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    LaunchedEffect(value) {
        if (!isDragging) {
            sliderValue = value.coerceIn(0f, 1f)
        }
    }

    val onDragEnd: () -> Unit = {
        if (isDragging) {
            isDragging = false
            currentOnValueChangeFinished?.invoke()
        }
    }

    var sliderWidth by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onSizeChanged { size ->
                sliderWidth = size.width
            }
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .handleKeyEvents(
                onLeft = {
                    val newValue = (sliderValue - tvStep).coerceIn(0f, 1f)
                    sliderValue = newValue
                    currentOnValueChange(newValue)
                },
                onRight = {
                    val newValue = (sliderValue + tvStep).coerceIn(0f, 1f)
                    sliderValue = newValue
                    currentOnValueChange(newValue)
                },
                onLongLeft = {
                    val newValue = (sliderValue - tvStep * 5).coerceIn(0f, 1f)
                    sliderValue = newValue
                    currentOnValueChange(newValue)
                },
                onLongRight = {
                    val newValue = (sliderValue + tvStep * 5).coerceIn(0f, 1f)
                    sliderValue = newValue
                    currentOnValueChange(newValue)
                },
                onSelect = {
                    focusRequester.requestFocus()
                },
                onLongSelect = {
                    currentOnValueChangeFinished?.invoke()
                },
            )
            .pointerInput(sliderWidth) {
                if (sliderWidth == 0) return@pointerInput

                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = onDragEnd,
                ) { change, _ ->
                    val dragX = change.position.x
                    val newProgress = (dragX / sliderWidth).coerceIn(0f, 1f)
                    sliderValue = newProgress
                    currentOnValueChange(newProgress)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(colors.trackColor)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(sliderValue)
                .fillMaxHeight()
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(colors.progressColor)
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = with(density) {
                    val availableWidth = (sliderWidth - thumbSize.toPx()).coerceAtLeast(0f)
                    (sliderValue * availableWidth).toDp()
                })
                .size(thumbSize)
                .clip(CircleShape)
                .background(
                    if (isDragging) colors.thumbActiveColor else colors.thumbColor
                )
        )
    }
}

data class SliderColors(
    val trackColor: Color,
    val progressColor: Color,
    val thumbColor: Color,
    val thumbActiveColor: Color,
)

object SliderDefaults {
    @Composable
    fun colors(
        trackColor: Color = MaterialTheme.colorScheme.surface.copy(0.2f),
        progressColor: Color = MaterialTheme.colorScheme.onSurface,
        thumbColor: Color = MaterialTheme.colorScheme.onSurface,
        thumbActiveColor: Color = MaterialTheme.colorScheme.onSurface,
    ): SliderColors {
        return SliderColors(
            trackColor,
            progressColor,
            thumbColor,
            thumbActiveColor,
        )
    }
}

@Preview
@Composable
private fun SliderPreview() {
    MyTvTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            var sliderValue by remember { mutableFloatStateOf(0.5f) }

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                thumbSize = 16.dp,
                trackHeight = 4.dp,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                colors = SliderDefaults.colors(
                    trackColor = MaterialTheme.colorScheme.surface.copy(0.3f),
                    progressColor = MaterialTheme.colorScheme.primary,
                    thumbColor = MaterialTheme.colorScheme.primary,
                    thumbActiveColor = MaterialTheme.colorScheme.onPrimary,
                ),
                thumbSize = 20.dp,
                trackHeight = 6.dp,
            )
        }
    }
}
