package top.yogiczy.mytv.tv.ui.material

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yogiczy.mytv.tv.ui.utils.backHandler
import top.yogiczy.mytv.tv.ui.utils.saveFocusRestorer
import top.yogiczy.mytv.tv.ui.utils.saveRequestFocus

@Composable
fun LazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal =
        if (!reverseLayout) Arrangement.Start else Arrangement.End,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    backHandler: Boolean = false,
    initialFocusRequester: FocusRequester? = null,
    content: LazyListScope.(LazyListRuntime) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val firstItemFocusRequester = remember { FocusRequester() }
    val lastItemFocusRequester = remember { FocusRequester() }
    var isFirstItemFocused by remember { mutableStateOf(false) }
    
    val itemCount by remember { derivedStateOf { state.layoutInfo.totalItemsCount } }
    val itemFocusRequesters = remember(itemCount) {
        List(itemCount) { FocusRequester() }
    }

    fun scrollToFirst() {
        coroutineScope.launch {
            // 优化：使用animateScrollToItem实现平滑滚动
            state.animateScrollToItem(0)
            firstItemFocusRequester.requestFocus()
        }
    }

    fun scrollToLast() {
        coroutineScope.launch {
            // 优化：使用animateScrollToItem实现平滑滚动
            state.animateScrollToItem((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            lastItemFocusRequester.requestFocus()
        }
    }

    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier
            .focusGroup()
            .saveFocusRestorer { initialFocusRequester ?: firstItemFocusRequester }
            .backHandler({ backHandler && !isFirstItemFocused }) {
                coroutineScope.launch {
                    scrollToFirst()
                    firstItemFocusRequester.saveRequestFocus()
                }
            },
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        content = {
            content(
                LazyListRuntime(
                    direction = LazyListDirection.Horizontal,
                    listState = state,
                    itemFocusRequesters = itemFocusRequesters,
                    firstItemFocusRequester = firstItemFocusRequester,
                    lastItemFocusRequester = lastItemFocusRequester,
                    onFirstItemFocusChanged = { isFirstItemFocused = it },
                    scrollToFirst = { scrollToFirst() },
                    scrollToLast = { scrollToLast() },
                )
            )
        },
    )
}
