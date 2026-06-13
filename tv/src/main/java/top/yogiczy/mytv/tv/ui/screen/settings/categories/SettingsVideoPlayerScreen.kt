package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.util.utils.headersValid
import top.yogiczy.mytv.core.util.utils.humanizeMs
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme

@Composable
fun SettingsVideoPlayerScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    toVideoPlayerCoreScreen: () -> Unit = {},
    toVideoPlayerRenderModeScreen: () -> Unit = {},
    toVideoPlayerDisplayModeScreen: () -> Unit = {},
    toVideoPlayerLoadTimeoutScreen: () -> Unit = {},
    toLiveSubtitleScreen: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 播放器") },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                headlineContent = "内核",
                trailingContent = settingsViewModel.videoPlayerCore.label,
                onSelect = toVideoPlayerCoreScreen,
                link = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "渲染方式",
                trailingContent = settingsViewModel.videoPlayerRenderMode.label,
                onSelect = toVideoPlayerRenderModeScreen,
                link = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "强制音频软解",
                trailingContent = {
                    Switch(settingsViewModel.videoPlayerForceAudioSoftDecode, null)
                },
                onSelect = {
                    settingsViewModel.videoPlayerForceAudioSoftDecode =
                        !settingsViewModel.videoPlayerForceAudioSoftDecode
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "停止上一媒体项",
                trailingContent = {
                    Switch(settingsViewModel.videoPlayerStopPreviousMediaItem, null)
                },
                onSelect = {
                    settingsViewModel.videoPlayerStopPreviousMediaItem =
                        !settingsViewModel.videoPlayerStopPreviousMediaItem
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "跳过多帧渲染",
                trailingContent = {
                    Switch(settingsViewModel.videoPlayerSkipMultipleFramesOnSameVSync, null)
                },
                onSelect = {
                    settingsViewModel.videoPlayerSkipMultipleFramesOnSameVSync =
                        !settingsViewModel.videoPlayerSkipMultipleFramesOnSameVSync
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "音量淡入淡出",
                supportingContent = "调节音量时平滑过渡",
                trailingContent = {
                    Switch(settingsViewModel.videoPlayerEnableVolumeFade, null)
                },
                onSelect = {
                    settingsViewModel.videoPlayerEnableVolumeFade =
                        !settingsViewModel.videoPlayerEnableVolumeFade
                },
            )
        }

        // 实时字幕
        item {
            SettingsListItem(
                headlineContent = "实时字幕",
                supportingContent = "语音识别外语并实时翻译为字幕",
                trailingContent = {
                    Switch(settingsViewModel.subtitleLiveEnable, null)
                },
                onSelect = {
                    settingsViewModel.subtitleLiveEnable =
                        !settingsViewModel.subtitleLiveEnable
                },
            )
        }

        if (settingsViewModel.subtitleLiveEnable) {
            item {
                SettingsListItem(
                    headlineContent = "实时字幕设置",
                    supportingContent = "识别: ${asrShortName(settingsViewModel.subtitleLiveAsrProvider)} → 翻译: ${translateShortName(settingsViewModel.subtitleLiveTranslateProvider)} → ${langShortName(settingsViewModel.subtitleLiveTranslateTarget)}",
                    onSelect = toLiveSubtitleScreen,
                    link = true,
                )
            }
        }

        item {
            SettingsListItem(
                headlineContent = "全局显示模式",
                trailingContent = settingsViewModel.videoPlayerDisplayMode.label,
                onSelect = toVideoPlayerDisplayModeScreen,
                link = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "加载超时",
                supportingContent = "影响超时换源、断线重连",
                trailingContent = settingsViewModel.videoPlayerLoadTimeout.humanizeMs(),
                onSelect = toVideoPlayerLoadTimeoutScreen,
                link = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "自定义ua",
                trailingContent = settingsViewModel.videoPlayerUserAgent,
                remoteConfig = true,
            )
        }

        item {
            val isValid = settingsViewModel.videoPlayerHeaders.headersValid()

            SettingsListItem(
                headlineContent = "自定义headers",
                supportingContent = settingsViewModel.videoPlayerHeaders,
                remoteConfig = true,
                trailingIcon = if (!isValid) Icons.Default.ErrorOutline else null,
            )
        }
    }
}

private fun asrShortName(provider: String): String = when (provider) {
    "vosk" -> "Vosk"
    "whisper" -> "Whisper"
    "azure" -> "Azure"
    "baidu" -> "百度"
    "google" -> "Google"
    else -> provider
}

private fun translateShortName(provider: String): String = when (provider) {
    "mlkit" -> "ML Kit"
    "google" -> "Google"
    "azure" -> "Azure"
    "baidu" -> "百度"
    "deepl" -> "DeepL"
    else -> provider
}

private fun langShortName(code: String): String = when (code) {
    "zh" -> "中文"
    "en" -> "EN"
    "ja" -> "日本語"
    "ko" -> "한국어"
    else -> code
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsVideoPlayerScreenPreview() {
    MyTvTheme {
        SettingsVideoPlayerScreen(
            settingsViewModel = SettingsViewModel().apply {
                videoPlayerUserAgent =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
                videoPlayerHeaders = "Accept: "
            }
        )
    }
}