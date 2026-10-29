package com.ffmathy.pooparazzi.phone.ui.family

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.account.FamilyError
import com.ffmathy.pooparazzi.phone.account.FamilyMember
import com.ffmathy.pooparazzi.phone.ui.components.GroupRow
import com.ffmathy.pooparazzi.phone.ui.edit.OutlinePill
import com.ffmathy.pooparazzi.phone.ui.history.HistoryRowDefaults
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * Settings › Family (BABIES_AND_BACKUP §3.4), under Account while signed in: one grouped row per member (email; "You",
 * and who started the family; "Remove" on the others' rows for the family's creator), "Invite someone", and "Leave
 * family" with two or more members. Leaving and removing ask first.
 */
@Composable
internal fun FamilySection(state: FamilyUiState, actions: FamilyActions) {
    val colors = MaterialTheme.colorScheme
    val family = state.family
    when {
        state.waiting -> NoteRow(stringResource(R.string.family_waiting))
        family == null && state.loadError != null -> GroupRow(first = true, last = true) {
            Text(
                stringResource(R.string.family_error),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = actions::onRetry)
                    .padding(16.dp),
            )
        }
        family == null -> NoteRow(stringResource(R.string.family_loading))
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(HistoryRowDefaults.Gap)) {
                family.members.forEachIndexed { index, member ->
                    GroupRow(first = index == 0, last = index == family.members.lastIndex) {
                        MemberRow(member, canRemove = family.youAreCreator && !member.you, enabled = !state.busy, actions)
                    }
                }
            }
            if (family.members.size <= 1) {
                Text(
                    stringResource(R.string.family_alone),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinePill(stringResource(R.string.family_invite), onClick = actions::onInvite, icon = XIcons.plus, enabled = !state.busy)
                Spacer(Modifier.weight(1f))
                if (family.members.size >= 2) {
                    TextButton(onClick = actions::onLeave, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.family_leave), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                    }
                }
            }
        }
    }
    state.actionError?.let { error ->
        Text(
            stringResource(error.message()),
            style = MaterialTheme.typography.bodySmall,
            color = colors.error,
            modifier = Modifier
                .padding(top = 8.dp, start = 4.dp, end = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    state.confirm?.let { FamilyConfirmDialog(it, busy = state.busy, actions) }
}

/** A member: their email, and under it "You" and/or "Started the family"; "Remove" for the creator. */
@Composable
private fun MemberRow(member: FamilyMember, canRemove: Boolean, enabled: Boolean, actions: FamilyActions) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(member.email, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            val role = when {
                member.you && member.creator -> stringResource(R.string.family_you_creator)
                member.you -> stringResource(R.string.family_you)
                member.creator -> stringResource(R.string.family_creator)
                else -> null
            }
            role?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
        }
        if (canRemove) {
            Spacer(Modifier.width(8.dp))
            val description = stringResource(R.string.family_remove_cd, member.email)
            TextButton(
                onClick = { actions.onRemove(member) },
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
            ) {
                Text(stringResource(R.string.family_remove), style = MaterialTheme.typography.labelLarge, color = colors.error)
            }
        }
    }
}

@Composable
private fun NoteRow(text: String) {
    GroupRow(first = true, last = true) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

/** "Leave the family?" or "Remove <email>?", with Cancel and the action. */
@Composable
private fun FamilyConfirmDialog(confirm: FamilyConfirm, busy: Boolean, actions: FamilyActions) {
    val colors = MaterialTheme.colorScheme
    val (title, body, action) = when (confirm) {
        FamilyConfirm.Leave -> Triple(
            stringResource(R.string.family_leave_title),
            stringResource(R.string.family_leave_body),
            stringResource(R.string.family_leave_confirm),
        )
        is FamilyConfirm.Remove -> Triple(
            stringResource(R.string.family_remove_title, confirm.member.email),
            stringResource(R.string.family_remove_body),
            stringResource(R.string.family_remove),
        )
    }
    AlertDialog(
        onDismissRequest = actions::onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = actions::onConfirm, enabled = !busy) {
                Text(action, color = colors.error)
            }
        },
        dismissButton = {
            TextButton(onClick = actions::onDismiss, enabled = !busy) {
                Text(stringResource(CoreR.string.action_cancel), color = colors.onSurface)
            }
        },
        containerColor = colors.surfaceContainerHigh,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
    )
}

/** The words of a failed family action. */
internal fun FamilyError.message(): Int = when (this) {
    FamilyError.NO_CONNECTION -> R.string.account_error_no_connection
    FamilyError.NOT_READY -> R.string.family_waiting
    FamilyError.INVITE_REFUSED -> R.string.family_error_invite_refused
    FamilyError.OTHER -> R.string.account_error_other
}
