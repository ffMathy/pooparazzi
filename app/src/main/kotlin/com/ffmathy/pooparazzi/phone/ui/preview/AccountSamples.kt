package com.ffmathy.pooparazzi.phone.ui.preview

import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.account.AccountUser
import com.ffmathy.pooparazzi.phone.ui.account.AccountUiState

/** The Account and Welcome screens for previews and screenshots. */
object AccountSamples {
    val user = AccountUser(uid = "sample-uid", email = "parent@example.com", google = false)

    fun signedOut(googleAvailable: Boolean = true): AccountUiState = AccountUiState(
        available = true,
        googleAvailable = googleAvailable,
        user = null,
        cloud = CloudStatus.Off,
        zone = SampleData.ZONE,
    )

    /** "Check your email": a sign-in link went to the parent's address. */
    fun linkSent(): AccountUiState = signedOut().copy(email = user.email, linkSentTo = user.email)

    fun signedIn(cloud: CloudStatus = CloudStatus.Synced(SampleData.SYNCED_1431)): AccountUiState =
        signedOut().copy(user = user, cloud = cloud)
}
