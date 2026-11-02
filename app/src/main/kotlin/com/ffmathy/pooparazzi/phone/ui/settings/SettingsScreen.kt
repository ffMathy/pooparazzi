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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.domain.ForecastKind
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.type.PhoneType
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.notify.ForecastNotifications
import com.ffmathy.pooparazzi.phone.notify.NotificationLink
import com.ffmathy.pooparazzi.phone.notify.openForecastChannelSettings
import com.ffmathy.pooparazzi.phone.notify.openNotificationSettings
import com.ffmathy.pooparazzi.phone.ui.account.backupStatusText
import com.ffmathy.pooparazzi.phone.ui.components.ConnectedChoiceGroup
import com.ffmathy.pooparazzi.phone.ui.components.GroupRow
import com.ffmathy.pooparazzi.phone.ui.components.RadioGroup
import com.ffmathy.pooparazzi.phone.ui.components.XTopBar
import com.ffmathy.pooparazzi.phone.ui.components.syncLineText
import com.ffmathy.pooparazzi.phone.ui.components.watchGlyph
import com.ffmathy.pooparazzi.phone.ui.edit.OutlinePill
import com.ffmathy.pooparazzi.phone.ui.family.FamilyActions
import com.ffmathy.pooparazzi.phone.ui.family.FamilySection
import com.ffmathy.pooparazzi.phone.ui.family.FamilyUiState
import com.ffmathy.pooparazzi.phone.ui.family.FamilyViewModel
import com.ffmathy.pooparazzi.phone.ui.family.inviteShareIntent
import com.ffmathy.pooparazzi.phone.ui.history.HistoryRowDefaults
import com.ffmathy.pooparazzi.phone.ui.preview.SettingsSamples
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.ffmathy.pooparazzi.core.R as CoreR

/** Settings wired to its view model and navigation. */
@Composable
fun SettingsRoute(onBack: () -> Unit, onOpenLicences: () -> Unit, onOpenAccount: () -> Unit = {}) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val familyViewModel: FamilyViewModel = viewModel(factory = FamilyViewModel.Factory)
    val family by familyViewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    // Only while this screen is resumed: a second tap during a transition must not pop Home or stack Licences twice.
    val back by rememberUpdatedState(dropUnlessResumed(block = onBack))
    val licences by rememberUpdatedState(dropUnlessResumed(block = onOpenLicences))
    val account by rememberUpdatedState(dropUnlessResumed(block = onOpenAccount))
    val context = LocalContext.current
    // The permission and the system switch can change in system settings: re-read them whenever Settings resumes.
    var notifications by remember { mutableStateOf(NotificationLink.current(context)) }
    // Each prediction notification's channel: on or off in the system settings.
    var channels by remember { mutableStateOf(ForecastNotifications.channelStates(context)) }
    LifecycleResumeEffect(context) {
        notifications = NotificationLink.current(context)
        channels = ForecastNotifications.channelStates(context)
        // A watch without Pooparazzi: asked again on every resume, so "Install on your watch" goes once it is installed.
        viewModel.refreshWatchApp()
        onPauseOrDispose {}
    }
    val link by rememberUpdatedState(notifications)
    val scope = rememberCoroutineScope()
    // "Download my data": the system's file picker chooses where; the file is written there, and a toast says so.
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(LogExportFile.MIME_TYPE)) { uri ->
        if (uri != null) scope.launch { context.exportLog(viewModel, uri) }
    }
    // "Import data": the system's file picker chooses the export; it is merged into the log, and a toast says how it went.
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { context.importLog(viewModel, uri) }
    }
    val actions = remember(viewModel, context, uriHandler) {
        object : SettingsActions by viewModel {
            override fun onBack() = back()
            override fun onOpenLicences() = licences()
            override fun onOpenAccount() = account()
            override fun onOpenNotificationSettings() = context.openNotificationSettings(link)
            override fun onOpenForecastChannel(kind: ForecastKind) =
                context.openForecastChannelSettings(kind, viewModel.state.value?.language ?: AppLanguage.SYSTEM)
            override fun onOpenPrivacy() {
                viewModel.state.value?.let { runCatching { uriHandler.openUri(it.privacyUrl) } }
            }
            override fun onOpenSourceCode() {
                runCatching { uriHandler.openUri(SOURCE_CODE_URL) }
            }
            override fun onOpenLinkedIn() {
                runCatching { uriHandler.openUri(LINKEDIN_URL) }
            }
            override fun onExport() = exporter.launch(LogExportFile.name(LocalDate.now()))
            override fun onImport() = importer.launch(LogExportFile.IMPORT_TYPES)
            override fun onInstallOnWatch() {
                scope.launch { context.installOnWatch(viewModel) }
            }
        }
    }
    // "Invite someone": the share sheet with the invite's link and text.
    LaunchedEffect(familyViewModel, context) {
        familyViewModel.shares.collect { invite ->
            runCatching { context.startActivity(inviteShareIntent(context, invite)) }
        }
    }
    val loaded = state
    if (loaded != null) {
        SettingsScreen(loaded, actions, notifications = notifications, channels = channels, family = family, familyActions = familyViewModel)
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    }
}

/**
 * Settings (DESIGN_SPEC §3.3.5; not drawn in the mockups, so it follows their language): the account and backup
 * (BABIES_AND_BACKUP §3.2), the babies, Appearance
 * (each with an optional birth date), Appearance (Auto · Night · Day), Language (System default · English · Dansk), the
 * watch (sync with a retry, or "Install on your watch" for a watch without the app, and the estimates its face shows),
 * Notifications (the link to the system's Live Update setting, §10.5, and one row per prediction notification, each
 * opening its channel's page) and About (version, font licences), and at the bottom a footer: "Made with
 * ❤️ by a father in Aarhus, Denmark", that Pooparazzi is open source, and GitHub and LinkedIn logo buttons. Section
 * headings are labelMedium; grouped rows use the History list's r 20 / r 6 corners.
 *
 * @param notifications what the Notifications row offers; the route reads it from the system.
 * @param channels whether each prediction notification's channel is on; the route reads it from the system.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    notifications: NotificationLink = NotificationLink.LIVE_UPDATES,
    channels: Map<ForecastKind, Boolean> = ForecastKind.entries.associateWith { true },
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    family: FamilyUiState = FamilyUiState(),
    familyActions: FamilyActions = FamilyActions.None,
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
            if (state.account is AccountSummary.SignedIn && family.signedIn) {
                Section(stringResource(R.string.settings_section_family)) { FamilySection(family, familyActions) }
            }
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
            Section(stringResource(R.string.settings_section_watch)) {
                SyncRow(state, actions)
                WatchForecastsGroup(state.watchForecasts, actions)
            }
            Section(stringResource(R.string.settings_section_notifications)) {
                NotificationsRow(notifications, actions::onOpenNotificationSettings)
                ForecastChannelsGroup(channels, actions)
            }
            Section(stringResource(R.string.settings_section_about)) {
                AboutGroup(state.versionName, actions)
            }
            MadeByFooter(actions)
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
 * Settings › Babies (BABIES_AND_BACKUP §2.4): one name field per baby (its label as the placeholder) with its birth
 * date under it ("Add birth date", or "Born 14 Sep 2026"; both open the date picker), "Add a baby"
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
                    removeLabel = if (state.canRemoveBaby) {
                        stringResource(R.string.settings_remove_baby_cd, labels.baby(baby, state.babies))
                    } else {
                        null
                    },
                    onRemove = { actions.onRemoveBaby(baby.id) },
                )
                BirthDatePill(baby, onClick = { actions.onEditBirthDate(baby.id) })
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
    // Signed in, the names go to the family's backup too (BABIES_AND_BACKUP §1).
    val signedIn = state.account is AccountSummary.SignedIn
    val hint = if (signedIn) R.string.settings_babies_hint_family else R.string.settings_babies_hint
    Text(
        stringResource(hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
    )
    state.removing?.let { RemoveBabyDialog(labels.baby(it, state.babies), signedIn, actions) }
    state.birthDateFor?.let { BirthDateDialog(it, actions) }
}

/** "Add birth date", or "Born 14 Sep 2026" (the date in the app language's medium style); opens the date picker. */
@Composable
private fun BirthDatePill(baby: Baby, onClick: () -> Unit) {
    val born = baby.born
    val locale = LocalConfiguration.current.locales[0]
    val text = if (born == null) {
        stringResource(R.string.settings_birth_date_add)
    } else {
        stringResource(R.string.settings_birth_date, DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(born))
    }
    OutlinePill(text, onClick = onClick, icon = if (born == null) XIcons.plus else XIcons.edit)
}

/**
 * The birth date picker (Material's date picker, today and earlier only): OK saves the picked date, Remove (when one
 * is set) forgets it, Cancel leaves it as it was. The birth date tunes what comes next to the baby's age.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateDialog(baby: Baby, actions: SettingsActions) {
    val today = remember { LocalDate.now() }
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = baby.born?.let { it.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
        yearRange = (today.year - MAX_AGE_YEARS)..today.year,
        selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = actions::onDismissBirthDate,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = picker.selectedDateMillis?.let { java.time.Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate() }
                    if (picked != null) actions.onBirthDate(baby.id, picked) else actions.onDismissBirthDate()
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            Row {
                if (baby.born != null) {
                    TextButton(onClick = { actions.onBirthDate(baby.id, null) }) { Text(stringResource(R.string.settings_birth_date_remove)) }
                }
                TextButton(onClick = actions::onDismissBirthDate) { Text(stringResource(android.R.string.cancel)) }
            }
        },
    ) {
        DatePicker(picker, title = {
            Text(
                stringResource(R.string.settings_birth_date_title),
                modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
            )
        })
    }
}

/** Babies older than this are not expected: the picker's years start here. */
private const val MAX_AGE_YEARS = 5

/** "Remove Noa?": the baby's log goes with it (for the whole family when signed in), with Cancel and "Remove". */
@Composable
private fun RemoveBabyDialog(label: String, signedIn: Boolean, actions: SettingsActions) {
    val colors = MaterialTheme.colorScheme
    val body = if (signedIn) R.string.settings_remove_baby_body_family else R.string.settings_remove_baby_body
    AlertDialog(
        onDismissRequest = actions::onDismissRemoveBaby,
        title = { Text(stringResource(R.string.settings_remove_baby_title, label)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = actions::onConfirmRemoveBaby) {
                Text(stringResource(R.string.settings_remove_baby_confirm), color = colors.error)
            }
        },
        dismissButton = {
            TextButton(onClick = actions::onDismissRemoveBaby) {
                Text(stringResource(CoreR.string.action_cancel), color = colors.onSurface)
            }
        },
        containerColor = colors.surfaceContainerHigh,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
    )
}

/**
 * A baby's name (shown in the top bar and notifications); empty shows its [placeholder] label. Done saves at once.
 * With a [removeLabel] (two or more babies) a 48 dp remove button ends the field.
 */
@Composable
private fun NameField(
    name: String,
    placeholder: String,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    removeLabel: String? = null,
    onRemove: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.settings_baby_field)) },
        placeholder = { Text(placeholder) },
        trailingIcon = removeLabel?.let { label ->
            {
                IconButton(onClick = onRemove) {
                    XIcon(XIcons.delete, contentDescription = label, tint = colors.onSurfaceVariant)
                }
            }
        },
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
                    summaryColor = if (failed || account.status == CloudStatus.Deleted) colors.error else colors.onSurfaceVariant,
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

/**
 * Settings › Watch: the sync line with "Sync now"; without a watch: "No watch yet" and that Pooparazzi has a watch app
 * for when the parent gets one. With a connected watch that lacks Pooparazzi, "Install on your watch" (opens its Play
 * Store page on the watch) takes the place of "No watch yet", or follows the sync line when another watch has the app.
 */
@Composable
private fun SyncRow(state: SettingsUiState, actions: SettingsActions) {
    val colors = MaterialTheme.colorScheme
    val formatters = rememberFormatters(state.zone)
    val line = syncLineText(state.sync, state.zone, formatters)
    Column(verticalArrangement = Arrangement.spacedBy(HistoryRowDefaults.Gap)) {
        if (line != null || !state.watchWithoutApp) {
            GroupRow(first = true, last = !state.watchWithoutApp) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                    if (line == null) {
                        Column(Modifier.semantics(mergeDescendants = true) {}) {
                            Text(stringResource(R.string.settings_no_watch), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                            Text(
                                stringResource(R.string.settings_no_watch_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
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
        if (state.watchWithoutApp) {
            GroupRow(first = line == null, last = true) {
                LinkRow(
                    stringResource(R.string.settings_watch_install),
                    stringResource(R.string.settings_watch_install_summary),
                    actions::onInstallOnWatch,
                )
            }
        }
    }
}

/**
 * Settings › Watch › On the watch face: one checkbox row per estimate (the next feed, the next poop, the next sleep and
 * the wake-up), all on until the parent turns some off. The watch face shows the earliest of those ticked, while the
 * baby sleeps and while awake; a hint says so.
 */
@Composable
private fun WatchForecastsGroup(shown: Set<ForecastKind>, actions: SettingsActions) {
    val colors = MaterialTheme.colorScheme
    Text(
        stringResource(R.string.settings_watch_forecasts),
        style = MaterialTheme.typography.titleSmall,
        color = colors.onSurface,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(HistoryRowDefaults.Gap)) {
        ForecastKind.entries.forEachIndexed { index, kind ->
            val checked = kind in shown
            GroupRow(first = index == 0, last = index == ForecastKind.entries.lastIndex) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { actions.onWatchForecast(kind, it) })
                        .padding(start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(forecastLabel(kind)), style = MaterialTheme.typography.titleMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
                    XIcon(
                        if (checked) XIcons.checkboxChecked else XIcons.checkbox,
                        contentDescription = null,
                        tint = if (checked) colors.onSurface else colors.outline,
                        size = 24.dp,
                    )
                }
            }
        }
    }
    Text(
        stringResource(R.string.settings_watch_forecasts_hint),
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
    )
}

/**
 * Settings › Notifications › What comes next: one row per prediction notification, saying whether its channel is on
 * ("On" / "Off"); a tap opens that channel's page in the system's notification settings, where it is turned on or off.
 */
@Composable
private fun ForecastChannelsGroup(channels: Map<ForecastKind, Boolean>, actions: SettingsActions) {
    val colors = MaterialTheme.colorScheme
    Text(
        stringResource(R.string.settings_forecast_notifications),
        style = MaterialTheme.typography.titleSmall,
        color = colors.onSurface,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(HistoryRowDefaults.Gap)) {
        ForecastKind.entries.forEachIndexed { index, kind ->
            GroupRow(first = index == 0, last = index == ForecastKind.entries.lastIndex) {
                val on = channels[kind] ?: true
                LinkRow(
                    stringResource(forecastLabel(kind)),
                    stringResource(if (on) R.string.settings_channel_on else R.string.settings_channel_off),
                    onClick = { actions.onOpenForecastChannel(kind) },
                )
            }
        }
    }
    Text(
        stringResource(R.string.settings_forecast_notifications_hint),
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
    )
}

/** "Next feed", "Next poop", "Next sleep and wake-up": the estimates' names, as their channels are named. */
private fun forecastLabel(kind: ForecastKind): Int = when (kind) {
    ForecastKind.FEED -> R.string.notification_channel_forecast_feed
    ForecastKind.POOP -> R.string.notification_channel_forecast_poop
    ForecastKind.SLEEP -> R.string.notification_channel_forecast_sleep
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
 * "Pooparazzi · Version 0.1.0", "Privacy policy" (opens it in the browser, BABIES_AND_BACKUP §3.2), "Download my data"
 * (the log and the babies' names as one ZIP file, wherever the parent saves it), "Import data" (merges such a file into
 * the log) and "Font licences" (opens the OFL texts).
 */
@Composable
private fun AboutGroup(versionName: String, actions: SettingsActions) {
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
        GroupRow(first = false, last = false) {
            LinkRow(stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_summary), actions::onOpenPrivacy)
        }
        GroupRow(first = false, last = false) {
            LinkRow(stringResource(R.string.settings_export), stringResource(R.string.settings_export_summary), actions::onExport)
        }
        GroupRow(first = false, last = false) {
            LinkRow(stringResource(R.string.settings_import), stringResource(R.string.settings_import_summary), actions::onImport)
        }
        GroupRow(first = false, last = true) {
            LinkRow(stringResource(R.string.settings_font_licences), stringResource(R.string.settings_font_licences_summary), actions::onOpenLicences)
        }
    }
}

/** Where Pooparazzi's source code is read. */
internal const val SOURCE_CODE_URL = "https://github.com/ffMathy/pooparazzi"

/** The maker's LinkedIn profile. */
internal const val LINKEDIN_URL = "https://www.linkedin.com/in/mathiaslorenzen/"

/**
 * The end of Settings, centred under About: "Made with ❤️ by a father in Aarhus, Denmark", "Pooparazzi is open source"
 * and two logo buttons (48 dp targets): GitHub opens the source code, LinkedIn the maker's profile.
 */
@Composable
private fun MadeByFooter(actions: SettingsActions) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.settings_made_by),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.settings_open_source),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = actions::onOpenSourceCode) {
                Icon(BrandIcons.gitHub, stringResource(R.string.cd_source_code), Modifier.size(24.dp), tint = colors.onSurfaceVariant)
            }
            IconButton(onClick = actions::onOpenLinkedIn) {
                Icon(BrandIcons.linkedIn, stringResource(R.string.cd_linkedin), Modifier.size(24.dp), tint = colors.onSurfaceVariant)
            }
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

/** "Install on your watch": opens the Play Store page on the watch, and says to look there (or that it couldn't). */
private suspend fun Context.installOnWatch(viewModel: SettingsViewModel) {
    val opened = viewModel.installOnWatch()
    val message = if (opened) R.string.settings_watch_install_opened else R.string.settings_watch_install_failed
    Toast.makeText(this, getString(message), Toast.LENGTH_LONG).show()
}

/** Merges the export at [uri] into the log ("Import data") and says how it went. */
private suspend fun Context.importLog(viewModel: SettingsViewModel, uri: Uri) {
    val message = try {
        val count = viewModel.importData(checkNotNull(contentResolver.openInputStream(uri)) { "No stream for $uri" })
        if (count == 0) getString(R.string.settings_import_none) else resources.getQuantityString(R.plurals.settings_import_done, count, count)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Settings", "Could not import", e)
        getString(R.string.settings_import_failed)
    }
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/** The export's file type and suggested name, and what "Import data" offers to open. */
internal object LogExportFile {
    const val MIME_TYPE = "application/zip"

    /** The ZIP file (as file managers label it), and the JSON file of earlier versions. */
    val IMPORT_TYPES = arrayOf(MIME_TYPE, "application/x-zip-compressed", "application/json", "application/octet-stream")

    /** "pooparazzi-log-2026-10-29.zip". */
    fun name(date: LocalDate): String = "pooparazzi-log-$date.zip"
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
