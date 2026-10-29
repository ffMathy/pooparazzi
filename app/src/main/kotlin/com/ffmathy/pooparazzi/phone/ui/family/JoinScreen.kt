package com.ffmathy.pooparazzi.phone.ui.family

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.account.AppAccounts
import com.ffmathy.pooparazzi.phone.account.Families
import com.ffmathy.pooparazzi.phone.account.FamilyError
import com.ffmathy.pooparazzi.phone.account.InviteCheck
import com.ffmathy.pooparazzi.phone.account.JoinResult
import com.ffmathy.pooparazzi.phone.ui.components.XTopBar
import com.ffmathy.pooparazzi.phone.ui.theme.LocalNightLight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why an invite cannot be used, in words (BABIES_AND_BACKUP §3.5). */
enum class JoinRefusal { EXPIRED, INVALID, ALREADY_MEMBER, NO_CONNECTION, OTHER }

/** The join confirmation as data. */
@Immutable
sealed interface JoinUiState {
    /** Reading the invite. */
    data object Loading : JoinUiState

    /** "Join <inviterEmail>'s family?" with Join and Not now. */
    data class Confirm(val inviterEmail: String?, val busy: Boolean = false, val error: FamilyError? = null) : JoinUiState

    /** "You're in the family." */
    data object Joined : JoinUiState

    /** The invite cannot be used, and why. */
    data class Refused(val refusal: JoinRefusal) : JoinUiState
}

/** What the parent can do on the join confirmation. */
@Stable
interface JoinActions {
    fun onJoin() {}

    /** "Not now", "Done", the close button and Back: the invite is put aside. */
    fun onClose() {}

    /** "No connection. Try again.": reads the invite again. */
    fun onRetry() {}

    object None : JoinActions
}

/**
 * Opening an invite link (BABIES_AND_BACKUP §3.5), signed in: reads the invite [code], then asks to join; joining is
 * one batch (the membership, the users document, the invite deleted), and the backup then merges the phone's log and
 * babies into the family.
 *
 * @param workScope runs the join, which must finish even if the screen closes.
 */
class JoinViewModel(
    private val code: String,
    private val families: Families,
    private val workScope: CoroutineScope,
) : ViewModel() {
    private val ui = MutableStateFlow<JoinUiState>(JoinUiState.Loading)
    val state: StateFlow<JoinUiState> = ui.asStateFlow()

    init {
        check()
    }

    private fun check() {
        ui.value = JoinUiState.Loading
        viewModelScope.launch { ui.value = families.checkInvite(code).toUiState() }
    }

    fun onRetry() {
        val current = ui.value
        if (current is JoinUiState.Refused && current.refusal == JoinRefusal.NO_CONNECTION) check()
    }

    fun onJoin() {
        val confirm = ui.value as? JoinUiState.Confirm ?: return
        if (confirm.busy) return
        ui.value = confirm.copy(busy = true, error = null)
        workScope.launch {
            ui.value = when (val result = families.join(code)) {
                JoinResult.Joined -> JoinUiState.Joined
                is JoinResult.Refused -> when (val check = result.check) {
                    // Offline or refused for another reason while asking: the question stays, with the error.
                    is InviteCheck.Failed -> confirm.copy(busy = false, error = check.error)
                    else -> check.toUiState()
                }
            }
        }
    }

    private fun InviteCheck.toUiState(): JoinUiState = when (this) {
        is InviteCheck.Valid -> JoinUiState.Confirm(inviterEmail)
        InviteCheck.AlreadyMember -> JoinUiState.Refused(JoinRefusal.ALREADY_MEMBER)
        InviteCheck.Expired -> JoinUiState.Refused(JoinRefusal.EXPIRED)
        InviteCheck.Invalid -> JoinUiState.Refused(JoinRefusal.INVALID)
        is InviteCheck.Failed -> JoinUiState.Refused(
            if (error == FamilyError.NO_CONNECTION) JoinRefusal.NO_CONNECTION else JoinRefusal.OTHER,
        )
    }

    companion object {
        fun factory(code: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { JoinViewModel(code, AppAccounts.families, Core.graph.scope) }
        }
    }
}

/** The join confirmation for invite [code], wired to its view model; [onClose] when it is done or put aside. */
@Composable
fun JoinRoute(code: String, onClose: () -> Unit) {
    val viewModel: JoinViewModel = viewModel(key = "join-$code", factory = JoinViewModel.factory(code))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    val actions = remember(viewModel) {
        object : JoinActions {
            override fun onJoin() = viewModel.onJoin()
            override fun onClose() = close()
            override fun onRetry() = viewModel.onRetry()
        }
    }
    JoinScreen(state, actions)
}

/**
 * "Join <inviter>'s family?" (BABIES_AND_BACKUP §3.5): what joining does (your babies and log are added to the
 * family's and shared; the first babies become one), Join and Not now. Afterwards "You're in the family", or why the
 * invite cannot be used.
 */
@Composable
fun JoinScreen(
    state: JoinUiState,
    actions: JoinActions,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val colors = MaterialTheme.colorScheme
    val bottomInset = with(LocalDensity.current) { windowInsets.getBottom(this).toDp() }
    val busy = (state as? JoinUiState.Confirm)?.busy == true
    BackHandler(enabled = !busy, onBack = actions::onClose)
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        XTopBar(
            title = stringResource(R.string.join_screen_title),
            navigationIcon = XIcons.close,
            navigationLabel = stringResource(R.string.cd_close),
            onNavigate = { if (!busy) actions.onClose() },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state) {
                JoinUiState.Loading -> Body(stringResource(R.string.join_loading))
                is JoinUiState.Confirm -> {
                    val title = state.inviterEmail?.let { stringResource(R.string.join_title, it) }
                        ?: stringResource(R.string.join_title_unknown)
                    Title(title)
                    Body(stringResource(R.string.join_body))
                    Body(stringResource(R.string.join_merge))
                    state.error?.let {
                        Text(
                            stringResource(it.message()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Primary(stringResource(R.string.join_accept), onClick = actions::onJoin, enabled = !state.busy)
                    TextButton(
                        onClick = actions::onClose,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.join_not_now), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                    }
                }
                JoinUiState.Joined -> {
                    Title(stringResource(R.string.join_joined))
                    Primary(stringResource(R.string.join_done), onClick = actions::onClose, enabled = true)
                }
                is JoinUiState.Refused -> {
                    val words = when (state.refusal) {
                        JoinRefusal.EXPIRED -> R.string.join_expired
                        JoinRefusal.INVALID -> R.string.join_invalid
                        JoinRefusal.ALREADY_MEMBER -> R.string.join_already_member
                        JoinRefusal.NO_CONNECTION -> R.string.account_error_no_connection
                        JoinRefusal.OTHER -> R.string.account_error_other
                    }
                    Title(stringResource(words))
                    if (state.refusal == JoinRefusal.NO_CONNECTION) {
                        Primary(stringResource(R.string.join_try_again), onClick = actions::onRetry, enabled = true)
                    }
                    TextButton(onClick = actions::onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.join_done), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        },
    )
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A full-width 56 dp filled button (the Account screen's). */
@Composable
private fun Primary(text: String, onClick: () -> Unit, enabled: Boolean) {
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
