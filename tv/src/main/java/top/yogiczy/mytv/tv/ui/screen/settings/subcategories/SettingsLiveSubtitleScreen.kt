package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsSelectionScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SelectionOption
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM

@Composable
fun SettingsLiveSubtitleScreen(
    settingsViewModel: SettingsViewModel = settingsVM,
    onBackPressed: () -> Unit = {},
) {
    var showAsrProviderSelector by remember { mutableStateOf(false) }
    var showTranslateProviderSelector by remember { mutableStateOf(false) }
    var showTargetLangSelector by remember { mutableStateOf(false) }
    var showWhisperModelSelector by remember { mutableStateOf(false) }
    var showAsrApiKeyDialog by remember { mutableStateOf(false) }
    var showAsrRegionDialog by remember { mutableStateOf(false) }
    var showTranslateApiKeyDialog by remember { mutableStateOf(false) }
    var showTranslateRegionDialog by remember { mutableStateOf(false) }

    val asrProvider = settingsViewModel.subtitleLiveAsrProvider
    val translateProvider = settingsViewModel.subtitleLiveTranslateProvider

    // 识别引擎选择器
    if (showAsrProviderSelector) {
        SettingsSelectionScreen(
            title = "语音识别引擎",
            options = listOf(
                SelectionOption("vosk", "Vosk（离线）", "低延迟，需单独下载模型"),
                SelectionOption("whisper", "Whisper.cpp（离线）", "高准确度，多语种支持"),
                SelectionOption("azure", "Azure Speech（云端）", "免费5小时/月"),
                SelectionOption("baidu", "百度语音（云端）", "免费5万次/天"),
                SelectionOption("google", "Google Speech（云端）", "免费60分钟/月"),
            ),
            selectedProvider = { asrProvider },
            onSelected = { value ->
                settingsViewModel.subtitleLiveAsrProvider = value
                showAsrProviderSelector = false
            },
            onBackPressed = { showAsrProviderSelector = false },
        )
    }

    // 翻译引擎选择器
    if (showTranslateProviderSelector) {
        SettingsSelectionScreen(
            title = "翻译引擎",
            options = listOf(
                SelectionOption("mlkit", "ML Kit（离线）", "免费无限制，首次需下载模型"),
                SelectionOption("google", "Google Cloud Translation（云端）", "免费50万字符/月"),
                SelectionOption("azure", "Azure Translator（云端）", "免费200万字符/月"),
                SelectionOption("baidu", "百度翻译（云端）", "免费200万字符/月"),
                SelectionOption("deepl", "DeepL（云端）", "免费50万字符/月，欧语准确度高"),
            ),
            selectedProvider = { translateProvider },
            onSelected = { value ->
                settingsViewModel.subtitleLiveTranslateProvider = value
                showTranslateProviderSelector = false
            },
            onBackPressed = { showTranslateProviderSelector = false },
        )
    }

    // 翻译目标语言选择器
    if (showTargetLangSelector) {
        SettingsSelectionScreen(
            title = "翻译目标语言",
            options = listOf(
                SelectionOption("zh", "中文（简体）"),
                SelectionOption("en", "English"),
                SelectionOption("ja", "日本語"),
                SelectionOption("ko", "한국어"),
                SelectionOption("fr", "Français"),
                SelectionOption("de", "Deutsch"),
                SelectionOption("es", "Español"),
                SelectionOption("pt", "Português"),
                SelectionOption("ru", "Русский"),
                SelectionOption("ar", "العربية"),
                SelectionOption("hi", "हिन्दी"),
                SelectionOption("th", "ไทย"),
                SelectionOption("vi", "Tiếng Việt"),
                SelectionOption("tr", "Türkçe"),
            ),
            selectedProvider = { settingsViewModel.subtitleLiveTranslateTarget },
            onSelected = { value ->
                settingsViewModel.subtitleLiveTranslateTarget = value
                showTargetLangSelector = false
            },
            onBackPressed = { showTargetLangSelector = false },
        )
    }

    // Whisper 模型选择器
    if (showWhisperModelSelector) {
        SettingsSelectionScreen(
            title = "Whisper 模型",
            options = listOf(
                SelectionOption("tiny", "tiny（75MB，低延迟）"),
                SelectionOption("base", "base（142MB，中等）"),
            ),
            selectedProvider = { settingsViewModel.subtitleLiveWhisperModel },
            onSelected = { value ->
                settingsViewModel.subtitleLiveWhisperModel = value
                showWhisperModelSelector = false
            },
            onBackPressed = { showWhisperModelSelector = false },
        )
    }

    // API Key 输入对话框
    if (showAsrApiKeyDialog) {
        ApiKeyInputDialog(
            title = "识别 API Key",
            value = settingsViewModel.subtitleLiveAsrApiKey,
            onConfirm = {
                settingsViewModel.subtitleLiveAsrApiKey = it
                showAsrApiKeyDialog = false
            },
            onDismiss = { showAsrApiKeyDialog = false },
        )
    }

    if (showAsrRegionDialog) {
        ApiKeyInputDialog(
            title = "识别区域（Azure）",
            value = settingsViewModel.subtitleLiveAsrRegion,
            onConfirm = {
                settingsViewModel.subtitleLiveAsrRegion = it
                showAsrRegionDialog = false
            },
            onDismiss = { showAsrRegionDialog = false },
        )
    }

    if (showTranslateApiKeyDialog) {
        ApiKeyInputDialog(
            title = "翻译 API Key",
            value = settingsViewModel.subtitleLiveTranslateApiKey,
            onConfirm = {
                settingsViewModel.subtitleLiveTranslateApiKey = it
                showTranslateApiKeyDialog = false
            },
            onDismiss = { showTranslateApiKeyDialog = false },
        )
    }

    if (showTranslateRegionDialog) {
        ApiKeyInputDialog(
            title = "翻译区域（Azure）",
            value = settingsViewModel.subtitleLiveTranslateRegion,
            onConfirm = {
                settingsViewModel.subtitleLiveTranslateRegion = it
                showTranslateRegionDialog = false
            },
            onDismiss = { showTranslateRegionDialog = false },
        )
    }

    // 主列表
    SettingsCategoryScreen(
        title = "实时字幕设置",
        onBackPressed = onBackPressed,
    ) {
        // 语音识别部分
        item {
            Text(
                "语音识别",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
        }

        item {
            SettingsListItem(
                headlineContent = "语音识别引擎",
                supportingContent = asrProviderLabel(asrProvider),
                onSelect = { showAsrProviderSelector = true },
                link = true,
            )
        }

        if (asrProvider in listOf("azure", "baidu", "google")) {
            item {
                SettingsListItem(
                    headlineContent = "识别 API Key",
                    supportingContent = apiKeyDisplay(settingsViewModel.subtitleLiveAsrApiKey),
                    onSelect = { showAsrApiKeyDialog = true },
                    link = true,
                )
            }

            if (asrProvider == "azure") {
                item {
                    SettingsListItem(
                        headlineContent = "识别区域",
                        supportingContent = settingsViewModel.subtitleLiveAsrRegion.ifBlank { "未设置" },
                        onSelect = { showAsrRegionDialog = true },
                        link = true,
                    )
                }
            }

            if (settingsViewModel.subtitleLiveAsrApiKey.isBlank()) {
                item {
                    SettingsListItem(
                        headlineContent = "未填写 Key，将自动回退 Vosk 离线引擎",
                        supportingContent = "",
                        onSelect = {},
                    )
                }
            }
        }

        if (asrProvider == "whisper") {
            item {
                SettingsListItem(
                    headlineContent = "Whisper 模型",
                    supportingContent = settingsViewModel.subtitleLiveWhisperModel,
                    onSelect = { showWhisperModelSelector = true },
                    link = true,
                )
            }
        }

        // 翻译部分
        item {
            Text(
                "翻译",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
        }

        item {
            SettingsListItem(
                headlineContent = "翻译引擎",
                supportingContent = translateProviderLabel(translateProvider),
                onSelect = { showTranslateProviderSelector = true },
                link = true,
            )
        }

        if (translateProvider in listOf("google", "azure", "baidu", "deepl")) {
            item {
                SettingsListItem(
                    headlineContent = "翻译 API Key",
                    supportingContent = apiKeyDisplay(settingsViewModel.subtitleLiveTranslateApiKey),
                    onSelect = { showTranslateApiKeyDialog = true },
                    link = true,
                )
            }

            if (translateProvider == "azure") {
                item {
                    SettingsListItem(
                        headlineContent = "翻译区域",
                        supportingContent = settingsViewModel.subtitleLiveTranslateRegion.ifBlank { "未设置" },
                        onSelect = { showTranslateRegionDialog = true },
                        link = true,
                    )
                }
            }

            if (settingsViewModel.subtitleLiveTranslateApiKey.isBlank()) {
                item {
                    SettingsListItem(
                        headlineContent = "未填写 Key，将自动回退 ML Kit 离线翻译",
                        supportingContent = "",
                        onSelect = {},
                    )
                }
            }
        }

        // 目标语言
        item {
            Text(
                "语言",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
        }

        item {
            SettingsListItem(
                headlineContent = "翻译目标语言",
                supportingContent = targetLangLabel(settingsViewModel.subtitleLiveTranslateTarget),
                onSelect = { showTargetLangSelector = true },
                link = true,
            )
        }
    }
}

private fun asrProviderLabel(provider: String): String = when (provider) {
    "vosk" -> "Vosk（离线）"
    "whisper" -> "Whisper.cpp（离线）"
    "azure" -> "Azure Speech（云端）"
    "baidu" -> "百度语音（云端）"
    "google" -> "Google Speech（云端）"
    else -> provider
}

private fun translateProviderLabel(provider: String): String = when (provider) {
    "mlkit" -> "ML Kit（离线）"
    "google" -> "Google Cloud Translation（云端）"
    "azure" -> "Azure Translator（云端）"
    "baidu" -> "百度翻译（云端）"
    "deepl" -> "DeepL（云端）"
    else -> provider
}

private fun targetLangLabel(code: String): String = when (code) {
    "zh" -> "中文（简体）"
    "en" -> "English"
    "ja" -> "日本語"
    "ko" -> "한국어"
    "fr" -> "Français"
    "de" -> "Deutsch"
    "es" -> "Español"
    "pt" -> "Português"
    "ru" -> "Русский"
    "ar" -> "العربية"
    "hi" -> "हिन्दी"
    "th" -> "ไทย"
    "vi" -> "Tiếng Việt"
    "tr" -> "Türkçe"
    else -> code
}

private fun apiKeyDisplay(key: String): String =
    if (key.isBlank()) "未设置"
    else if (key.length <= 4) "****"
    else "${key.take(4)}****"

@Composable
private fun ApiKeyInputDialog(
    title: String,
    value: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf(value) }
    val isModified = input != value

    SettingsCategoryScreen(
        title = title,
        onBackPressed = {
            if (isModified) {
                onConfirm(input)
            } else {
                onDismiss()
            }
        },
    ) {
        item {
            SettingsListItem(
                headlineContent = "输入 $title",
                supportingContent = input.ifBlank { "请通过推送输入或按返回键清空" },
            )
        }

        item {
            SettingsListItem(
                headlineContent = if (input.isBlank()) "清空" else "确认",
                supportingContent = if (input.isBlank()) "当前未设置" else "当前值: ${apiKeyDisplay(input)}",
                onSelect = {
                    onConfirm(input)
                },
            )
        }
    }
}