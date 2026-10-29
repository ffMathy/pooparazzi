package com.ffmathy.pooparazzi.phone.ui.account

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.format.ClockFormatter
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.account.AccountError
import java.time.ZoneId

/** The words of an account error (BABIES_AND_BACKUP §3.3). */
@StringRes
internal fun AccountError.message(): Int = when (this) {
    AccountError.INVALID_EMAIL -> R.string.account_error_invalid_email
    AccountError.NO_CONNECTION -> R.string.account_error_no_connection
    AccountError.TIMED_OUT -> R.string.account_error_timed_out
    AccountError.TOO_MANY_ATTEMPTS -> R.string.account_error_too_many
    AccountError.EMAIL_QUOTA -> R.string.account_error_email_quota
    AccountError.LINK_INVALID -> R.string.account_error_link_invalid
    AccountError.NO_GOOGLE_ACCOUNT -> R.string.account_error_no_google
    AccountError.RECENT_LOGIN_REQUIRED -> R.string.account_error_recent_login
    AccountError.WRONG_ACCOUNT -> R.string.account_error_wrong_account
    AccountError.OTHER -> R.string.account_error_other
}

/** The words of a failed account deletion. */
@StringRes
internal fun DeleteProblem.message(): Int = when (this) {
    is DeleteProblem.SignIn -> error.message()
    DeleteProblem.Backup -> R.string.account_delete_backup_failed
    is DeleteProblem.Account -> R.string.account_delete_account_failed
}

/**
 * The backup status line of a signed-in account (§3.2): "Backing up…", "Backed up · 14:31", "Saved on phone · will
 * back up", "Couldn't back up · Tap to retry", "Backup deleted · Back up now to start again". [CloudStatus.Off] while signed in (before the first sync has said
 * anything) reads as saved on the phone.
 */
@Composable
internal fun backupStatusText(status: CloudStatus, clock: ClockFormatter, zone: ZoneId): String = when (status) {
    CloudStatus.Syncing -> stringResource(R.string.account_status_syncing)
    is CloudStatus.Synced -> stringResource(R.string.account_status_synced, clock.format(status.atMillis, zone))
    CloudStatus.Offline, CloudStatus.Off -> stringResource(R.string.account_status_offline)
    CloudStatus.Error -> stringResource(R.string.account_status_error)
    CloudStatus.Deleted -> stringResource(R.string.account_status_deleted)
}
