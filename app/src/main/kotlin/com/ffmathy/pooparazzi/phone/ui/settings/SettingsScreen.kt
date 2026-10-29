package com.ffmathy.pooparazzi.phone.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.type.PhoneType
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.notify.NotificationLink
import com.ffmathy.pooparazzi.phone.privacy.openPrivacyPolicy
import com.ffmathy.pooparazzi.phone.ui.account.backupStatusText
import com.ffmathy.pooparazzi.phone.notify.openNotificationSettings
import com.ffmathy.pooparazzi.phone.ui.components.ConnectedChoiceGroup
import com.ffmathy.pooparazzi.phone.ui.components.GroupRow
import com.ffmathy.pooparazzi.phone.ui.components.RadioGroup
import com.ffmathy.pooparazzi.phone.ui.components.XTopBar
import com.ffmathy.pooparazzi.phone.ui.components.syncLineText
import com.ffmathy.pooparazzi.phone.ui.components.watchGlyph
import com.ffmathy.pooparazzi.phone.ui.edit.OutlinePill
import com.ffmathy.pooparazzi.phone.ui.history.HistoryRowDefaults
import com.ffmathy.pooparazzi.phone.ui.preview.SettingsSamples
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.ffmathy.pooparazzi.core.R as CoreR

/** Settings wired to its view model and navigation. */
@Composable
fun SettingsRoute(onBack: () -> Unit, onOpenLicences: () -> Unit, onOpenAccount: () -> Unit = {}) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Only while this screen is resumed: a second tap during a transition must not pop Home or stack Licences twice.
    val back by rememberUpdatedState(dropUnlessResumed(block = onBack))
    val licences by rememberUpdatedState(dropUnlessResumed(block = onOpenLicences))
    val account by rememberUpdatedState(dropUnlessResumed(block = onOpenAccount))
    val context = LocalContext.current
    // The permission and the system switch can change in system settings: re-read them whenever Settings resumes.
    var notifications by remember { mutableStateOf(NotificationLink.current(context)) }
    LifecycleResumeEffect(context) {
        notifications = NotificationLink.current(context)
        onPauseOrDispose {}
    }
    val link by rememberUpdatedState(notifications)
    val scope = rememberCoroutineScope()
    // "Download my data": the system's file picker chooses where; the file is written there, and a toast says so.
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(LogExportFile.MIME_TYPE)) { uri ->
        if (uri != null) scope.launch { context.exportLog(viewModel, uri) }
    }
    val actions = remember(viewModel, context) {
        object : SettingsActions by viewModel {
            override fun onBack() = back()
            override fun onOpenLicences() = licences()
            override fun onOpenAccount() = account()
            override fun onOpenNotificationSettings() = context.openNotificationSettings(link)
            override fun onOpenPrivacyPolicy() = context.openPrivacyPolicy()
            override fun onExport() = exporter.launch(LogExportFile.name(LocalDate.now()))
        }
    }
    val loaded = state
    if (loaded != null) {
        SettingsScreen(loaded, actions, notifications = notifications)
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    }
}

/**
 * Settings (DESIGN_SPEC §3.3.5; not drawn in the mockups, so it follows their language): the account and backup
 * (BABIES_AND_BACKUP §3.2), the babies, Appearance
 * (Auto · Night · Day), Language (System default · English · Dansk), watch sync with a retry, Notifications (the link
 * to the system's Live Update setting, §10.5), Privacy (the policy, "Download my data") and About (version, font
 * licences). Section headings are labelMedium;
 * grouped rows use the History list's r 20 / r 6 corners.
 *
 * @param notifications what the Notifications row offers; the route reads it from the system.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    notifications: NotificationLink = NotificationLink.LIVE_UPDATES,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val colors = MaterialTheme.colorScheme
    val bottomInset = with(LocalDensity.current) { windowInsets.getBottom(this).toDp() }
    // Edge to edge like History: the content scrolls behind the navigation bar and its end clears it.
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
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
                .padding(bottom = 24.dp + bottomInset),
        ) {
            Section(stringResource(R.string.settings_section_account)) { AccountRow(state, actions) }
            val babiesTitle = if (state.babies.size > 1) R.string.settings_section_babies else R.string.settings_section_baby
            Section(stringResource(babiesTitle)) { BabiesGroup(state, actions) }
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
            Section(stringResource(R.string.settings_section_notifications)) {
                NotificationsRow(notifications, actions::onOpenNotificationSettings)
            }
            Section(stringResource(R.string.settings_section_privacy)) { PrivacyGroup(actions) }
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

/**
 * Settings › Babies (BABIES_AND_BACKUP §2.4): one name field per baby (its label as the placeholder), "Add a baby"
 * (hidden at [Settings.MAX_BABIES]; the new baby's field takes the focus; disabled, with a line saying why, while the
 * paired watch runs a version from before several babies) and the hint that names stay on the parent's devices.
 */
@Composable
private fun BabiesGroup(state: SettingsUiState, actions: SettingsActions) {
    val labels = rememberFormatters(state.zone).labels
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var focusAdded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.babies.forEach { baby ->
            key(baby.id) {
                val requester = focusRequesters.getOrPut(baby.id) { FocusRequester() }
                NameField(
                    name = baby.name,
                    // The label an empty name shows: "Baby", or "Baby 2" … with several.
                    placeholder = labels.baby(baby.copy(name = ""), state.babies),
                    onChange = { actions.onBabyNameChange(baby.id, it) },
                    onDone = { actions.onBabyNameDone(baby.id) },
                    modifier = Modifier.focusRequester(requester),
                )
            }
        }
    }
    val newest = state.babies.last().id
    LaunchedEffect(newest) {
        if (focusAdded) {
            focusAdded = false
            focusRequesters[newest]?.requestFocus()
        }
    }
    if (state.canAddBaby) {
        OutlinePill(
            stringResource(R.string.settings_add_baby),
            onClick = {
                focusAdded = true
                actions.onAddBaby()
            },
            icon = XIcons.plus,
            modifier = Modifier.padding(top = 4.dp),
            enabled = !state.watchNeedsUpdate,
        )
        if (state.watchNeedsUpdate) {
            Text(
                stringResource(R.string.settings_add_baby_watch_update),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
    Text(
        stringResource(R.string.settings_babies_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
    )
}

/** A baby's name (shown in the top bar and notifications); empty shows its [placeholder] label. Done saves at once. */
@Composable
private fun NameField(
    name: String,
    placeholder: String,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.settings_baby_field)) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            onDone()
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

/**
 * Settings › Account (BABIES_AND_BACKUP §3.2): one row that opens the Account screen, saying where the log is. In
 * "Couldn't back up · Tap to retry" a tap also backs up again. Builds without a Firebase configuration say so, and the
 * row does nothing.
 */
@Composable
private fun AccountRow(state: SettingsUiState, actions: SettingsActions) {
    val colors = MaterialTheme.colorScheme
    when (val account = state.account) {
        AccountSummary.Unavailable -> GroupRow(first = true, last = true) {
            Text(
                stringResource(R.string.account_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
        AccountSummary.SignedOut -> GroupRow(first = true, last = true) {
            LinkRow(
                stringResource(R.string.account_not_signed_in),
                stringResource(R.string.account_not_signed_in_summary),
                actions::onOpenAccount,
            )
        }
        is AccountSummary.SignedIn -> {
            val clock = rememberFormatters(state.zone).clock
            val failed = account.status == CloudStatus.Error
            GroupRow(first = true, last = true) {
                LinkRow(
                    account.email,
                    backupStatusText(account.status, clock, state.zone),
                    onClick = {
                        if (failed) actions.onRetryBackup()
                        actions.onOpenAccount()
                    },
                    // "Backup deleted": nothing is backed up, but only "Back up now" on the Account screen starts again.
                    summaryColor = if (failed || account.status == CloudStatus.Deleted || account.status == CloudStatus.Paused) {
                        colors.error
                    } else {
                        colors.onSurfaceVariant
                    },
                    // "Backed up · 14:31" has digits: Google Sans Flex with tabular figures (the digit rule).
                    summaryStyle = PhoneType.dataSmall,
                )
            }
        }
    }
}

/** System default · English · Dansk as radio rows (language names in their own language). */
@Composable
private fun LanguageGroup(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    RadioGroup(AppLanguage.entries, selected, onSelect, label = { stringResource(Labels.language(it)) })
}

/** The sync line with "Sync now"; without a watch: "No watch connected. Everything is saved on this phone." */
@Composable
private fun SyncRow(state: SettingsUiState, actions: SettingsActions) {
    val colors = MaterialTheme.colorScheme
    val formatters = rememberFormatters(state.zone)
    val line = syncLineText(state.sync, state.zone, formatters)
    GroupRow(first = true, last = true) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            if (line == null) {
                Text(
                    stringResource(R.string.settings_sync_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            } else {
                // The line first (Danish "Synkroniseret med ur · 14:31" needs the full width), "Sync now" under it.
                val tint = if (state.sync is SyncStatus.Error) colors.error else colors.onSurfaceVariant
                Row(verticalAlignment = Alignment.CenterVertically) {
                    XIcon(watchGlyph(state.sync), contentDescription = null, tint = tint, size = 20.dp)
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

/**
 * The way to the system's notification settings: Live Updates on Android 16+ (the running timer in the status bar and
 * on the lock screen), the app's notification settings when notifications are off or on older Android.
 */
@Composable
private fun NotificationsRow(link: NotificationLink, onOpen: () -> Unit) {
    val (title, summary) = when (link) {
        NotificationLink.OFF -> R.string.settings_notifications_off to R.string.settings_notifications_off_summary
        NotificationLink.LIVE_UPDATES -> R.string.settings_live_updates to R.string.settings_live_updates_summary
        NotificationLink.NOTIFICATIONS -> R.string.settings_notifications to R.string.settings_notifications_summary
    }
    GroupRow(first = true, last = true) {
        LinkRow(stringResource(title), stringResource(summary), onOpen)
    }
}

/** A tappable row: title and summary with a chevron (opens another screen). */
@Composable
private fun LinkRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    summaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    summaryStyle: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(summary, style = summaryStyle, color = summaryColor)
        }
        Spacer(Modifier.width(12.dp))
        XIcon(XIcons.chevronRight, contentDescription = null, tint = colors.outline, size = 16.dp)
    }
}

/**
 * Settings › Privacy (docs/PRIVACY.md): "Privacy policy" (opens it in the browser) and "Download my data" (the whole log
 * and the babies' names as a JSON file, wherever the parent saves it).
 */
@Composable
private fun PrivacyGroup(actions: SettingsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(HistoryRowDefaults.Gap)) {
        GroupRow(first = true, last = false) {
            LinkRow(stringResource(R.string.settings_privacy_policy), stringResource(R.string.settings_privacy_policy_summary), actions::onOpenPrivacyPolicy)
        }
        GroupRow(first = false, last = true) {
            LinkRow(stringResource(R.string.settings_export), stringResource(R.string.settings_export_summary), actions::onExport)
        }
    }
}

/** Writes "Download my data" to [uri] and says how it went. */
private suspend fun Context.exportLog(viewModel: SettingsViewModel, uri: Uri) {
    val message = try {
        val count = checkNotNull(contentResolver.openOutputStream(uri)) { "No stream for $uri" }.use { viewModel.export(it) }
        resources.getQuantityString(R.plurals.settings_export_done, count, count)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Settings", "Could not save the export", e)
        getString(R.string.settings_export_failed)
    }
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/** The export's file type and suggested name. */
internal object LogExportFile {
    const val MIME_TYPE = "application/json"

    /** "pooparazzi-log-2026-10-29.json". */
    fun name(date: LocalDate): String = "pooparazzi-log-$date.json"
}

/** "Pooparazzi · Version 0.1.0" and "Font licences" (opens the OFL texts). */
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
            LinkRow(stringResource(R.string.settings_font_licences), stringResource(R.string.settings_font_licences_summary), onOpenLicences)
        }
    }
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
