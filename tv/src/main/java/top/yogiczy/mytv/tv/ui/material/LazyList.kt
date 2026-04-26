package top.yogiczy.mytv.tv.ui.material

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse

data class LazyListRuntime(
    val direction: LazyListDirection,
    val listState: LazyListState,
    val itemFocusRequesters: List<FocusRequester>,
    val firstItemFocusRequester: FocusRequester,
    val lastItemFocusRequester: FocusRequester,
    val onFirstItemFocusChanged: (Boolean) -> Unit,
    val scrollToFirst: () -> Unit,
    val scrollToLast: () -> Unit,
)

enum class LazyListDirection {
    Vertical,
    Horizontal,
}

@Composable
fun rememberLazyListRuntime(
    itemCount: Int,
    direction: LazyListDirection,
    listState: LazyListState = rememberLazyListState(),
    onFirstItemFocusChanged: (Boolean) -> Unit = {},
): LazyListRuntime {
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    
    val itemFocusRequesters = remember(itemCount) {
        List(itemCount) { FocusRequester() }
    }
    val firstItemFocusRequester = remember { FocusRequester() }
    val lastItemFocusRequester = remember { FocusRequester() }
    
    return remember(itemCount, direction, listState) {
        LazyListRuntime(
            direction = direction,
            listState = listState,
            itemFocusRequesters = itemFocusRequesters,
            firstItemFocusRequester = firstItemFocusRequester,
            lastItemFocusRequester = lastItemFocusRequester,
            onFirstItemFocusChanged = onFirstItemFocusChanged,
            scrollToFirst = {
                coroutineScope.launch {
                    listState.scrollToItem(0)
                    firstItemFocusRequester.requestFocus()
                }
            },
            scrollToLast = {
                coroutineScope.launch {
                    listState.scrollToItem(maxOf(0, itemCount - 1))
                    lastItemFocusRequester.requestFocus()
                }
            },
        )
    }
}

inline fun <T> LazyListScope.items(
    items: List<T>,
    runtime: LazyListRuntime,
    noinline key: ((item: T) -> Any)? = null,
    crossinline contentType: (item: T) -> Any? = { null },
    crossinline itemContent: @Composable LazyItemScope.(itemModifier: Modifier, item: T) -> Unit,
) = items(
    count = items.size,
    key = if (key != null) { index: Int -> key(items[index]) } else { index: Int -> index },
    contentType = { index -> contentType(items[index]) }
) { index ->
    val itemModifier = Modifier
        .focusRequester(runtime.itemFocusRequesters.getOrElse(index) { FocusRequester() })
        .then(
            when (index) {
                0 -> Modifier
                    .focusRequester(runtime.firstItemFocusRequester)
                    .onFocusChanged { state -> runtime.onFirstItemFocusChanged(state.isFocused) }
                    .handleKeyEvents(
                        onLeft = {
                            if (runtime.direction == LazyListDirection.Horizontal)
                                runtime.scrollToLast()
                        },
                        onUp = {
                            if (runtime.direction == LazyListDirection.Vertical)
                                runtime.scrollToLast()
                        },
                    )
                items.size - 1 -> Modifier
                    .focusRequester(runtime.lastItemFocusRequester)
                    .handleKeyEvents(
                        onRight = {
                            if (runtime.direction == LazyListDirection.Horizontal)
                                runtime.scrollToFirst()
                        },
                        onDown = {
                            if (runtime.direction == LazyListDirection.Vertical)
                                runtime.scrollToFirst()
                        },
                    )
                else -> Modifier
            }
        )
    itemContent(itemModifier, items[index])
}

inline fun <T> LazyListScope.itemsIndexed(
    items: List<T>,
    runtime: LazyListRuntime,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    crossinline contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    crossinline itemContent: @Composable (LazyItemScope.(itemModifier: Modifier, index: Int, item: T) -> Unit),
) = items(
    count = items.size,
    key = if (key != null) { index: Int -> key(index, items[index]) } else { index: Int -> index },
    contentType = { index -> contentType(index, items[index]) },
) { index ->
    val itemModifier = Modifier
        .focusRequester(runtime.itemFocusRequesters.getOrElse(index) { FocusRequester() })
        .then(
            when (index) {
                0 -> Modifier
                    .focusRequester(runtime.firstItemFocusRequester)
                    .onFocusChanged { state -> runtime.onFirstItemFocusChanged(state.isFocused) }
                    .handleKeyEvents(
                        onLeft = {
                            if (runtime.direction == LazyListDirection.Horizontal)
                                runtime.scrollToLast()
                        },
                        onUp = {
                            if (runtime.direction == LazyListDirection.Vertical)
                                runtime.scrollToLast()
                        },
                    )
                items.size - 1 -> Modifier
                    .focusRequester(runtime.lastItemFocusRequester)
                    .handleKeyEvents(
                        onRight = {
                            if (runtime.direction == LazyListDirection.Horizontal)
                                runtime.scrollToFirst()
                        },
                        onDown = {
                            if (runtime.direction == LazyListDirection.Vertical)
                                runtime.scrollToFirst()
                        },
                    )
                else -> Modifier
            }
        )
    itemContent(itemModifier, index, items[index])
}