package com.ffmathy.pooparazzi.phone.ui.account

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.type.PhoneType
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.privacy.openPrivacyPolicy
import com.ffmathy.pooparazzi.phone.ui.components.ConnectedChoiceGroup
import com.ffmathy.pooparazzi.phone.ui.components.GroupRow
import com.ffmathy.pooparazzi.phone.ui.components.XTopBar
import com.ffmathy.pooparazzi.phone.ui.preview.AccountSamples
import com.ffmathy.pooparazzi.phone.ui.theme.LocalNightLight
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import com.ffmathy.pooparazzi.core.R as CoreR

/** The view model's actions with the activity the Google account picker opens over. */
@Composable
private fun rememberAccountActions(viewModel: AccountViewModel, extra: AccountActions? = null): AccountActions {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val extraActions by rememberUpdatedState(extra)
    return remember(viewModel, activity, context) {
        object : AccountActions {
            override fun onBack() { extraActions?.onBack() }
            override fun onGoogle() { activity?.let(viewModel::onGoogle) }
            override fun onAgreed(agreed: Boolean) = viewModel.onAgreed(agreed)
            override fun onOpenPrivacyPolicy() = context.openPrivacyPolicy()
            override fun onAgreeSignedIn() = viewModel.onAgreeSignedIn()
            override fun onMode(mode: EmailMode) = viewModel.onMode(mode)
            override fun onEmail(email: String) = viewModel.onEmail(email)
            override fun onPassword(password: String) = viewModel.onPassword(password)
            override fun onSubmit() = viewModel.onSubmit()
            override fun onForgotPassword() = viewModel.onForgotPassword()
            override fun onBackUpNow() = viewModel.onBackUpNow()
            override fun onSignOut() = viewModel.onSignOut()
            override fun onDeleteStart() = viewModel.onDeleteStart()
            override fun onDeletePassword(password: String) = viewModel.onDeletePassword(password)
            override fun onDeleteConfirm() = viewModel.onDeleteConfirm(activity)
            override fun onDeleteDismiss() = viewModel.onDeleteDismiss()
            override fun onContinueWithout() { extraActions?.onContinueWithout() }
        }
    }
}

/** Settings › Account wired to its view model. */
@Composable
fun AccountRoute(onBack: () -> Unit) {
    val viewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val back = dropUnlessResumed(block = onBack)
    val nav = remember(back) { object : AccountActions { override fun onBack() = back() } }
    AccountScreen(state, rememberAccountActions(viewModel, nav))
}

/**
 * The Welcome screen of the first run wired to the account view model; [onDone] when it has been answered. Back
 * counts as "Continue without an account" (the app opens Home rather than closing).
 */
@Composable
fun WelcomeRoute(onDone: () -> Unit) {
    val viewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val done by rememberUpdatedState(onDone)
    val nav = remember { object : AccountActions { override fun onContinueWithout() = done() } }
    BackHandler(enabled = !state.busy) { done() }
    WelcomeScreen(state, rememberAccountActions(viewModel, nav))
}

/**
 * Settings › Account (BABIES_AND_BACKUP §3.3). Signed out: the explanation, "Continue with Google" first, then "or use
 * email" with the email form. Signed in: the address, the backup status, "Back up now", "Sign out" and "Delete
 * account…".
 */
@Composable
fun AccountScreen(
    state: AccountUiState,
    actions: AccountActions,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val colors = MaterialTheme.colorScheme
    val bottomInset = with(LocalDensity.current) { windowInsets.getBottom(this).toDp() }
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        XTopBar(
            title = stringResource(R.string.settings_section_account),
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
                .padding(top = 8.dp, bottom = 24.dp + bottomInset),
        ) {
            val user = state.user
            when {
                !state.available -> BodyText(stringResource(R.string.account_unavailable))
                user == null -> {
                    Explanation()
                    Spacer(Modifier.height(24.dp))
                    SignInForm(state, actions)
                }
                else -> SignedIn(state, user.email, actions)
            }
        }
    }
}

/**
 * The first-run Welcome (§3.1): where the log is without an account, the sign-in choices, and "Continue without an
 * account".
 */
@Composable
fun WelcomeScreen(
    state: AccountUiState,
    actions: AccountActions,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val colors = MaterialTheme.colorScheme
    val bottomInset = with(LocalDensity.current) { windowInsets.getBottom(this).toDp() }
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 32.dp, bottom = 24.dp + bottomInset),
    ) {
        Text(
            stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(16.dp))
        Explanation()
        Spacer(Modifier.height(24.dp))
        SignInForm(state, actions)
        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = actions::onContinueWithout,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.welcome_continue_without), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
        }
    }
}

@Composable
private fun Explanation() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BodyText(stringResource(R.string.welcome_body_local))
        BodyText(stringResource(R.string.welcome_body_later))
    }
}

@Composable
private fun BodyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The consent to the backup (§3.4) and "Privacy policy", then "Continue with Google" (when the build can), "or use
 * email", then Sign in / Create account, the fields, the main button and, for signing in, "Forgot password?". The
 * error or notice line sits above the main button; signing in without the consent ticked says so there.
 */
@Composable
private fun SignInForm(state: AccountUiState, actions: AccountActions) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val enabled = !state.busy
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConsentCheckbox(state.agreed, actions::onAgreed, enabled)
        PrivacyPolicyLink(actions)
        if (state.googleAvailable) {
            PrimaryButton(stringResource(R.string.account_google), onClick = actions::onGoogle, enabled = enabled)
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = colors.outlineVariant)
                Text(
                    stringResource(R.string.account_or_email),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(Modifier.weight(1f), color = colors.outlineVariant)
            }
        }
        ConnectedChoiceGroup(
            options = EmailMode.entries,
            selected = state.mode,
            onSelect = actions::onMode,
            label = { stringResource(if (it == EmailMode.SIGN_IN) R.string.account_mode_sign_in else R.string.account_mode_create) },
            enabled = enabled,
        )
        AccountField(
            value = state.email,
            onChange = actions::onEmail,
            label = stringResource(R.string.account_email),
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            enabled = enabled,
        )
        AccountField(
            value = state.password,
            onChange = actions::onPassword,
            label = stringResource(R.string.account_password),
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            enabled = enabled,
            supporting = if (state.mode == EmailMode.CREATE) stringResource(R.string.account_password_hint) else null,
            onDone = {
                focus.clearFocus()
                actions.onSubmit()
            },
        )
        state.error?.let { ErrorLine(stringResource(it.message())) }
        if (state.notice == AccountNotice.RESET_SENT) {
            Text(
                stringResource(R.string.account_reset_sent, state.noticeEmail),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val submit = if (state.mode == EmailMode.SIGN_IN) R.string.account_mode_sign_in else R.string.account_mode_create
        if (state.googleAvailable) {
            SecondaryButton(stringResource(submit), onClick = actions::onSubmit, enabled = enabled)
        } else {
            PrimaryButton(stringResource(submit), onClick = actions::onSubmit, enabled = enabled)
        }
        if (state.mode == EmailMode.SIGN_IN) {
            TextButton(onClick = actions::onForgotPassword, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.account_forgot_password), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
            }
        }
    }
}

/**
 * "I agree that my baby's log … is stored in my backup …": a checkbox row, the whole row the toggle (at least 48 dp
 * tall), read by TalkBack as the text with its checkbox state.
 */
@Composable
private fun ConsentCheckbox(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        XIcon(
            if (checked) XIcons.checkboxChecked else XIcons.checkbox,
            contentDescription = null,
            tint = if (checked) colors.onSurface else colors.outline,
            modifier = Modifier.padding(top = 2.dp, end = 12.dp),
        )
        Text(stringResource(R.string.account_consent), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
    }
}

@Composable
private fun PrivacyPolicyLink(actions: AccountActions) {
    TextButton(onClick = actions::onOpenPrivacyPolicy, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(
            stringResource(R.string.account_privacy_policy),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = TextDecoration.Underline,
        )
    }
}

/**
 * Signed in: "Signed in as" the address, the backup status line, "Back up now", "Sign out", "Delete account…". An
 * account that has not agreed to the backup on this phone (§3.4) shows the consent and "Agree and back up" in place of
 * "Back up now".
 */
@Composable
private fun SignedIn(state: AccountUiState, email: String, actions: AccountActions) {
    val colors = MaterialTheme.colorScheme
    val clock = rememberFormatters(state.zone).clock
    val status = backupStatusText(state.cloud, clock, state.zone)
    val failed = state.cloud == CloudStatus.Error
    GroupRow(first = true, last = true) {
        Column(
            Modifier
                .fillMaxWidth()
                // "Couldn't back up · Tap to retry": the row is the retry.
                .then(if (failed) Modifier.clickable(role = Role.Button, onClick = actions::onBackUpNow) else Modifier)
                .padding(16.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(stringResource(R.string.account_signed_in_as), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(email, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            // A deleted backup is no failure to retry, but nothing is backed up: it reads as a warning all the same.
            val tint = if (failed || state.cloud == CloudStatus.Deleted || state.cloud == CloudStatus.Paused) colors.error else colors.onSurfaceVariant
            Text(status, style = PhoneType.dataMedium, color = tint, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.user?.consented == false) {
            BodyText(stringResource(R.string.account_paused_body))
            Text(stringResource(R.string.account_consent), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            PrivacyPolicyLink(actions)
            state.error?.let { ErrorLine(stringResource(it.message())) }
            PrimaryButton(stringResource(R.string.account_agree), onClick = actions::onAgreeSignedIn, enabled = !state.busy)
        } else {
            PrimaryButton(
                stringResource(R.string.account_back_up_now),
                onClick = actions::onBackUpNow,
                enabled = !state.backingUp && state.cloud != CloudStatus.Syncing,
            )
        }
        SecondaryButton(stringResource(R.string.account_sign_out), onClick = actions::onSignOut, enabled = !state.busy)
        BodyText(stringResource(R.string.account_signed_in_hint))
        Spacer(Modifier.height(8.dp))
        val delete = state.delete
        if (delete == null) {
            TextButton(onClick = actions::onDeleteStart, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.account_delete), style = MaterialTheme.typography.labelLarge, color = colors.error)
            }
        } else {
            DeleteConfirmation(delete, actions)
        }
    }
}

/**
 * "Delete account?" in place of "Delete account…": what is deleted and what stays, then the password (email accounts)
 * or a confirmation only (Google accounts sign in with Google again when it is confirmed). Errors in words, a partial
 * failure as it is. Inline rather than a dialog window, so the password field sits with the rest of the screen; Back
 * cancels it.
 */
@Composable
private fun DeleteConfirmation(dialog: DeleteState, actions: AccountActions) {
    val colors = MaterialTheme.colorScheme
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { bringIntoView.bringIntoView() }
    BackHandler(enabled = !dialog.busy, onBack = actions::onDeleteDismiss)
    GroupRow(first = true, last = true) {
        Column(
            Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoView)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.account_delete_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            BodyText(stringResource(R.string.account_delete_body))
            if (dialog.google) {
                BodyText(stringResource(R.string.account_delete_google_prompt))
            } else {
                BodyText(stringResource(R.string.account_delete_password_prompt))
                AccountField(
                    value = dialog.password,
                    onChange = actions::onDeletePassword,
                    label = stringResource(R.string.account_password),
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    enabled = !dialog.busy,
                    onDone = actions::onDeleteConfirm,
                )
            }
            dialog.problem?.let { ErrorLine(stringResource(it.message())) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = actions::onDeleteDismiss, enabled = !dialog.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(CoreR.string.action_cancel), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                }
                TextButton(onClick = actions::onDeleteConfirm, enabled = !dialog.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(CoreR.string.action_delete), style = MaterialTheme.typography.labelLarge, color = colors.error)
                }
            }
        }
    }
}

@Composable
private fun ErrorLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun AccountField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    enabled: Boolean,
    supporting: String? = null,
    onDone: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.titleMedium,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (keyboardType == KeyboardType.Password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(
            onNext = { focus.moveFocus(FocusDirection.Down) },
            onDone = { onDone() },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.onSurface,
            unfocusedBorderColor = colors.outline,
            focusedLabelColor = colors.onSurface,
            unfocusedLabelColor = colors.onSurfaceVariant,
            cursorColor = colors.onSurface,
        ),
    )
}

/** A full-width 56 dp filled button (the sheets' Save colours). */
@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    val night = LocalNightLight.current.isNight
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (night) colors.primaryContainer else colors.primary,
            contentColor = if (night) colors.onPrimaryContainer else colors.onPrimary,
            disabledContainerColor = colors.onSurface.copy(alpha = 0.12f),
            disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** A full-width 56 dp outlined button. */
@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Preview(name = "Signed out · Night", widthDp = 390, heightDp = 1000)
@Composable
private fun AccountSignedOutPreview() {
    NightLightTheme(night = true) { AccountScreen(AccountSamples.signedOut(), AccountActions.None) }
}

@Preview(name = "Signed in · Day · Danish", widthDp = 390, heightDp = 844, locale = "da")
@Composable
private fun AccountSignedInPreview() {
    NightLightTheme(night = false) { AccountScreen(AccountSamples.signedIn(), AccountActions.None) }
}

@Preview(name = "Welcome · Day", widthDp = 390, heightDp = 1100)
@Composable
private fun WelcomePreview() {
    NightLightTheme(night = false) { WelcomeScreen(AccountSamples.signedOut(), AccountActions.None) }
}
