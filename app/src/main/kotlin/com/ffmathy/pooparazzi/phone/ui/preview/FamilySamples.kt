package com.ffmathy.pooparazzi.phone.ui.preview

import com.ffmathy.pooparazzi.phone.account.Family
import com.ffmathy.pooparazzi.phone.account.FamilyMember
import com.ffmathy.pooparazzi.phone.ui.family.FamilyUiState
import com.ffmathy.pooparazzi.phone.ui.family.JoinUiState

/** Settings › Family and the join confirmation for previews and screenshots (BABIES_AND_BACKUP §3.4–3.5). */
object FamilySamples {
    /** The signed-in parent started the family; a second parent and a grandparent joined. */
    val family = Family(
        id = "sample-family",
        members = listOf(
            FamilyMember("sample-uid", AccountSamples.user.email, you = true, creator = true),
            FamilyMember("sample-uid-2", "partner@example.com", you = false, creator = false),
            FamilyMember("sample-uid-3", "grandma@example.com", you = false, creator = false),
        ),
    )

    fun state(family: Family? = this.family): FamilyUiState = FamilyUiState(signedIn = true, family = family)

    /** Only the signed-in parent so far. */
    fun alone(): FamilyUiState = state(family.copy(members = family.members.take(1)))

    val join: JoinUiState = JoinUiState.Confirm(inviterEmail = "partner@example.com")
}
