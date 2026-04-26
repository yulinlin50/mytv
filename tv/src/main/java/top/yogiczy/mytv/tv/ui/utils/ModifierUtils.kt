package top.yogiczy.mytv.tv.ui.utils

import android.os.Build
import android.view.KeyEvent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import top.yogiczy.mytv.tv.ui.theme.LAYOUT_GRID_SPACING
import top.yogiczy.mytv.tv.ui.theme.LAYOUT_GRID_WIDTH
import kotlin.math.absoluteValue

fun Modifier.ifElse(
    condition: () -> Boolean, ifTrueModifier: Modifier, ifFalseModifier: Modifier = Modifier
): Modifier = then(if (condition()) ifTrueModifier else ifFalseModifier)

fun Modifier.ifElse(
    condition: Boolean, ifTrueModifier: Modifier, ifFalseModifier: Modifier = Modifier
): Modifier = ifElse({ condition }, ifTrueModifier, ifFalseModifier)

fun Modifier.focusOnLaunched(key: Any = Unit): Modifier = composed {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(key) {
        focusRequester.saveRequestFocus()
    }
    focusRequester(focusRequester)
}

fun Modifier.focusOnLaunchedSaveable(key: Any = Unit): Modifier = composed {
    val focusRequester = remember { FocusRequester() }
    var hasFocused by rememberSaveable(key) { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasFocused) {
            focusRequester.saveRequestFocus()
            hasFocused = true
        }
    }
    focusRequester(focusRequester)
}

fun Modifier.handleKeyEvents(
    onKeyTap: Map<Int, (() -> Unit)?> = emptyMap(),
    onKeyLongTap: Map<Int, (() -> Unit)?> = emptyMap(),
    debounceMs: Long = 150L, // 默认防抖时间150ms
): Modifier = composed {
    val keyDownMap = remember { mutableMapOf<Int, Boolean>() }
    val lastKeyEventTime = remember { mutableMapOf<Int, Long>() }
    val currentOnKeyTap by rememberUpdatedState(onKeyTap)
    val currentOnKeyLongTap by rememberUpdatedState(onKeyLongTap)

    onPreviewKeyEvent { event ->
        val keyCode = event.nativeKeyEvent.keyCode
        val currentTime = System.currentTimeMillis()

        when (event.nativeKeyEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                // 防抖检查：如果距离上次按键时间太短，忽略此次按键
                val lastTime = lastKeyEventTime[keyCode] ?: 0
                if (currentTime - lastTime < debounceMs && event.nativeKeyEvent.repeatCount == 0) {
                    return@onPreviewKeyEvent true // 消费事件但不做处理
                }

                if (event.nativeKeyEvent.repeatCount == 0) {
                    keyDownMap[keyCode] = true
                    lastKeyEventTime[keyCode] = currentTime
                    false
                } else if (event.nativeKeyEvent.repeatCount == 1) {
                    val handled = currentOnKeyLongTap[keyCode] != null
                    if (handled) {
                        keyDownMap.remove(keyCode)
                        lastKeyEventTime[keyCode] = currentTime
                        currentOnKeyLongTap[keyCode]?.invoke()
                    }
                    handled
                } else {
                    false
                }
            }

            KeyEvent.ACTION_UP -> {
                if (keyDownMap[keyCode] == true) {
                    keyDownMap.remove(keyCode)
                    lastKeyEventTime[keyCode] = currentTime
                    val handled = currentOnKeyTap[keyCode] != null
                    if (handled) {
                        currentOnKeyTap[keyCode]?.invoke()
                    }
                    handled
                } else {
                    false
                }
            }

            else -> false
        }
    }
}

fun Modifier.handleDragGestures(
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
) = composed {
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)

    val speedThreshold = 100.dp
    val distanceThreshold = 10.dp

    pointerInput(Unit) {
        var verticalDragOffset = 0f
        var verticalTracker: VelocityTracker? = null

        detectVerticalDragGestures(
            onDragStart = {
                verticalDragOffset = 0f
                verticalTracker = VelocityTracker()
            },
            onDragEnd = {
                verticalTracker?.let { tracker ->
                    if (verticalDragOffset.absoluteValue > distanceThreshold.toPx()) {
                        if (tracker.calculateVelocity().y > speedThreshold.toPx()) {
                            currentOnSwipeDown()
                        } else if (tracker.calculateVelocity().y < -speedThreshold.toPx()) {
                            currentOnSwipeUp()
                        }
                    }
                }
            },
        ) { change, dragAmount ->
            verticalDragOffset += dragAmount
            verticalTracker?.addPosition(change.uptimeMillis, change.position)
        }
    }
        .pointerInput(Unit) {
            var horizontalDragOffset = 0f
            var horizontalTracker: VelocityTracker? = null

            detectHorizontalDragGestures(
                onDragStart = {
                    horizontalDragOffset = 0f
                    horizontalTracker = VelocityTracker()
                },
                onDragEnd = {
                    horizontalTracker?.let { tracker ->
                        if (horizontalDragOffset.absoluteValue > distanceThreshold.toPx()) {
                            if (tracker.calculateVelocity().x > speedThreshold.toPx()) {
                                currentOnSwipeRight()
                            } else if (tracker.calculateVelocity().x < -speedThreshold.toPx()) {
                                currentOnSwipeLeft()
                            }
                        }
                    }
                },
            ) { change, dragAmount ->
                horizontalDragOffset += dragAmount
                horizontalTracker?.addPosition(change.uptimeMillis, change.position)
            }
        }
}

fun Modifier.clickableNoIndication(
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)

    pointerInput(onClick, onLongClick, onDoubleClick) {
        detectTapGestures(
            onDoubleTap = currentOnDoubleClick?.let { { _ -> it() } },
            onLongPress = currentOnLongClick?.let { { _ -> it() } },
            onTap = currentOnClick?.let { { _ -> it() } },
        )
    }
}

/**
 * 支持TV遥控器和触摸的统一点击处理
 * 优先使用TV遥控器的焦点系统，同时支持触摸点击
 */
fun Modifier.handleClick(
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)

    this
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = currentOnDoubleClick?.let { { _ -> it() } },
                onLongPress = currentOnLongClick?.let { { _ -> it() } },
                onTap = currentOnClick?.let { { _ -> it() } },
            )
        }
}

fun Modifier.handleKeyEvents(
    onLeft: (() -> Unit)? = null,
    onLongLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onLongRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onLongUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLongDown: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onLongSelect: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onNumber: ((Int) -> Unit)? = null,
) = handleKeyEvents(
    onKeyTap = buildMap {
        put(KeyEvent.KEYCODE_DPAD_LEFT, onLeft)
        put(KeyEvent.KEYCODE_DPAD_RIGHT, onRight)
        put(KeyEvent.KEYCODE_DPAD_UP, onUp)
        put(KeyEvent.KEYCODE_CHANNEL_UP, onUp)
        put(KeyEvent.KEYCODE_DPAD_DOWN, onDown)
        put(KeyEvent.KEYCODE_CHANNEL_DOWN, onDown)

        put(KeyEvent.KEYCODE_DPAD_CENTER, onSelect)
        put(KeyEvent.KEYCODE_ENTER, onSelect)
        put(KeyEvent.KEYCODE_NUMPAD_ENTER, onSelect)

        put(KeyEvent.KEYCODE_MENU, onSettings)
        put(KeyEvent.KEYCODE_SETTINGS, onSettings)
        put(KeyEvent.KEYCODE_HELP, onSettings)
        put(KeyEvent.KEYCODE_H, onSettings)

        put(KeyEvent.KEYCODE_L, onLongSelect)
        put(KeyEvent.KEYCODE_W, onLongUp)
        put(KeyEvent.KEYCODE_S, onLongDown)
        put(KeyEvent.KEYCODE_A, onLongLeft)
        put(KeyEvent.KEYCODE_D, onLongRight)

        put(KeyEvent.KEYCODE_0, onNumber?.let { { it(0) } })
        put(KeyEvent.KEYCODE_1, onNumber?.let { { it(1) } })
        put(KeyEvent.KEYCODE_2, onNumber?.let { { it(2) } })
        put(KeyEvent.KEYCODE_3, onNumber?.let { { it(3) } })
        put(KeyEvent.KEYCODE_4, onNumber?.let { { it(4) } })
        put(KeyEvent.KEYCODE_5, onNumber?.let { { it(5) } })
        put(KeyEvent.KEYCODE_6, onNumber?.let { { it(6) } })
        put(KeyEvent.KEYCODE_7, onNumber?.let { { it(7) } })
        put(KeyEvent.KEYCODE_8, onNumber?.let { { it(8) } })
        put(KeyEvent.KEYCODE_9, onNumber?.let { { it(9) } })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT, onLeft)
            put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT, onRight)
            put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP, onUp)
            put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN, onDown)
        }
    },
    onKeyLongTap = mapOf(
        KeyEvent.KEYCODE_DPAD_LEFT to onLongLeft,
        KeyEvent.KEYCODE_DPAD_RIGHT to onLongRight,
        KeyEvent.KEYCODE_DPAD_UP to onLongUp,
        KeyEvent.KEYCODE_CHANNEL_UP to onLongUp,
        KeyEvent.KEYCODE_DPAD_DOWN to onLongDown,
        KeyEvent.KEYCODE_CHANNEL_DOWN to onLongDown,

        KeyEvent.KEYCODE_ENTER to onLongSelect,
        KeyEvent.KEYCODE_NUMPAD_ENTER to onLongSelect,
        KeyEvent.KEYCODE_DPAD_CENTER to onLongSelect,
    ),
)
    .clickableNoIndication(
        onClick = onSelect,
        onLongClick = onLongSelect,
        onDoubleClick = onSettings,
    )

/**
 * 仅处理遥控器按键事件，不包含触摸点击处理
 * 适用于需要通过其他方式（如 ListItem.onClick）处理触摸点击的场景
 * 与 handleKeyEvents 的区别：不添加 clickableNoIndication 修饰符
 */
fun Modifier.handleKeyEventsNoTouch(
    onLeft: (() -> Unit)? = null,
    onLongLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onLongRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onLongUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLongDown: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onLongSelect: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onNumber: ((Int) -> Unit)? = null,
) = handleKeyEvents(
    onKeyTap = buildMap {
        put(KeyEvent.KEYCODE_DPAD_LEFT, onLeft)
        put(KeyEvent.KEYCODE_DPAD_RIGHT, onRight)
        put(KeyEvent.KEYCODE_DPAD_UP, onUp)
        put(KeyEvent.KEYCODE_CHANNEL_UP, onUp)
        put(KeyEvent.KEYCODE_DPAD_DOWN, onDown)
        put(KeyEvent.KEYCODE_CHANNEL_DOWN, onDown)

        put(KeyEvent.KEYCODE_DPAD_CENTER, onSelect)
        put(KeyEvent.KEYCODE_ENTER, onSelect)
        put(KeyEvent.KEYCODE_NUMPAD_ENTER, onSelect)

        put(KeyEvent.KEYCODE_MENU, onSettings)
        put(KeyEvent.KEYCODE_SETTINGS, onSettings)
        put(KeyEvent.KEYCODE_HELP, onSettings)
        put(KeyEvent.KEYCODE_H, onSettings)

        put(KeyEvent.KEYCODE_L, onLongSelect)
        put(KeyEvent.KEYCODE_W, onLongUp)
        put(KeyEvent.KEYCODE_S, onLongDown)
        put(KeyEvent.KEYCODE_A, onLongLeft)
        put(KeyEvent.KEYCODE_D, onLongRight)

        put(KeyEvent.KEYCODE_0, onNumber?.let { { it(0) } })
        put(KeyEvent.KEYCODE_1, onNumber?.let { { it(1) } })
        put(KeyEvent.KEYCODE_2, onNumber?.let { { it(2) } })
        put(KeyEvent.KEYCODE_3, onNumber?.let { { it(3) } })
        put(KeyEvent.KEYCODE_4, onNumber?.let { { it(4) } })
        put(KeyEvent.KEYCODE_5, onNumber?.let { { it(5) } })
        put(KeyEvent.KEYCODE_6, onNumber?.let { { it(6) } })
        put(KeyEvent.KEYCODE_7, onNumber?.let { { it(7) } })
        put(KeyEvent.KEYCODE_8, onNumber?.let { { it(8) } })
        put(KeyEvent.KEYCODE_9, onNumber?.let { { it(9) } })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT, onLeft)
            put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT, onRight)
            put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP, onUp)
            put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN, onDown)
        }
    },
    onKeyLongTap = mapOf(
        KeyEvent.KEYCODE_DPAD_LEFT to onLongLeft,
        KeyEvent.KEYCODE_DPAD_RIGHT to onLongRight,
        KeyEvent.KEYCODE_DPAD_UP to onLongUp,
        KeyEvent.KEYCODE_CHANNEL_UP to onLongUp,
        KeyEvent.KEYCODE_DPAD_DOWN to onLongDown,
        KeyEvent.KEYCODE_CHANNEL_DOWN to onLongDown,

        KeyEvent.KEYCODE_ENTER to onLongSelect,
        KeyEvent.KEYCODE_NUMPAD_ENTER to onLongSelect,
        KeyEvent.KEYCODE_DPAD_CENTER to onLongSelect,
    ),
)

fun Modifier.handleKeyEvents(
    isFocused: () -> Boolean,
    focusRequester: FocusRequester,
    onLeft: (() -> Unit)? = null,
    onLongLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onLongRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onLongUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLongDown: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onLongSelect: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onNumber: ((Int) -> Unit)? = null,
) = handleKeyEvents(
    onLeft = onLeft?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onLongLeft = onLongLeft?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onRight = onRight?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onLongRight = onLongRight?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onUp = onUp?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onLongUp = onLongUp?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onDown = onDown?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onLongDown = onLongDown?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onSelect = onSelect?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onLongSelect = onLongSelect?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onSettings = onSettings?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onNumber = onNumber?.let { { num -> if (isFocused()) it(num) else focusRequester.saveRequestFocus() } },
)

fun Modifier.backHandler(onBackPressed: () -> Unit) = composed {
    val currentOnBackPressed by rememberUpdatedState(onBackPressed)
    this.onPreviewKeyEvent {
        if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
            // 检查是否有弹窗显示，如果有则不处理返回事件（让弹窗优先处理）
            if (top.yogiczy.mytv.tv.ui.material.PopupVisibilityState.hasVisiblePopup()) {
                false
            } else {
                currentOnBackPressed()
                true
            }
        } else {
            false
        }
    }
}

fun Modifier.backHandler(
    condition: () -> Boolean,
    onBackPressed: () -> Unit,
) = composed {
    val currentCondition by rememberUpdatedState(condition)
    val currentOnBackPressed by rememberUpdatedState(onBackPressed)
    this.onPreviewKeyEvent {
        if (it.key == Key.Back && it.type == KeyEventType.KeyUp && currentCondition()) {
            // 检查是否有弹窗显示，如果有则不处理返回事件（让弹窗优先处理）
            if (top.yogiczy.mytv.tv.ui.material.PopupVisibilityState.hasVisiblePopup()) {
                false
            } else {
                currentOnBackPressed()
                true
            }
        } else {
            false
        }
    }
}

fun Modifier.visible(visible: Boolean) = alpha(if (visible) 1f else 0f)

fun Int.gridColumns() = (LAYOUT_GRID_WIDTH * this + LAYOUT_GRID_SPACING * (this - 1)).dp
fun Float.gridColumns() = (LAYOUT_GRID_WIDTH * this + LAYOUT_GRID_SPACING * (this - 1)).dp

fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.saveFocusRestorer(onRestoreFailed: (() -> FocusRequester)? = null): Modifier {
    if (!Configs.uiFocusOptimize) return this

    return focusRestorer {
        if (onRestoreFailed == null) return@focusRestorer FocusRequester.Default

        val result = onRestoreFailed()
        runCatching {
            result.requestFocus()
            result
        }.getOrElse { FocusRequester.Default }
    }
}

fun FocusRequester.saveRequestFocus() = runCatching { requestFocus() }

fun Modifier.focusDebugLog(tag: String): Modifier = composed {
    this.onFocusChanged { state ->
        android.util.Log.d("FocusDebug", "[$tag] isFocused=${state.isFocused}, hasFocus=${state.hasFocus}")
    }
}