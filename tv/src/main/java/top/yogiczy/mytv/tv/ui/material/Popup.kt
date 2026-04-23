package top.yogiczy.mytv.tv.ui.material

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.MaterialTheme
import top.yogiczy.mytv.tv.ui.utils.clickableNoIndication
import top.yogiczy.mytv.tv.ui.utils.ifElse
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import java.util.UUID

class PopupManager {
    private val stack = mutableListOf<StackItem>()

    fun push(focusRequester: FocusRequester, emitter: Boolean = false) {
        stack.add(StackItem(focusRequester, emitter))
    }

    fun pop() {
        try {
            if (stack.isNotEmpty()) {
                stack.removeAt(stack.lastIndex)
                val last = stack.lastOrNull()
                last?.focusRequester?.requestFocus()
                if (last?.emitter == true) {
                    stack.remove(last)
                    stack.lastOrNull()?.focusRequester?.requestFocus()
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            try {
                stack.lastOrNull()?.focusRequester?.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    class StackItem(
        val focusRequester: FocusRequester,
        val emitter: Boolean = false,
    )
}

val LocalPopupManager = compositionLocalOf { PopupManager() }

fun Modifier.popupable() = composed {
    val popupManager = LocalPopupManager.current
    val focusRequester = remember { FocusRequester() }

    DisposableEffect(Unit) {
        popupManager.push(focusRequester)
        onDispose { popupManager.pop() }
    }

    focusRequester(focusRequester).focusable()
}

/**
 * 全局弹窗显示状态追踪，用于处理返回键优先级
 */
object PopupVisibilityState {
    private var popupCount = 0

    @Synchronized
    fun increment() {
        popupCount++
    }

    @Synchronized
    fun decrement() {
        popupCount = (popupCount - 1).coerceAtLeast(0)
    }

    @Synchronized
    fun hasVisiblePopup(): Boolean = popupCount > 0
}

@Composable
fun PopupContent(
    modifier: Modifier = Modifier,
    visibleProvider: () -> Boolean,
    onDismissRequest: (() -> Unit)? = null,
    withBackground: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!visibleProvider()) return

    // 追踪弹窗显示状态
    DisposableEffect(Unit) {
        PopupVisibilityState.increment()
        onDispose { PopupVisibilityState.decrement() }
    }

    Box(
        modifier
            .fillMaxSize()
            .popupable()
            .clickableNoIndication { onDismissRequest?.invoke() }
            .onPreviewKeyEvent {
                if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                    onDismissRequest?.invoke()
                    true
                } else {
                    false
                }
            }
            .ifElse(
                withBackground,
                Modifier.background(MaterialTheme.colorScheme.background.copy(0.5f)),
            ),
    ) {
        content()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SimplePopup(
    modifier: Modifier = Modifier,
    visibleProvider: () -> Boolean,
    onDismissRequest: (() -> Unit)? = null,
    withBackground: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val visible = visibleProvider()
    val key = remember { UUID.randomUUID().toString() }
    val popupState = LocalPopupState.current

    if (visible) {
        if (popupState.none { it.key == key }) {
            popupState.add(
                PopupState(
                    key = key,
                    composableReference = {
                        PopupContent(
                            modifier = modifier
                                .focusRequester(FocusRequester())
                                .focusProperties { exit = { FocusRequester.Cancel } },
                            visibleProvider = visibleProvider,
                            onDismissRequest = onDismissRequest,
                            withBackground = true,
                            content = content,
                        )
                    },
                ),
            )
        }
    } else {
        popupState.removeAll { it.key == key }
    }
}

data class PopupState(
    val key: String = UUID.randomUUID().toString(),
    val composableReference: @Composable () -> Unit = {},
)

val LocalPopupState = compositionLocalOf { mutableStateListOf<PopupState>() }

@Composable
fun PopupHandleableApplication(
    applicationContent: @Composable () -> Unit
) {
    val popupState = LocalPopupState.current

    Box(modifier = Modifier.fillMaxSize()) {
        applicationContent()

        popupState.map {
            it.composableReference()
        }
    }
}
