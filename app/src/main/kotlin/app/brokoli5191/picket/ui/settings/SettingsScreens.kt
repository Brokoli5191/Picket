package app.brokoli5191.picket.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.brokoli5191.picket.R
import app.brokoli5191.picket.data.settings.AppSettings
import app.brokoli5191.picket.data.settings.AppearanceSettings
import app.brokoli5191.picket.data.settings.ContrastPref
import app.brokoli5191.picket.data.settings.MotionPref
import app.brokoli5191.picket.data.settings.SchemeStyle
import app.brokoli5191.picket.data.settings.Seeds
import app.brokoli5191.picket.data.settings.ThemeMode
import app.brokoli5191.picket.ui.components.SectionHeader
import app.brokoli5191.picket.ui.components.segmentedColors
import app.brokoli5191.picket.ui.expressive.ChoiceRow
import app.brokoli5191.picket.ui.theme.rememberHaptics

/** One entry of the settings list. */
data class SettingsLink(
    val title: Int,
    val summary: Int,
    val icon: Int,
    val onClick: () -> Unit,
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    versionName: String,
    links: List<SettingsLink>,
    onSampling: (Long) -> Unit,
    onShowSystem: (Boolean) -> Unit,
    onDebugOverlay: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = rememberHaptics()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                subtitle = { Text(stringResource(R.string.settings_version, versionName)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader(stringResource(R.string.settings_section_general)) }
            links.forEachIndexed { index, link ->
                item {
                    SegmentedListItem(
                        onClick = link.onClick,
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = links.size),
                        colors = segmentedColors(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
                        leadingContent = { Icon(painterResource(link.icon), contentDescription = null) },
                        supportingContent = { Text(stringResource(link.summary)) },
                    ) { Text(stringResource(link.title)) }
                }
            }
            item { SectionHeader(stringResource(R.string.settings_section_sampling)) }
            item {
                ChoiceRow(
                    options = AppSettings.SAMPLING_CHOICES_MS,
                    selected = settings.samplingIntervalMs,
                    label = { stringResource(R.string.settings_seconds, it / 1000) },
                    onSelect = { ms ->
                        haptics.tick()
                        onSampling(ms)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            item {
                SwitchRow(
                    R.string.settings_show_system,
                    R.string.settings_show_system_summary,
                    settings.showSystemApps,
                ) {
                    haptics.toggle(it)
                    onShowSystem(it)
                }
            }
            item {
                SwitchRow(
                    R.string.settings_debug_overlay,
                    R.string.settings_debug_overlay_summary,
                    settings.debugOverlay,
                ) {
                    haptics.toggle(it)
                    onDebugOverlay(it)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: Int,
    summary: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors = segmentedColors(),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        supportingContent = { Text(stringResource(summary)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) { Text(stringResource(title)) }
}

/** Appearance with a live preview at the top; every change applies immediately. */
@Composable
fun AppearanceScreen(
    appearance: AppearanceSettings,
    dynamicAvailable: Boolean,
    onChange: ((AppearanceSettings) -> AppearanceSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_appearance)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { PreviewCard() }

            item { SectionHeader(stringResource(R.string.appearance_theme)) }
            item {
                Choice(
                    options = ThemeMode.entries,
                    selected = appearance.themeMode,
                    label = {
                        when (it) {
                            ThemeMode.SYSTEM -> R.string.appearance_theme_system
                            ThemeMode.LIGHT -> R.string.appearance_theme_light
                            ThemeMode.DARK -> R.string.appearance_theme_dark
                        }
                    },
                ) { mode ->
                    haptics.tick()
                    onChange { it.copy(themeMode = mode) }
                }
            }
            item {
                SwitchRow(
                    R.string.appearance_pure_black,
                    R.string.appearance_pure_black_summary,
                    appearance.pureBlack,
                ) { on ->
                    haptics.toggle(on)
                    onChange { it.copy(pureBlack = on) }
                }
            }

            item { SectionHeader(stringResource(R.string.appearance_colour)) }
            if (dynamicAvailable) {
                item {
                    SwitchRow(
                        R.string.appearance_dynamic,
                        R.string.appearance_dynamic_summary,
                        appearance.dynamicColor,
                    ) { on ->
                        haptics.toggle(on)
                        onChange { it.copy(dynamicColor = on) }
                    }
                }
            }
            if (!appearance.dynamicColor || !dynamicAvailable) {
                item {
                    SeedPicker(appearance.seed) { index ->
                        haptics.tick()
                        onChange { it.copy(seed = index) }
                    }
                }
                item {
                    Choice(
                        options = SchemeStyle.entries,
                        selected = appearance.style,
                        label = {
                            when (it) {
                                SchemeStyle.TONAL_SPOT -> R.string.appearance_style_tonal
                                SchemeStyle.VIBRANT -> R.string.appearance_style_vibrant
                                SchemeStyle.EXPRESSIVE -> R.string.appearance_style_expressive
                                SchemeStyle.FIDELITY -> R.string.appearance_style_fidelity
                            }
                        },
                    ) { style ->
                        haptics.tick()
                        onChange { it.copy(style = style) }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.appearance_contrast)) }
            item {
                Choice(
                    options = ContrastPref.entries,
                    selected = appearance.contrast,
                    label = {
                        when (it) {
                            ContrastPref.SYSTEM -> R.string.appearance_follow_system
                            ContrastPref.STANDARD -> R.string.appearance_contrast_standard
                            ContrastPref.MEDIUM -> R.string.appearance_contrast_medium
                            ContrastPref.HIGH -> R.string.appearance_contrast_high
                        }
                    },
                ) { contrast ->
                    haptics.tick()
                    onChange { it.copy(contrast = contrast) }
                }
            }

            item { SectionHeader(stringResource(R.string.appearance_motion)) }
            item {
                Choice(
                    options = MotionPref.entries,
                    selected = appearance.motion,
                    label = {
                        when (it) {
                            MotionPref.SYSTEM -> R.string.appearance_follow_system
                            MotionPref.FULL -> R.string.appearance_motion_full
                            MotionPref.REDUCED -> R.string.appearance_motion_reduced
                            MotionPref.OFF -> R.string.appearance_motion_off
                        }
                    },
                ) { motion ->
                    haptics.tick()
                    onChange { it.copy(motion = motion) }
                }
            }
            item {
                SwitchRow(R.string.appearance_haptics, R.string.appearance_haptics_summary, appearance.haptics) { on ->
                    onChange { it.copy(haptics = on) }
                }
            }
        }
    }
}

@Composable
private fun <T> Choice(
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    ChoiceRow(
        options = options,
        selected = selected,
        label = { stringResource(label(it)) },
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SeedPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Seeds.all.forEachIndexed { index, seed ->
            val ring = if (index == selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
            Box(
                Modifier
                    .size(44.dp)
                    .border(3.dp, ring, CircleShape)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(Color(seed.argb))
                    .clickable { onSelect(index) }
                    .semantics { contentDescription = seed.name },
            )
        }
    }
}

/** Shows the current scheme on real components, so every change is visible at once. */
@Composable
private fun PreviewCard() {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.appearance_preview), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {}) { Text(stringResource(R.string.appearance_preview_primary)) }
                OutlinedButton(onClick = {}) { Text(stringResource(R.string.appearance_preview_secondary)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.error,
                ).forEach { Box(Modifier.size(28.dp).clip(CircleShape).background(it)) }
            }
            Text(
                stringResource(R.string.appearance_preview_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
