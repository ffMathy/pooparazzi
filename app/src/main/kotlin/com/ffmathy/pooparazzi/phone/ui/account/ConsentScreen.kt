package com.ffmathy.pooparazzi.phone.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.account.Accounts
import com.ffmathy.pooparazzi.phone.account.AppAccounts
import com.ffmathy.pooparazzi.phone.account.BackupConsent
import com.ffmathy.pooparazzi.phone.account.PrivacyPolicy
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The consent screen (BABIES_AND_BACKUP §3.6) as data.
 *
 * @property email the signed-in address, null when nobody is signed in (nothing to ask).
 * @property busy signing out ("Not now, sign out") is under way.
 */
@Immutable
data class ConsentUiState(val email: String?, val privacyUrl: String, val busy: Boolean = false)

/** What the parent can do on the consent screen. Defaults do nothing (previews). */
@Stable
interface ConsentActions {
    fun onAgree() {}
    fun onDecline() {}
    fun onOpenPrivacy() {}

    object None : ConsentActions
}

/**
 * The explicit consent before the first upload: shown while the signed-in account has not agreed on this phone
 * ([BackupConsent]). "I agree" records it, and the family and the backup start; "Not now, sign out" signs out, and
 * nothing is uploaded.
 */
class ConsentViewModel(
    private val accounts: Accounts,
    private val consent: BackupConsent,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val privacyUrl = PrivacyPolicy.url(accounts.hostingDomain)

    val state: StateFlow<ConsentUiState> = combine(accounts.user, busy) { user, b -> ConsentUiState(user?.email, privacyUrl, b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConsentUiState(accounts.user.value?.email, privacyUrl))

    val privacy: String get() = privacyUrl

    fun onAgree() {
        val user = accounts.user.value ?: return
        if (busy.value) return
        consent.agree(user.uid)
    }

    fun onDecline() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                accounts.signOut()
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ConsentViewModel(AppAccounts.accounts, AppAccounts.consent) }
        }
    }
}

/** The consent screen wired to its view model. Back is "Not now, sign out" (the app cannot go on without an answer). */
@Composable
fun ConsentRoute() {
    val viewModel: ConsentViewModel = viewModel(factory = ConsentViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val actions = remember(viewModel, uriHandler) {
        object : ConsentActions {
            override fun onAgree() = viewModel.onAgree()
            override fun onDecline() = viewModel.onDecline()
            override fun onOpenPrivacy() {
                runCatching { uriHandler.openUri(viewModel.privacy) }
            }
        }
    }
    BackHandler(enabled = !state.busy) { actions.onDecline() }
    ConsentScreen(state, actions)
}

/**
 * "Back up to Google Firebase?": what is uploaded, who sees it, where it is kept and how to change one's mind, the
 * privacy policy, then "I agree" and "Not now, sign out".
 */
@Composable
fun ConsentScreen(
    state: ConsentUiState,
    actions: ConsentActions,
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
            stringResource(R.string.consent_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.consent_intro, state.email.orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Point(R.string.consent_what_title, R.string.consent_what)
            Point(R.string.consent_who_title, R.string.consent_who)
            Point(R.string.consent_where_title, R.string.consent_where)
            Point(R.string.consent_withdraw_title, R.string.consent_withdraw)
        }
        PolicyLinkText(R.string.consent_policy, actions::onOpenPrivacy, Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.consent_agree), onClick = actions::onAgree, enabled = !state.busy)
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = actions::onDecline,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.consent_decline), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
        }
    }
}

/** One heading and its sentence. */
@Composable
private fun Point(title: Int, body: Int) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

@Preview(name = "Consent · Day", widthDp = 390, heightDp = 1000)
@Composable
private fun ConsentPreview() {
    NightLightTheme(night = false) {
        ConsentScreen(ConsentUiState("parent@example.com", PrivacyPolicy.REPOSITORY_URL), ConsentActions.None)
    }
}
