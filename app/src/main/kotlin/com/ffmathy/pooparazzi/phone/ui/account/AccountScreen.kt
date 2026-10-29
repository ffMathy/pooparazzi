package com.ffmathy.pooparazzi.phone.ui.account

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.type.PhoneType
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.ui.components.GroupRow
import com.ffmathy.pooparazzi.phone.ui.components.XTopBar
import com.ffmathy.pooparazzi.phone.ui.preview.AccountSamples
import com.ffmathy.pooparazzi.phone.ui.theme.LocalNightLight
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import com.ffmathy.pooparazzi.core.R as CoreR

/** The view model's actions with the activity the Google account picker opens over, and the privacy policy link. */
@Composable
private fun rememberAccountActions(viewModel: AccountViewModel, extra: AccountActions? = null): AccountActions {
    val activity = LocalActivity.current
    val uriHandler = LocalUriHandler.current
    val extraActions by rememberUpdatedState(extra)
    // While the screen shows, it takes the email sign-in links that open the app.
    LaunchedEffect(viewModel) { viewModel.receiveLinks() }
    return remember(viewModel, activity, uriHandler) {
        object : AccountActions {
            override fun onBack() { extraActions?.onBack() }
            override fun onGoogle() { activity?.let(viewModel::onGoogle) }
            override fun onEmail(email: String) = viewModel.onEmail(email)
            override fun onSendLink() = viewModel.onSendLink()
            override fun onUseAnotherEmail() = viewModel.onUseAnotherEmail()
            override fun onFinishSignIn() = viewModel.onFinishSignIn()
            override fun onOpenPrivacy() {
                runCatching { uriHandler.openUri(viewModel.state.value.privacyUrl) }
            }
            override fun onBackUpNow() = viewModel.onBackUpNow()
            override fun onSignOut() = viewModel.onSignOut()
            override fun onDeleteStart() = viewModel.onDeleteStart()
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
 * email" with "Email me a sign-in link", and the privacy policy. Signed in: the address, the backup status, "Back up
 * now", "Sign out" and "Delete account…".
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
 * "Continue with Google" (when the build can), "or use email", then the email address and "Email me a sign-in link";
 * once sent, "Check your email" with "Send again" and "Use another email". A link opened on a phone that did not ask
 * for it asks for the address instead. There are no passwords. The error line sits above the email button, and the
 * consent to the privacy policy under everything.
 */
@Composable
private fun SignInForm(state: AccountUiState, actions: AccountActions) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val enabled = !state.busy
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.joining) {
            Text(
                stringResource(R.string.account_join_waiting),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.linkNeedsEmail) {
            BodyText(stringResource(R.string.account_link_needs_email))
            EmailField(state, actions, imeAction = ImeAction.Done) {
                focus.clearFocus()
                actions.onFinishSignIn()
            }
            state.error?.let { ErrorLine(stringResource(it.message())) }
            PrimaryButton(stringResource(R.string.account_sign_in), onClick = actions::onFinishSignIn, enabled = enabled)
        } else {
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
            val sentTo = state.linkSentTo
            if (sentTo == null) {
                EmailField(state, actions, imeAction = ImeAction.Send) {
                    focus.clearFocus()
                    actions.onSendLink()
                }
                state.error?.let { ErrorLine(stringResource(it.message())) }
                val send = stringResource(R.string.account_email_link)
                if (state.googleAvailable) {
                    SecondaryButton(send, onClick = actions::onSendLink, enabled = enabled)
                } else {
                    PrimaryButton(send, onClick = actions::onSendLink, enabled = enabled)
                }
            } else {
                Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    Text(stringResource(R.string.account_link_sent_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Spacer(Modifier.height(4.dp))
                    BodyText(stringResource(R.string.account_link_sent, sentTo))
                }
                state.error?.let { ErrorLine(stringResource(it.message())) }
                SecondaryButton(stringResource(R.string.account_send_again), onClick = actions::onSendLink, enabled = enabled)
                TextButton(onClick = actions::onUseAnotherEmail, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.account_use_another_email), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                }
            }
        }
        PrivacyConsent(actions::onOpenPrivacy)
    }
}

/** The address the sign-in link goes to. */
@Composable
private fun EmailField(state: AccountUiState, actions: AccountActions, imeAction: ImeAction, onDone: () -> Unit) {
    AccountField(
        value = state.email,
        onChange = actions::onEmail,
        label = stringResource(R.string.account_email),
        keyboardType = KeyboardType.Email,
        imeAction = imeAction,
        enabled = !state.busy,
        onDone = onDone,
    )
}

/** "By continuing you agree to the privacy policy", the last words a link to it. */
@Composable
private fun PrivacyConsent(onOpen: () -> Unit) = PolicyLinkText(R.string.account_privacy_consent, onOpen)

/** The sentence [sentence] (with a `%1$s` for the words "privacy policy"), those words a link to the policy. */
@Composable
internal fun PolicyLinkText(@StringRes sentence: Int, onOpen: () -> Unit, modifier: Modifier = Modifier.padding(top = 4.dp)) {
    val colors = MaterialTheme.colorScheme
    val open by rememberUpdatedState(onOpen)
    val link = stringResource(R.string.account_privacy_link)
    val sentence = stringResource(sentence, link)
    val text = remember(sentence, link, colors.onSurface) {
        buildAnnotatedString {
            val start = sentence.indexOf(link).coerceAtLeast(0)
            append(sentence.substring(0, start))
            withLink(
                LinkAnnotation.Clickable(
                    tag = PRIVACY_TAG,
                    styles = TextLinkStyles(SpanStyle(color = colors.onSurface, textDecoration = TextDecoration.Underline)),
                ) { open() },
            ) { append(link) }
            append(sentence.substring((start + link.length).coerceAtMost(sentence.length)))
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        modifier = modifier,
    )
}

private const val PRIVACY_TAG = "privacy"

/** Signed in: "Signed in as" the address, the backup status line, "Back up now", "Sign out", "Delete account…". */
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
            val tint = if (failed || state.cloud == CloudStatus.Deleted) colors.error else colors.onSurfaceVariant
            Text(status, style = PhoneType.dataMedium, color = tint, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton(
            stringResource(R.string.account_back_up_now),
            onClick = actions::onBackUpNow,
            enabled = !state.backingUp && state.cloud != CloudStatus.Syncing,
        )
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
 * "Delete account?" in place of "Delete account…": what is deleted and what stays, then how it is confirmed: Google
 * accounts sign in with Google again ("Delete"); email accounts get a link ("Email me a link") that completes the
 * deletion when it opens the app while this is open. A deletion link that opens the app while it is not opens it
 * with "Delete", which uses that link. Errors in words, a partial failure as it is. Inline rather than a dialog window, so
 * it sits with the rest of the screen; Back cancels it.
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
            val prompt = when {
                dialog.link != null -> R.string.account_delete_link_ready
                dialog.google -> R.string.account_delete_google_prompt
                dialog.linkSent -> R.string.account_delete_link_sent
                else -> R.string.account_delete_link_prompt
            }
            Text(
                stringResource(prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = if (dialog.linkSent || dialog.link != null) colors.onSurface else colors.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            dialog.problem?.let { ErrorLine(stringResource(it.message())) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = actions::onDeleteDismiss, enabled = !dialog.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(CoreR.string.action_cancel), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                }
                val confirm = when {
                    dialog.google || dialog.link != null -> stringResource(CoreR.string.action_delete)
                    dialog.linkSent -> stringResource(R.string.account_send_again)
                    else -> stringResource(R.string.account_delete_send_link)
                }
                TextButton(onClick = actions::onDeleteConfirm, enabled = !dialog.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(confirm, style = MaterialTheme.typography.labelLarge, color = colors.error)
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
    onDone: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.titleMedium,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(
            onNext = { focus.moveFocus(FocusDirection.Down) },
            onDone = { onDone() },
            onSend = { onDone() },
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
internal fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean) {
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
