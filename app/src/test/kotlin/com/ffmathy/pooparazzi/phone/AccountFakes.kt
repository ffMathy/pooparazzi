package com.ffmathy.pooparazzi.phone

import android.app.Activity
import com.ffmathy.pooparazzi.core.cloud.CloudAccount
import com.ffmathy.pooparazzi.core.cloud.CloudCursor
import com.ffmathy.pooparazzi.core.cloud.CloudError
import com.ffmathy.pooparazzi.core.cloud.CloudException
import com.ffmathy.pooparazzi.core.cloud.CloudPage
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.cloud.CloudSync
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.phone.account.AccountInput
import com.ffmathy.pooparazzi.phone.account.AccountResult
import com.ffmathy.pooparazzi.phone.account.AccountUser
import com.ffmathy.pooparazzi.phone.account.Accounts
import com.ffmathy.pooparazzi.phone.account.Families
import com.ffmathy.pooparazzi.phone.account.Family
import com.ffmathy.pooparazzi.phone.account.FamilyCache
import com.ffmathy.pooparazzi.phone.account.FamilyResult
import com.ffmathy.pooparazzi.phone.account.FamilyStore
import com.ffmathy.pooparazzi.phone.account.InviteCheck
import com.ffmathy.pooparazzi.phone.account.JoinResult
import com.ffmathy.pooparazzi.phone.account.LegacyState
import com.ffmathy.pooparazzi.phone.account.LinkPurpose
import com.ffmathy.pooparazzi.phone.account.SentLink
import com.ffmathy.pooparazzi.phone.account.StoredInvite
import com.ffmathy.pooparazzi.phone.account.StoredMember
import com.ffmathy.pooparazzi.phone.account.WelcomeStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [Accounts] without Firebase: each call is recorded and answers with the result queued for it (success by default),
 * applying the same input checks as the real one. A successful sign-in signs in [nextUser] (with the link's address).
 * Email links are strings starting with [LINK].
 */
class FakeAccounts(
    override val available: Boolean = true,
    override val googleAvailable: Boolean = true,
    signedIn: AccountUser? = null,
    override val hostingDomain: String? = "demo-pooparazzi.web.app",
) : Accounts {
    private val current = MutableStateFlow(signedIn)
    override val user: StateFlow<AccountUser?> = current.asStateFlow()
    override var sentLink: SentLink? = null

    val calls = mutableListOf<String>()
    var nextUser = AccountUser("uid-1", "parent@example.com", google = false)
    var googleUser = AccountUser("uid-g", "parent@gmail.com", google = true)

    /**
     * Results by call name ("google", "sendLink", "signInWithLink", "reauthGoogle", "sendReauthLink", "reauthLink",
     * "delete").
     */
    val results = mutableMapOf<String, AccountResult>()

    /** When set, calls wait for it before answering. */
    var gate: CompletableDeferred<Unit>? = null

    private suspend fun answer(name: String): AccountResult {
        calls += name
        gate?.await()
        return results[name] ?: AccountResult.Success
    }

    fun signInAs(user: AccountUser?) {
        current.value = user
    }

    override suspend fun signInWithGoogle(activity: Activity): AccountResult =
        answer("google").also { if (it == AccountResult.Success) current.value = googleUser }

    override suspend fun sendSignInLink(email: String): AccountResult {
        AccountInput.emailError(email)?.let { return AccountResult.Failed(it) }
        return answer("sendLink:${email.trim()}").let { results["sendLink"] ?: it }
            .also { if (it == AccountResult.Success) sentLink = SentLink(email.trim(), LinkPurpose.SIGN_IN) }
    }

    override fun isSignInLink(link: String): Boolean = link.startsWith(LINK)

    override suspend fun signInWithLink(email: String, link: String): AccountResult {
        AccountInput.emailError(email)?.let { return AccountResult.Failed(it) }
        return answer("signInWithLink:${email.trim()}").let { results["signInWithLink"] ?: it }.also {
            if (it == AccountResult.Success) {
                current.value = nextUser.copy(email = email.trim())
                sentLink = null
            }
        }
    }

    override suspend fun signOut() {
        calls += "signOut"
        current.value = null
    }

    override suspend fun reauthenticateWithGoogle(activity: Activity): AccountResult = answer("reauthGoogle")

    override suspend fun sendReauthenticationLink(): AccountResult = answer("sendReauthLink").also {
        if (it == AccountResult.Success) sentLink = SentLink(current.value!!.email, LinkPurpose.DELETE_ACCOUNT)
    }

    override suspend fun reauthenticateWithLink(link: String): AccountResult = answer("reauthLink")

    override suspend fun deleteAccount(): AccountResult =
        answer("delete").also { if (it == AccountResult.Success) current.value = null }

    companion object {
        const val LINK = "https://demo-pooparazzi.web.app/__/auth/links?link="
    }
}

/** [CloudSync] whose status the test sets, recording what was asked of it. */
class FakeCloudSync(status: CloudStatus = CloudStatus.Off) : CloudSync {
    val state = MutableStateFlow(status)
    override val status: StateFlow<CloudStatus> = state.asStateFlow()
    var syncs = 0
    var requests = 0
    var deletions = 0
    var deleteSucceeds = true

    /** When set, [syncNow] waits for it. */
    var gate: CompletableDeferred<Unit>? = null

    override fun start() = Unit

    override suspend fun syncNow() {
        syncs++
        gate?.await()
    }

    override fun requestSync() {
        requests++
    }

    override suspend fun deleteBackup(): Boolean {
        deletions++
        return deleteSucceeds
    }
}

/** [Families] answering what the test queues, recording each call. */
class FakeFamilies(account: CloudAccount? = null) : Families {
    val current = MutableStateFlow(account)
    override val account: StateFlow<CloudAccount?> = current.asStateFlow()
    val calls = mutableListOf<String>()

    var loadResult: FamilyResult<Family> = FamilyResult.Ok(Family("fam-1", emptyList()))
    var inviteResult: FamilyResult<String> = FamilyResult.Ok("https://demo-pooparazzi.web.app/join/AbCdEfGhIjKlMnOpQrStUv")
    var leaveResult: FamilyResult<Unit> = FamilyResult.Ok(Unit)
    var removeResult: FamilyResult<Unit> = FamilyResult.Ok(Unit)
    var check: InviteCheck = InviteCheck.Valid("partner@example.com")
    var joinResult: JoinResult = JoinResult.Joined
    var holds = mutableListOf<Boolean>()

    /** When set, [join] waits for it. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun load(): FamilyResult<Family> = loadResult.also { calls += "load" }
    override suspend fun createInvite(): FamilyResult<String> = inviteResult.also { calls += "invite" }
    override suspend fun leave(): FamilyResult<Unit> = leaveResult.also { calls += "leave" }
    override suspend fun remove(uid: String): FamilyResult<Unit> = removeResult.also { calls += "remove:$uid" }
    override suspend fun checkInvite(code: String): InviteCheck = check.also { calls += "check:$code" }

    override suspend fun join(code: String): JoinResult {
        calls += "join:$code"
        gate?.await()
        return joinResult
    }

    override fun refresh() {
        calls += "refresh"
    }

    override suspend fun holdForDeletion(held: Boolean) {
        holds += held
    }
}

/**
 * [FamilyStore] in memory, refusing what `firebase/firestore.rules` refuses where the app relies on it: a non-member
 * reads no member document (isMember is false), a used invite is gone, the family goes only with its last member.
 * Each call is recorded; [failWith] makes every call throw.
 */
class FakeFamilyStore : FamilyStore {
    val users = mutableMapOf<String, String>()
    val creators = mutableMapOf<String, String>()
    val members = mutableMapOf<String, MutableMap<String, StoredMember>>()
    val invites = mutableMapOf<String, StoredInvite>()
    val legacy = mutableMapOf<String, MutableList<Entry>>()
    val calls = mutableListOf<String>()
    var failWith: CloudError? = null
    private var clock = 0L

    private fun record(call: String) {
        calls += call
        failWith?.let { throw CloudException(it) }
    }

    fun seedFamily(familyId: String, creator: String, vararg others: Pair<String, String>, creatorEmail: String = "$creator@example.com") {
        creators[familyId] = creator
        val all = listOf(creator to creatorEmail) + others
        members[familyId] = all.associate { (uid, email) -> uid to StoredMember(uid, email, ++clock) }.toMutableMap()
        all.forEach { (uid, _) -> users[uid] = familyId }
    }

    override suspend fun familyOf(uid: String): String? = users[uid].also { record("familyOf:$uid") }

    override suspend fun isMember(familyId: String, uid: String): Boolean {
        record("isMember:$familyId:$uid")
        return members[familyId]?.containsKey(uid) == true
    }

    override suspend fun createFamily(familyId: String, uid: String, email: String, leaving: String?) {
        record("createFamily:$familyId:$uid:$email" + (leaving?.let { ":leaving=$it" } ?: ""))
        leaving?.let { members[it]?.remove(uid) }
        creators[familyId] = uid
        members[familyId] = mutableMapOf(uid to StoredMember(uid, email, ++clock))
        users[uid] = familyId
    }

    override suspend fun creatorOf(familyId: String): String = creators.getValue(familyId).also { record("creatorOf:$familyId") }

    override suspend fun members(familyId: String): List<StoredMember> {
        record("members:$familyId")
        return members[familyId]?.values?.toList() ?: throw CloudException(CloudError.OTHER)
    }

    override suspend fun createInvite(code: String, invite: StoredInvite) {
        record("createInvite:$code")
        invites[code] = invite
    }

    override suspend fun invite(code: String): StoredInvite? = invites[code].also { record("invite:$code") }

    override suspend fun join(code: String, familyId: String, uid: String, email: String) {
        record("join:$code:$familyId:$uid")
        val invite = invites[code]
        if (invite == null || invite.familyId != familyId) throw CloudException(CloudError.OTHER)
        members.getOrPut(familyId) { mutableMapOf() }[uid] = StoredMember(uid, email, ++clock)
        users[uid] = familyId
        invites.remove(code)
    }

    override suspend fun removeMember(familyId: String, uid: String) {
        record("removeMember:$familyId:$uid")
        members[familyId]?.remove(uid)
    }

    override suspend fun leave(familyId: String, uid: String) {
        record("leave:$familyId:$uid")
        val family = members[familyId] ?: return
        family.remove(uid)
        if (family.isEmpty()) {
            members.remove(familyId)
            creators.remove(familyId)
        }
    }

    override suspend fun deleteUser(uid: String) {
        record("deleteUser:$uid")
        users.remove(uid)
    }

    override suspend fun legacyEntries(uid: String, cursor: CloudCursor?, limit: Int): CloudPage<Entry> {
        record("legacyEntries:$uid")
        val all = legacy[uid].orEmpty()
        val start = cursor?.documentId?.let { id -> all.indexOfFirst { it.id == id } + 1 } ?: 0
        val page = all.drop(start).take(limit)
        return CloudPage(page, page.lastOrNull()?.let { CloudCursor(1, 0, it.id) }, page.size)
    }

    override suspend fun deleteLegacyEntries(uid: String) {
        record("deleteLegacyEntries:$uid")
        legacy.remove(uid)
    }
}

/** [FamilyCache] in memory. */
class MemoryFamilyCache : FamilyCache {
    val families = mutableMapOf<String, String>()
    val legacy = mutableMapOf<String, Pair<LegacyState, Long>>()
    val held = mutableSetOf<String>()
    override fun familyOf(uid: String): String? = families[uid]
    override fun setFamily(uid: String, familyId: String) {
        families[uid] = familyId
    }
    override fun legacy(uid: String): LegacyState = legacy[uid]?.first ?: LegacyState.UNKNOWN
    override fun legacyImportedAt(uid: String): Long = legacy[uid]?.second ?: 0
    override fun setLegacy(uid: String, state: LegacyState, atMillis: Long) {
        legacy[uid] = state to atMillis
    }
    override fun heldForDeletion(uid: String): Boolean = uid in held
    override fun setHeldForDeletion(uid: String, held: Boolean) {
        if (held) this.held += uid else this.held -= uid
    }
}

/** A [WelcomeStore] in memory. */
class MemoryWelcomeStore(override var seen: Boolean = false) : WelcomeStore
