package com.ffmathy.xaternity.phone.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ffmathy.xaternity.core.format.Labels
import com.ffmathy.xaternity.core.format.rememberFormatters
import com.ffmathy.xaternity.core.settings.AppLanguage
import com.ffmathy.xaternity.core.settings.Appearance
import com.ffmathy.xaternity.core.sync.SyncStatus
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.core.ui.type.PhoneType
import com.ffmathy.xaternity.phone.R
import com.ffmathy.xaternity.phone.ui.components.ConnectedChoiceGroup
import com.ffmathy.xaternity.phone.ui.components.XTopBar
import com.ffmathy.xaternity.phone.ui.components.syncIcon
import com.ffmathy.xaternity.phone.ui.components.syncLineText
import com.ffmathy.xaternity.phone.ui.edit.OutlinePill
import com.ffmathy.xaternity.phone.ui.history.HistoryRowDefaults
import com.ffmathy.xaternity.phone.ui.preview.SettingsSamples
import com.ffmathy.xaternity.phone.ui.theme.NightLightTheme
import com.ffmathy.xaternity.core.R as CoreR

/** Settings wired to its view model and navigation. */
@Composable
fun SettingsRoute(onBack: () -> Unit, onOpenLicences: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Only while this screen is resumed: a second tap during a transition must not pop Home or stack Licences twice.
    val back by rememberUpdatedState(dropUnlessResumed(block = onBack))
    val licences by rememberUpdatedState(dropUnlessResumed(block = onOpenLicences))
    val actions = remember(viewModel) {
        object : SettingsActions by viewModel {
            override fun onBack() = back()
            override fun onOpenLicences() = licences()
        }
    }
    val loaded = state
    if (loaded != null) {
        SettingsScreen(loaded, actions)
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    }
}

/**
 * Settings (DESIGN_SPEC §3.3.5; not drawn in the mockups, so it follows their language): the baby's name, Appearance
 * (Auto · Night · Day), Language (System default · English · Dansk), watch sync with a retry, and About (version, font
 * licences). Section headings are labelMedium; grouped rows use the History list's r 20 / r 6 corners.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(windowInsets),
    ) {
        XTopBar(
            title = stringResource(CoreR.string.nav_settings),
            navigationIcon = XIcons.back,
            navigationLabel = stringResource(R.string.cd_back),
            onNavigate = actions::onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Section(stringResource(R.string.settings_section_baby)) { NameField(state.babyName, actions) }
            Section(stringResource(CoreR.string.settings_appearance)) {
                ConnectedChoiceGroup(
                    options = Appearance.entries,
                    selected = state.appearance,
                    onSelect = actions::onAppearance,
                    label = { stringResource(Labels.appearance(it)) },
                )
                Text(
                    stringResource(R.string.settings_appearance_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                )
            }
            Section(stringResource(CoreR.string.settings_language)) { LanguageGroup(state.language, actions::onLanguage) }
            Section(stringResource(R.string.settings_section_watch)) { SyncRow(state, actions) }
            Section(stringResource(R.string.settings_section_about)) { AboutGroup(state.versionName, actions::onOpenLicences) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
    Column(content = content)
}

/** The baby's name (shown in the top bar and notifications); empty shows the "Baby" hint. Done saves at once. */
@Composable
private fun NameField(name: String, actions: SettingsActions) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = name,
        onValueChange = actions::onBabyNameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(CoreR.string.settings_baby_name)) },
        placeholder = { Text(stringResource(CoreR.string.baby_name_hint)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            actions.onBabyNameDone()
            focus.clearFocus()
        }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.onSurface,
            unfocusedBorderColor = colors.outline,
            focusedLabelColor = colors.onSurface,
            unfocusedLabelColor = colors.onSurfaceVariant,
            cursorColor = colors.onSurface,
        ),
    )
}

/** System default · English · Dansk as radio rows (language names in their own language). */
@Composable
private fun LanguageGroup(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    val options = AppLanguage.entries
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(HistoryRowDefaults.Gap)) {
        options.forEachIndexed { index, language ->
            GroupRow(first = index == 0, last = index == options.lastIndex) {
                val isSelected = language == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(language) })
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Labels.language(language)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.onSurface,
                            unselectedColor = MaterialTheme.colorScheme.outline,
                        ),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

/** The sync line with "Sync now"; without a watch: "No watch connected. Everything is saved on this phone." */
@Composable
private fun SyncRow(state: SettingsUiState, actions: SettingsActions) {
    val colors = MaterialTheme.colorScheme
    val formatters = rememberFormatters(state.zone)
    val line = syncLineText(state.sync, state.zone, formatters)
    GroupRow(first = true, last = true) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
            if (line == null) {
                Text(
                    stringResource(R.string.settings_sync_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                // The line first (Danish "Synkroniseret med ur · 14:31" needs the full width), "Sync now" under it.
                val tint = if (state.sync is SyncStatus.Error) colors.error else colors.onSurfaceVariant
                Row(verticalAlignment = Alignment.CenterVertically) {
                    XIcon(syncIcon(state.sync), contentDescription = null, tint = tint, size = 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(line, style = PhoneType.dataMedium, color = tint)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinePill(stringResource(R.string.settings_sync_now), onClick = actions::onRetrySync)
                }
            }
        }
    }
}

/** "Xaternity · Version 0.1.0" and "Font licences" (opens the OFL texts). */
@Composable
private fun AboutGroup(versionName: String, onOpenLicences: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(HistoryRowDefaults.Gap)) {
        GroupRow(first = true, last = false) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(stringResource(R.string.settings_version, versionName), style = PhoneType.dataMedium, color = colors.onSurfaceVariant)
            }
        }
        GroupRow(first = false, last = true) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClick = onOpenLicences)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_font_licences), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text(stringResource(R.string.settings_font_licences_summary), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                XIcon(XIcons.chevronRight, contentDescription = null, tint = colors.outline, size = 16.dp)
            }
        }
    }
}

/** A row of a grouped list: surfaceContainer, outer corners r 20, inner r 6 (as the History list). */
@Composable
private fun GroupRow(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(HistoryRowDefaults.shape(first, last))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

@Preview(name = "Night", widthDp = 390, heightDp = 1000)
@Composable
private fun SettingsNightPreview() {
    NightLightTheme(night = true) { SettingsScreen(SettingsSamples.state(), SettingsActions.None) }
}

@Preview(name = "Day · Danish", widthDp = 390, heightDp = 1000, locale = "da")
@Composable
private fun SettingsDayPreview() {
    NightLightTheme(night = false) { SettingsScreen(SettingsSamples.state(), SettingsActions.None) }
}
