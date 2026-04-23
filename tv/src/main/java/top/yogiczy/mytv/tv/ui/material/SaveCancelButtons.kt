package top.yogiczy.mytv.tv.ui.material

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun SaveCancelButtons(
    modifier: Modifier = Modifier,
    onSave: () -> Unit = {},
    onCancel: () -> Unit = {},
    saveFocusRequester: FocusRequester? = null,
    cancelFocusRequester: FocusRequester? = null,
) {
    val internalSaveFocusRequester = remember { FocusRequester() }
    val internalCancelFocusRequester = remember { FocusRequester() }
    
    val finalSaveFocusRequester = saveFocusRequester ?: internalSaveFocusRequester
    val finalCancelFocusRequester = cancelFocusRequester ?: internalCancelFocusRequester

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val saveInteractionSource = remember { MutableInteractionSource() }
        val saveIsFocused by saveInteractionSource.collectIsFocusedAsState()
        val saveContentColor = if (saveIsFocused) Color.Black else Color.White

        Surface(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .focusRequester(finalSaveFocusRequester)
                .handleKeyEvents(onSelect = onSave),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(0.1f),
                focusedContainerColor = Color.White,
            ),
            interactionSource = saveInteractionSource,
            onClick = onSave,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("保存", style = MaterialTheme.typography.titleMedium, color = saveContentColor)
            }
        }

        val cancelInteractionSource = remember { MutableInteractionSource() }
        val cancelIsFocused by cancelInteractionSource.collectIsFocusedAsState()
        val cancelContentColor = if (cancelIsFocused) Color.Black else Color.White

        Surface(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .focusRequester(finalCancelFocusRequester)
                .handleKeyEvents(onSelect = onCancel),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(0.1f),
                focusedContainerColor = Color.White,
            ),
            interactionSource = cancelInteractionSource,
            onClick = onCancel,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("取消", style = MaterialTheme.typography.titleMedium, color = cancelContentColor)
            }
        }
    }
}
