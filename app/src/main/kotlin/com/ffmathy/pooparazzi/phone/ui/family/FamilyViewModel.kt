package com.ffmathy.pooparazzi.phone.ui.family

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.account.AppAccounts
import com.ffmathy.pooparazzi.phone.account.Families
import com.ffmathy.pooparazzi.phone.account.Family
import com.ffmathy.pooparazzi.phone.account.FamilyError
import com.ffmathy.pooparazzi.phone.account.FamilyMember
import com.ffmathy.pooparazzi.phone.account.FamilyResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A question Settings › Family asks before it acts (BABIES_AND_BACKUP §3.4). */
sealed interface FamilyConfirm {
    /** "Leave the family?" */
    data object Leave : FamilyConfirm

    /** "Remove <email>?" */
    data class Remove(val member: FamilyMember) : FamilyConfirm
}

/**
 * Settings › Family as data.
 *
 * @property signedIn the section shows only while an account is signed in.
 * @property waiting the account's family is not known yet ("Setting up your family…").
 * @property family the members, once loaded.
 * @property loadError loading failed: "Couldn't load the family. Tap to try again."
 * @property busy an invite, leave or remove is running.
 * @property actionError the last action failed, in words.
 */
@Immutable
data class FamilyUiState(
    val signedIn: Boolean = false,
    val waiting: Boolean = false,
    val family: Family? = null,
    val loadError: FamilyError? = null,
    val busy: Boolean = false,
    val confirm: FamilyConfirm? = null,
    val actionError: FamilyError? = null,
) {
    /** Loading for the first time. */
    val loading: Boolean get() = signedIn && !waiting && family == null && loadError == null
}

/** What the parent can do in Settings › Family. Defaults do nothing (previews). */
@Stable
interface FamilyActions {
    /** "Invite someone": makes an invite and opens the share sheet with its link. */
    fun onInvite() {}
    fun onLeave() {}
    fun onRemove(member: FamilyMember) {}
    fun onConfirm() {}
    fun onDismiss() {}

    /** "Couldn't load the family. Tap to try again." */
    fun onRetry() {}

    object None : FamilyActions
}

/**
 * Settings › Family (BABIES_AND_BACKUP §3.4): the members of the signed-in account's family, inviting (an invite link
 * for the share sheet, [shares]), leaving (after a confirmation; the account gets a new family of its own) and, for the
 * family's creator, removing members (after a confirmation). Reloads whenever the account or its family changes.
 *
 * @param workScope runs leaving and removing, which must finish even if Settings closes.
 */
class FamilyViewModel(
    private val families: Families,
    private val workScope: CoroutineScope,
) : ViewModel(), FamilyActions {
    private val ui = MutableStateFlow(FamilyUiState(signedIn = families.account.value != null))
    val state: StateFlow<FamilyUiState> = ui.asStateFlow()

    private val shareLinks = Channel<String>(Channel.BUFFERED)

    /** Invite links to hand to the share sheet ([inviteShareIntent]), one per "Invite someone". */
    val shares: Flow<String> = shareLinks.receiveAsFlow()

    init {
        viewModelScope.launch {
            families.account.map { it?.uid to it?.familyId }.distinctUntilChanged().collectLatest { (uid, familyId) ->
                when {
                    uid == null -> ui.value = FamilyUiState()
                    familyId == null -> ui.update { FamilyUiState(signedIn = true, waiting = true, confirm = it.confirm) }
                    else -> {
                        ui.update { it.copy(signedIn = true, waiting = false, loadError = null, family = it.family?.takeIf { f -> f.id == familyId }) }
                        load()
                    }
                }
            }
        }
    }

    private suspend fun load() {
        when (val result = families.load()) {
            is FamilyResult.Ok -> ui.update { it.copy(family = result.value, loadError = null) }
            is FamilyResult.Failed -> ui.update {
                // NOT_READY: the family changed (or the account signed out) meanwhile; the new one loads by itself.
                if (result.error == FamilyError.NOT_READY) it else it.copy(loadError = result.error)
            }
        }
    }

    override fun onRetry() {
        ui.update { it.copy(loadError = null) }
        viewModelScope.launch { load() }
    }

    override fun onInvite() {
        if (ui.value.busy) return
        ui.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            val result = families.createInvite()
            ui.update { it.copy(busy = false, actionError = (result as? FamilyResult.Failed)?.error) }
            if (result is FamilyResult.Ok) shareLinks.send(result.value)
        }
    }

    override fun onLeave() = ui.update { it.copy(confirm = FamilyConfirm.Leave, actionError = null) }

    override fun onRemove(member: FamilyMember) {
        if (ui.value.family?.youAreCreator != true || member.you) return
        ui.update { it.copy(confirm = FamilyConfirm.Remove(member), actionError = null) }
    }

    override fun onDismiss() = ui.update { if (it.busy) it else it.copy(confirm = null) }

    override fun onConfirm() {
        val confirm = ui.value.confirm ?: return
        if (ui.value.busy) return
        ui.update { it.copy(busy = true, actionError = null) }
        workScope.launch {
            val result = when (confirm) {
                FamilyConfirm.Leave -> families.leave()
                is FamilyConfirm.Remove -> families.remove(confirm.member.uid)
            }
            val error = (result as? FamilyResult.Failed)?.error
            ui.update { it.copy(busy = false, confirm = null, actionError = error) }
            // Leaving publishes the new family, which reloads by itself; a removal changes only the members.
            if (error == null && confirm is FamilyConfirm.Remove) load()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { FamilyViewModel(AppAccounts.families, com.ffmathy.pooparazzi.core.Core.graph.scope) }
        }
    }
}

/** The text of an invite: "Join my family on Pooparazzi to share our baby log: <link> (works once, for 7 days)". */
fun inviteShareText(context: Context, link: String): String = context.getString(R.string.family_invite_text, link)

/** The Android share sheet with an invite [link] and its text. */
fun inviteShareIntent(context: Context, link: String): Intent {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, inviteShareText(context, link))
    return Intent.createChooser(send, context.getString(R.string.family_invite_chooser))
}
