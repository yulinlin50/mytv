package top.yogiczy.mytv.tv.ui.material

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun LongPressIndicator(
    modifier: Modifier = Modifier,
    isFocused: Boolean,
    showHint: Boolean = true,
    hintText: String = "",
) {
    AnimatedVisibility(
        visible = isFocused && showHint,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = hintText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.surface,
            )
        }
    }
}
