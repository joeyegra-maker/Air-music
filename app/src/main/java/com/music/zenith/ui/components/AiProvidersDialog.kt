package com.music.zenith.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.zenith.R
import com.music.zenith.data.lyrics.AiLyricsTranslator
import com.music.zenith.data.lyrics.AiProvider
import com.music.zenith.data.settings.AppSettings
import com.music.zenith.ui.icons.SfIcons
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.launch

/** How much of the screen the provider's rows may take before they scroll inside the card. */
private val PROVIDERS_MAX_HEIGHT = 360.dp

private fun providerKeyUrl(provider: AiProvider): String = when (provider) {
    AiProvider.GEMINI -> "aistudio.google.com/apikey"
    AiProvider.GROQ -> "console.groq.com/keys"
    AiProvider.OPENROUTER -> "openrouter.ai/keys"
}

/**
 * Every AI provider's settings in one frosted alert: a dropdown picks the
 * provider, and under it sit that provider's switch, API keys, model and key
 * test. Keys and model open the app's usual value and model alerts on top.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AiProvidersDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val config by AppSettings.aiTranslate.collectAsStateWithLifecycle()
    var selected by remember {
        mutableStateOf(
            config.providers.firstOrNull { it.usable }?.provider
                ?: config.providers.firstOrNull()?.provider
                ?: AiProvider.GEMINI,
        )
    }
    val cfg = config.of(selected)
    var pickerOpen by remember { mutableStateOf(false) }
    var keysOpen by remember { mutableStateOf(false) }
    var modelOpen by remember { mutableStateOf(false) }
    var testing by remember(selected) { mutableStateOf(false) }
    var results by remember(selected) { mutableStateOf<List<AiLyricsTranslator.KeyTest>?>(null) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(ALERT_CORNER)
    val motion = rememberMacOSPopupState(onClosed = onDismiss)
    BackHandler(enabled = !keysOpen && !modelOpen) { motion.requestClose() }

    val keyResults = results
    val testDetail = when {
        testing -> stringResource(R.string.ai_translate_testing)
        keyResults == null -> stringResource(R.string.ai_provider_test_subtitle)
        keyResults.isEmpty() -> stringResource(R.string.ai_provider_keys_none)
        else -> keyResults.joinToString("\n") {
            "${if (it.ok) "✓" else "✗"} #${it.keyNumber}: ${it.detail}"
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .macOSPopupScrim(motion, SCRIM_COLOR)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = motion::requestClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(ALERT_WIDTH)
                    .macOSPopupMotion(motion)
                    .clip(shape)
                    .alertSurface(hazeState, frosted = true, shape = shape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    )
                    .macOSPopupContent(motion),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 19.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.ai_translate_providers),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.W600,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.ai_translate_providers_subtitle),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }

                AlertRule()
                Column(
                    modifier = Modifier
                        .heightIn(max = PROVIDERS_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // The dropdown: the chosen provider, opening into the three.
                    Column(modifier = Modifier.animateContentSize()) {
                        ProviderRow(
                            label = stringResource(R.string.ai_provider_pick),
                            detail = selected.label,
                            onClick = { pickerOpen = !pickerOpen },
                            trailing = {
                                Icon(
                                    imageVector = SfIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .rotate(if (pickerOpen) 90f else 0f),
                                )
                            },
                        )
                        if (pickerOpen) {
                            AiProvider.entries.forEach { provider ->
                                val providerCfg = config.of(provider)
                                AlertRule()
                                ProviderRow(
                                    label = provider.label,
                                    detail = if (providerCfg.keys.isEmpty()) {
                                        stringResource(R.string.ai_provider_keys_none)
                                    } else {
                                        stringResource(R.string.ai_provider_keys_count, providerCfg.keys.size)
                                    },
                                    onClick = {
                                        selected = provider
                                        pickerOpen = false
                                    },
                                    trailing = if (provider == selected) {
                                        {
                                            Icon(
                                                imageVector = SfIcons.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(19.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }

                    AlertRule()
                    ProviderRow(
                        label = stringResource(R.string.enabled),
                        detail = null,
                        onClick = { AppSettings.setAiProviderEnabled(selected, !cfg.enabled) },
                        trailing = {
                            IosSwitch(
                                checked = cfg.enabled,
                                onCheckedChange = { AppSettings.setAiProviderEnabled(selected, it) },
                            )
                        },
                    )
                    AlertRule()
                    ProviderRow(
                        label = stringResource(R.string.ai_provider_keys),
                        detail = if (cfg.keys.isEmpty()) {
                            stringResource(R.string.ai_provider_keys_none)
                        } else {
                            stringResource(R.string.ai_provider_keys_count, cfg.keys.size)
                        },
                        onClick = { keysOpen = true },
                    )
                    AlertRule()
                    ProviderRow(
                        label = stringResource(R.string.ai_provider_model),
                        detail = if (cfg.model.isBlank()) {
                            stringResource(R.string.ai_provider_model_auto)
                        } else {
                            cfg.model
                        },
                        onClick = { modelOpen = true },
                    )
                    AlertRule()
                    ProviderRow(
                        label = stringResource(R.string.ai_provider_test),
                        detail = testDetail,
                        detailLines = 6,
                        onClick = {
                            if (testing) return@ProviderRow
                            if (cfg.keys.isEmpty()) {
                                results = emptyList()
                            } else {
                                scope.launch {
                                    testing = true
                                    try {
                                        results = AiLyricsTranslator.testKeys(cfg)
                                    } finally {
                                        testing = false
                                    }
                                }
                            }
                        },
                    )
                    AlertRule()
                    Text(
                        text = stringResource(R.string.ai_provider_get_key, providerKeyUrl(selected)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }

                AlertRule()
                AlertAction(
                    label = stringResource(R.string.done),
                    emphasised = true,
                    onClick = motion::requestClose,
                )
            }
        }

        if (keysOpen) {
            BackHandler { keysOpen = false }
            var input by remember { mutableStateOf(cfg.keys.joinToString(", ")) }
            TextValueAlert(
                hazeState = hazeState,
                title = stringResource(R.string.ai_provider_keys_title, selected.label),
                message = stringResource(R.string.ai_provider_keys_message),
                placeholder = "key1, key2",
                value = input,
                onValueChange = { input = it },
                onSave = {
                    AppSettings.setAiProviderKeys(selected, input)
                    results = null
                    keysOpen = false
                },
                onDismiss = { keysOpen = false },
                onRemove = if (cfg.keys.isNotEmpty()) {
                    {
                        AppSettings.setAiProviderKeys(selected, "")
                        results = null
                        keysOpen = false
                    }
                } else {
                    null
                },
            )
        }

        if (modelOpen) {
            BackHandler { modelOpen = false }
            AiModelDialog(
                hazeState = hazeState,
                provider = selected,
                onDismiss = { modelOpen = false },
            )
        }
    }
}

/** Label over detail with an optional trailing control — a source row from the lyrics sources alert. */
@Composable
private fun ProviderRow(
    label: String,
    detail: String?,
    onClick: () -> Unit,
    detailLines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ACTION_HEIGHT)
            .background(
                if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f) else Color.Transparent,
            )
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = detailLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}