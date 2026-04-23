package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme

data class KeyInstruction(
    val key: String,
    val action: String,
    val longPressAction: String? = null,
)

@Composable
fun SettingsKeyInstructionsScreen(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
) {
    val remoteControlKeys = listOf(
        KeyInstruction("方向键 ↑/↓", "切换频道", "快速切换频道"),
        KeyInstruction("方向键 ←/→", "切换线路", "快速切换线路"),
        KeyInstruction("确认键 (OK)", "选择/确认", "收藏/取消收藏"),
        KeyInstruction("菜单键 (Menu)", "打开设置", null),
        KeyInstruction("数字键 0-9", "快速选台", null),
        KeyInstruction("返回键 (Back)", "返回上一级", null),
    )
    
    val touchGestures = listOf(
        KeyInstruction("向上滑动", "切换到下一个频道", null),
        KeyInstruction("向下滑动", "切换到上一个频道", null),
        KeyInstruction("向左滑动", "切换到上一个线路", null),
        KeyInstruction("向右滑动", "切换到下一个线路", null),
        KeyInstruction("单击", "选择/确认", null),
        KeyInstruction("长按", "收藏/取消收藏", null),
        KeyInstruction("双击", "打开设置", null),
    )
    
    AppScreen(
        modifier = modifier.padding(top = 10.dp),
        header = { Text("设置 / 按键说明") },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 38.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                KeyInstructionSection(
                    title = "遥控器按键",
                    instructions = remoteControlKeys,
                )
            }
            
            item {
                KeyInstructionSection(
                    title = "触摸手势",
                    instructions = touchGestures,
                )
            }
            
            item {
                Text(
                    text = "提示：长按功能会在焦点停留时显示提示，帮助您发现更多功能。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun KeyInstructionSection(
    title: String,
    instructions: List<KeyInstruction>,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                instructions.forEach { instruction ->
                    KeyInstructionRow(instruction = instruction)
                }
            }
        }
    }
}

@Composable
private fun KeyInstructionRow(
    instruction: KeyInstruction,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    MaterialTheme.shapes.small
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = instruction.key,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        
        Text(
            text = instruction.action,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        )
        
        instruction.longPressAction?.let { longAction ->
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "长按: $longAction",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsKeyInstructionsScreenPreview() {
    MyTvTheme {
        SettingsKeyInstructionsScreen()
    }
}
