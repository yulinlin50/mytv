package top.yogiczy.mytv.tv.ui.screen.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

data class SelectionOption<T>(
    val value: T,
    val label: String,
    val supportingContent: String? = null,
)

@Composable
fun <T> SettingsSelectionScreen(
    modifier: Modifier = Modifier,
    title: String = "",
    options: List<SelectionOption<T>>,
    selectedProvider: () -> T,
    onSelected: (T) -> Unit = {},
    columns: Int = 0,
    centered: Boolean = false,
    onBackPressed: () -> Unit = {},
    extraTrailingContent: (@Composable (T) -> Unit)? = null,
) {
    val selected = selectedProvider()
    val childPadding = rememberChildPadding()
    val firstItemFocusRequester = remember { FocusRequester() }

    AppScreen(
        modifier = modifier,
        header = { Text(title) },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        if (columns > 0) {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                contentPadding = childPadding.copy(top = 10.dp).paddingValues,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(options) { index, option ->
                    SelectionItem(
                        modifier = Modifier.then(
                            if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                        ),
                        option = option,
                        isSelected = option.value == selected,
                        centered = centered,
                        onSelected = { onSelected(option.value) },
                        extraTrailingContent = extraTrailingContent,
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                contentPadding = childPadding.copy(top = 10.dp).paddingValues,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(options) { index, option ->
                    SelectionItem(
                        modifier = Modifier.then(
                            if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                        ),
                        option = option,
                        isSelected = option.value == selected,
                        centered = centered,
                        onSelected = { onSelected(option.value) },
                        extraTrailingContent = extraTrailingContent,
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> SelectionItem(
    modifier: Modifier = Modifier,
    option: SelectionOption<T>,
    isSelected: Boolean,
    centered: Boolean,
    onSelected: () -> Unit,
    extraTrailingContent: (@Composable (T) -> Unit)? = null,
) {
    ListItem(
        modifier = modifier.handleKeyEvents(onSelect = onSelected),
        headlineContent = {
            if (centered) {
                Text(
                    option.label,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(option.label)
            }
        },
        supportingContent = option.supportingContent?.let { { Text(it) } },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                extraTrailingContent?.invoke(option.value)
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(0.1f),
        ),
        selected = false,
        onClick = {},
    )
}
