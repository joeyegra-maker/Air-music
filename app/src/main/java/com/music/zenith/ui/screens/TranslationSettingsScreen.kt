package com.music.zenith.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.zenith.R
import com.music.zenith.data.lyrics.AiLyricsTranslator
import com.music.zenith.data.lyrics.translationLanguageName
import com.music.zenith.data.settings.AppSettings
import com.music.zenith.ui.components.AiProviderOrderDialog
import com.music.zenith.ui.components.AiProvidersDialog
import com.music.zenith.ui.components.IosSwitch
import com.music.zenith.ui.icons.SfIcons
import dev.chrisbanes.haze.HazeState
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The popups the translation page opens. Rendered by the activity, above
 * the tab bar and mini player, like [DiscordDialog].
 */
sealed interface AiTranslateDialog {
    /** Drag-to-reorder list of the providers. */
    data object Order : AiTranslateDialog

    /** Every provider's switch, keys, model and test, behind one dropdown. */
    data object Providers : AiTranslateDialog
}

/**
 * Lyrics translation in one place: the target language, the AI switch, the
 * order the providers are tried in and their settings. Whatever is tried last
 * is the built-in translation, so a lyric always has a way to be translated.
 */
@Composable
fun AiTranslateScreen(
    onOpenDialog: (AiTranslateDialog) -> Unit,
    onOpenLanguage: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val config by AppSettings.aiTranslate.collectAsStateWithLifecycle()
    val translationLanguage by AppSettings.translationLanguage.collectAsStateWithLifecycle()
    val lastUsed by AiLyricsTranslator.lastUsed.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<List<AiLyricsTranslator.ProviderTest>?>(null) }

    val ready = config.providers.any { it.usable }
    val builtIn = stringResource(R.string.ai_translate_builtin)
    val orderText = config.providers.joinToString(" → ") { it.provider.label } + " → " + builtIn
    val languageText = if (translationLanguage.isBlank()) {
        stringResource(R.string.translation_language_subtitle)
    } else {
        translationLanguageName(
            translationLanguage,
            AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault(),
        )
    }
    val result = testResult
    val testSubtitle = when {
        testing -> stringResource(R.string.ai_translate_testing)
        result == null -> stringResource(R.string.ai_translate_test_subtitle)
        result.isEmpty() -> stringResource(R.string.ai_translate_test_none)
        else -> result.joinToString("\n") {
            "${if (it.ok) "✓" else "✗"} ${it.provider.label}: ${it.detail}"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        SettingsPageTitle(stringResource(R.string.translation_language))

        SettingsGroup {
            SettingsRow(
                icon = SfIcons.Translate,
                title = stringResource(R.string.translation_target),
                subtitle = languageText,
                trailing = { Chevron() },
                onClick = onOpenLanguage,
            )
        }

        SettingsGroup(footer = stringResource(R.string.ai_translate_flow_footer)) {
            SettingsRow(
                icon = SfIcons.AutoAwesome,
                title = stringResource(R.string.ai_translate_use),
                subtitle = stringResource(
                    if (config.enabled && !ready) R.string.ai_translate_need_key
                    else R.string.ai_translate_use_subtitle,
                ),
                trailing = {
                    IosSwitch(
                        checked = config.enabled,
                        onCheckedChange = AppSettings::setAiTranslateEnabled,
                    )
                },
                onClick = { AppSettings.setAiTranslateEnabled(!config.enabled) },
            )
            RowDivider()
            SettingsRow(
                icon = SfIcons.Cloud,
                title = stringResource(R.string.ai_translate_providers),
                subtitle = stringResource(R.string.ai_translate_providers_subtitle),
                trailing = { Chevron() },
                onClick = { onOpenDialog(AiTranslateDialog.Providers) },
            )
            RowDivider()
            SettingsRow(
                icon = SfIcons.Tune,
                title = stringResource(R.string.ai_translate_order),
                subtitle = orderText,
                trailing = { Chevron() },
                onClick = { onOpenDialog(AiTranslateDialog.Order) },
            )
            RowDivider()
            SettingsRow(
                icon = SfIcons.CloudDone,
                title = stringResource(R.string.ai_translate_test),
                subtitle = testSubtitle,
                enabled = !testing,
                onClick = {
                    scope.launch {
                        testing = true
                        try {
                            testResult = AiLyricsTranslator.testAll(config)
                        } finally {
                            testing = false
                        }
                    }
                },
            )
            RowDivider()
            SettingsRow(
                icon = SfIcons.History,
                title = stringResource(R.string.ai_translate_last_used),
                subtitle = lastUsed.ifEmpty { stringResource(R.string.ai_translate_last_used_none) },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AiTranslateDialogHost(
    which: AiTranslateDialog,
    hazeState: HazeState,
    onDismiss: () -> Unit,
) {
    when (which) {
        AiTranslateDialog.Order -> AiProviderOrderDialog(
            hazeState = hazeState,
            onDismiss = onDismiss,
        )

        AiTranslateDialog.Providers -> AiProvidersDialog(
            hazeState = hazeState,
            onDismiss = onDismiss,
        )
    }
}