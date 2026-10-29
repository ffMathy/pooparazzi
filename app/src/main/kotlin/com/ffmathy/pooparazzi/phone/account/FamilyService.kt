package com.ffmathy.pooparazzi.phone.account

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.ffmathy.pooparazzi.core.cloud.CloudAccount
import com.ffmathy.pooparazzi.core.cloud.CloudCursor
import com.ffmathy.pooparazzi.core.cloud.CloudError
import com.ffmathy.pooparazzi.core.cloud.CloudException
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.cloud.CloudStore
import com.ffmathy.pooparazzi.core.model.Entry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.Base64

/** A member of the family, as Settings › Family lists them (BABIES_AND_BACKUP §3.4). */
data class FamilyMember(val uid: String, val email: String, val you: Boolean, val creator: Boolean)

/** The signed-in account's family: its members (you first, then in the order they joined). */
data class Family(val id: String, val members: List<FamilyMember>) {
    /** The signed-in account made the family: it may remove the others. */
    val youAreCreator: Boolean get() = members.any { it.you && it.creator }
}

/** Why a family action failed. */
enum class FamilyError {
    /** "No connection. Try again." */
    NO_CONNECTION,

    /** Signed out, or the account's family is not known yet: nothing to act on. */
    NOT_READY,

    /** "Something went wrong. Try again." */
    OTHER,
}

/** The outcome of a family action. */
sealed interface FamilyResult<out T> {
    data class Ok<T>(val value: T) : FamilyResult<T>
    data class Failed(val error: FamilyError) : FamilyResult<Nothing>
}

/** What an invite link turned out to be (BABIES_AND_BACKUP §3.5). */
sealed interface InviteCheck {
    /** It may be used: "Join <inviterEmail>'s family?" ([inviterEmail] null for an invite without one). */
    data class Valid(val inviterEmail: String?) : InviteCheck

    /** "You're already in this family." */
    data object AlreadyMember : InviteCheck

    /** "This invite has expired or was already used. Ask for a new one." (A used invite is deleted: the same thing.) */
    data object Expired : InviteCheck

    /** "This invite link isn't valid." (Not a code at all.) */
    data object Invalid : InviteCheck

    data class Failed(val error: FamilyError) : InviteCheck
}

/** The outcome of joining with an invite. */
sealed interface JoinResult {
    data object Joined : JoinResult

    /** The invite cannot be used ([InviteCheck.AlreadyMember], [InviteCheck.Expired] …) and nothing was changed. */
    data class Refused(val check: InviteCheck) : JoinResult
}

/**
 * The signed-in account's family (BABIES_AND_BACKUP §3.4–3.5, §4.1): who is in it, inviting, joining, leaving and
 * removing. [FamilyService] in builds with a Firebase configuration, [NoFamilies] otherwise. Nothing here throws.
 */
interface Families {
    /** The account with its family as the backup follows it (the family null while it is not known yet). */
    val account: StateFlow<CloudAccount?>

    /** The family and its members. */
    suspend fun load(): FamilyResult<Family>

    /** Creates an invite (single use, 7 days) and returns its link, `https://<project-id>.web.app/join/<code>`. */
    suspend fun createInvite(): FamilyResult<String>

    /** Leaves the family for a new, empty one of your own (one batch); the phone then uploads its log there. */
    suspend fun leave(): FamilyResult<Unit>

    /** Removes member [uid] from the family; the family's creator only. */
    suspend fun remove(uid: String): FamilyResult<Unit>

    /** What the invite [code] is, before joining. */
    suspend fun checkInvite(code: String): InviteCheck

    /** Joins the family of invite [code]: the membership, the users document and the invite deleted, in one batch. */
    suspend fun join(code: String): JoinResult

    /** Reads the account's family again (the app in the foreground): a removed member gets a family of their own. */
    fun refresh()

    /**
     * Holds the account's family as it is while its backup is deleted for deleting the account ([held] true): no new
     * family is made for it, also after a restart, until it is released ([held] false: "Back up now", or the deletion
     * of the backup failed) or someone signs out. Releasing resolves the family again (making a new one when the
     * account is not in its family any more).
     */
    suspend fun holdForDeletion(held: Boolean)
}

/** Builds without a Firebase configuration. */
object NoFamilies : Families {
    private val nobody = MutableStateFlow<CloudAccount?>(null).asStateFlow()
    override val account: StateFlow<CloudAccount?> = nobody
    override suspend fun load(): FamilyResult<Family> = FamilyResult.Failed(FamilyError.NOT_READY)
    override suspend fun createInvite(): FamilyResult<String> = FamilyResult.Failed(FamilyError.NOT_READY)
    override suspend fun leave(): FamilyResult<Unit> = FamilyResult.Failed(FamilyError.NOT_READY)
    override suspend fun remove(uid: String): FamilyResult<Unit> = FamilyResult.Failed(FamilyError.NOT_READY)
    override suspend fun checkInvite(code: String): InviteCheck = InviteCheck.Failed(FamilyError.NOT_READY)
    override suspend fun join(code: String): JoinResult = JoinResult.Refused(InviteCheck.Failed(FamilyError.NOT_READY))
    override fun refresh() = Unit
    override suspend fun holdForDeletion(held: Boolean) = Unit
}

/** How far the backup from before families (`users/{uid}/entries`) has been moved into the family, per account. */
enum class LegacyState {
    /** Not looked at yet on this phone. */
    UNKNOWN,

    /** Its entries are in this phone's log, to be uploaded to the family; the old documents still exist. */
    IMPORTED,

    /** Nothing (left) to move. */
    DONE,
}

/** What the phone remembers of its accounts' families, so a restart need not wait for the server ([FamilyPrefs]). */
interface FamilyCache {
    fun familyOf(uid: String): String?
    fun setFamily(uid: String, familyId: String)
    fun legacy(uid: String): LegacyState

    /** When the legacy entries were imported (wall clock), for [LegacyState.IMPORTED]. */
    fun legacyImportedAt(uid: String): Long
    fun setLegacy(uid: String, state: LegacyState, atMillis: Long = 0)
    fun heldForDeletion(uid: String): Boolean
    fun setHeldForDeletion(uid: String, held: Boolean)
}

/**
 * The family layer of the phone's accounts (BABIES_AND_BACKUP §4.1): resolves the signed-in account's family and
 * hands it to the backup ([account]), and carries out the family actions ([Families]).
 *
 * **Resolution.** On sign-in (and at start, and on [refresh]) it reads `users/{uid}`. Without one, it creates a family
 * of the account's own in one batch (the family, the member document, the users document). A users document naming a
 * family the account is no longer a member of (it was removed) counts as leaving: a new family of its own. The family
 * is cached per account on this phone and published at once on the next start, so the status line need not wait for
 * the server; the resolution then confirms or corrects it. A failed resolution (offline) is retried every
 * [retryMillis].
 *
 * **The backup from before families** (`users/{uid}/entries`): the first resolution on this phone pulls its entries
 * into the phone's log (merged like any pull, [start]'s `importLegacy`) before the family is published, so the
 * engine's first sync for the family uploads them along with everything else; once a sync of the family has finished
 * after that, the old documents are deleted.
 *
 * Changing family (joining, leaving, being removed) only publishes the new family id: the backup engine treats it like
 * another account, flags everything on the phone for upload and pulls the new family from its start.
 *
 * @param hostingDomain the project's website, for the invite links; null leaves inviting off.
 */
class FamilyService(
    private val store: FamilyStore,
    private val user: StateFlow<AccountUser?>,
    private val cache: FamilyCache,
    private val scope: CoroutineScope,
    private val hostingDomain: String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val randomBytes: (Int) -> ByteArray = ::secureRandomBytes,
    private val retryMillis: Long = RETRY_MILLIS,
) : Families {
    private val published = MutableStateFlow(user.value?.let(::cachedAccount))
    override val account: StateFlow<CloudAccount?> = published.asStateFlow()

    /** One resolution or family change at a time. */
    private val mutex = Mutex()
    private val hooks = CompletableDeferred<Hooks>()

    /** What only the core graph has, handed over in [start]. */
    private class Hooks(val status: StateFlow<CloudStatus>, val importLegacy: suspend (List<Entry>) -> Unit)

    init {
        scope.launch { followUser() }
    }

    /**
     * Starts resolving (and moving the backup from before families) once the core graph exists: [status] is the
     * backup's, [importLegacy] merges entries into the phone's log as if pulled from the backend.
     */
    fun start(status: StateFlow<CloudStatus>, importLegacy: suspend (List<Entry>) -> Unit) {
        if (hooks.complete(Hooks(status, importLegacy))) scope.launch { cleanUpLegacy(status) }
    }

    override fun refresh() {
        val signedIn = user.value ?: return
        scope.launch { resolveQuietly(signedIn) }
    }

    override suspend fun holdForDeletion(held: Boolean) {
        val signedIn = user.value ?: return
        cache.setHeldForDeletion(signedIn.uid, held)
        if (!held) resolveQuietly(signedIn)
    }

    override suspend fun load(): FamilyResult<Family> = act { signedIn, familyId ->
        val creator = store.creatorOf(familyId)
        val members = store.members(familyId)
            .sortedWith(compareBy<StoredMember>({ it.uid != signedIn.uid }, { it.joinedAtMillis }, { it.email }))
            .map { FamilyMember(it.uid, it.email, you = it.uid == signedIn.uid, creator = it.uid == creator) }
        Family(familyId, members)
    }

    override suspend fun createInvite(): FamilyResult<String> {
        val domain = hostingDomain ?: return FamilyResult.Failed(FamilyError.OTHER)
        return act { signedIn, familyId ->
            val code = newCode()
            val invite = StoredInvite(familyId, signedIn.uid, now() + INVITE_LIFETIME_MILLIS, signedIn.email.ifBlank { null })
            store.createInvite(code, invite)
            inviteLink(domain, code)
        }
    }

    override suspend fun leave(): FamilyResult<Unit> = act(locked = true) { signedIn, familyId ->
        val others = store.members(familyId).any { it.uid != signedIn.uid }
        // Alone, there is nobody to leave: the family already is the account's own.
        if (others) {
            val fresh = newFamilyId()
            store.createFamily(fresh, signedIn.uid, signedIn.email, leaving = familyId)
            switchTo(signedIn, fresh)
        }
    }

    override suspend fun remove(uid: String): FamilyResult<Unit> = act { signedIn, familyId ->
        if (uid == signedIn.uid || store.creatorOf(familyId) != signedIn.uid) {
            throw CloudException(CloudError.OTHER, IllegalStateException("Only the family's creator removes others"))
        }
        store.removeMember(familyId, uid)
    }

    override suspend fun checkInvite(code: String): InviteCheck {
        if (!isInviteCode(code)) return InviteCheck.Invalid
        val signedIn = user.value ?: return InviteCheck.Failed(FamilyError.NOT_READY)
        return try {
            mutex.withLock {
                resolveLocked(signedIn)
                inspect(code).first
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the invite", e)
            InviteCheck.Failed(e.toFamilyError())
        }
    }

    override suspend fun join(code: String): JoinResult {
        if (!isInviteCode(code)) return JoinResult.Refused(InviteCheck.Invalid)
        val signedIn = user.value ?: return JoinResult.Refused(InviteCheck.Failed(FamilyError.NOT_READY))
        var old: String? = null
        val result = try {
            mutex.withLock {
                resolveLocked(signedIn)
                val (check, invite) = inspect(code)
                when {
                    check is InviteCheck.Valid && invite != null -> {
                        try {
                            store.join(code, invite.familyId, signedIn.uid, signedIn.email)
                        } catch (e: CloudException) {
                            // Refused: most likely used or expired just now.
                            if (e.error == CloudError.OTHER && inspect(code).first !is InviteCheck.Valid) {
                                return@withLock JoinResult.Refused(InviteCheck.Expired)
                            }
                            throw e
                        }
                        old = published.value?.familyId?.takeIf { it != invite.familyId }
                        cache.setHeldForDeletion(signedIn.uid, false)
                        switchTo(signedIn, invite.familyId)
                        JoinResult.Joined
                    }
                    else -> JoinResult.Refused(check)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not join the family", e)
            return JoinResult.Refused(InviteCheck.Failed(e.toFamilyError()))
        }
        // The old family loses its member; when it was the last, its data goes with it (as when leaving). The phone's
        // log goes up to the new family, so nothing is lost. Not waited for, and only logged: the join stands.
        old?.let { previous ->
            scope.launch {
                try {
                    store.leave(previous, signedIn.uid)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not leave the old family", e)
                }
            }
        }
        return result
    }

    /** Follows the account: its cached family at once, then the resolved one (retried until it resolves). */
    private suspend fun followUser() {
        var previous: String? = null
        user.collectLatest { signedIn ->
            // Signing out (or in to another account) ends any hold of the account before.
            previous?.takeIf { it != signedIn?.uid }?.let { cache.setHeldForDeletion(it, false) }
            previous = signedIn?.uid
            published.value = signedIn?.let(::cachedAccount)
            if (signedIn == null) return@collectLatest
            hooks.await()
            while (!resolveQuietly(signedIn)) delay(retryMillis)
        }
    }

    /** The account with its family as this phone last knew it (none before its old backup was looked at). */
    private fun cachedAccount(signedIn: AccountUser): CloudAccount {
        val family = cache.familyOf(signedIn.uid)?.takeIf { cache.legacy(signedIn.uid) != LegacyState.UNKNOWN }
        return CloudAccount(signedIn.uid, signedIn.email, family)
    }

    /** [resolveLocked] under the lock; true when it resolved (or there was nothing to resolve). */
    private suspend fun resolveQuietly(signedIn: AccountUser): Boolean = try {
        hooks.await()
        mutex.withLock { resolveLocked(signedIn) }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not resolve the family", e)
        false
    }

    /** Reads (or creates) the account's family and publishes it. Call with [mutex] held. */
    private suspend fun resolveLocked(signedIn: AccountUser) {
        if (user.value?.uid != signedIn.uid) return
        // Its backup was deleted for deleting the account: no new family until released.
        if (cache.heldForDeletion(signedIn.uid)) return
        var family = store.familyOf(signedIn.uid)
        // Removed by the family's creator (or the family is gone): like leaving, a family of its own.
        if (family != null && !store.isMember(family, signedIn.uid)) family = null
        if (family == null) {
            family = newFamilyId()
            store.createFamily(family, signedIn.uid, signedIn.email)
        }
        if (cache.legacy(signedIn.uid) == LegacyState.UNKNOWN) importLegacy(signedIn.uid)
        switchTo(signedIn, family)
    }

    /** Caches and publishes [familyId] as [signedIn]'s family (the backup follows). */
    private fun switchTo(signedIn: AccountUser, familyId: String) {
        cache.setFamily(signedIn.uid, familyId)
        if (user.value?.uid == signedIn.uid) published.value = CloudAccount(signedIn.uid, signedIn.email, familyId)
    }

    /** Pulls the backup from before families into the phone's log (before the family is published). */
    private suspend fun importLegacy(uid: String) {
        val import = hooks.await().importLegacy
        var cursor: CloudCursor? = null
        var found = false
        var pages = 0
        while (pages++ < MAX_LEGACY_PAGES) {
            val page = store.legacyEntries(uid, cursor, CloudStore.PAGE_SIZE)
            if (page.items.isNotEmpty()) {
                import(page.items)
                found = true
            }
            val last = page.last
            if (last == null || last == cursor || page.documents < CloudStore.PAGE_SIZE) break
            cursor = last
        }
        cache.setLegacy(uid, if (found) LegacyState.IMPORTED else LegacyState.DONE, now())
    }

    /**
     * Deletes the backup from before families once a sync of the account's family has finished after its entries were
     * imported (the first full upload to the family). A failure is tried again at the next finished sync.
     */
    private suspend fun cleanUpLegacy(status: StateFlow<CloudStatus>) {
        combine(published, status) { signedIn, s -> signedIn to s }.collect { (signedIn, s) ->
            val uid = signedIn?.uid ?: return@collect
            if (s !is CloudStatus.Synced || signedIn.familyId == null) return@collect
            if (cache.legacy(uid) != LegacyState.IMPORTED || s.atMillis < cache.legacyImportedAt(uid)) return@collect
            try {
                store.deleteLegacyEntries(uid)
                cache.setLegacy(uid, LegacyState.DONE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete the backup from before families", e)
            }
        }
    }

    /** What the invite [code] means to [signedIn], with the invite. Call with [mutex] held. */
    private suspend fun inspect(code: String): Pair<InviteCheck, StoredInvite?> {
        val invite = store.invite(code) ?: return InviteCheck.Expired to null
        val check = when {
            invite.expiresAtMillis <= now() -> InviteCheck.Expired
            invite.familyId == published.value?.familyId -> InviteCheck.AlreadyMember
            else -> InviteCheck.Valid(invite.email?.takeIf { it.isNotBlank() })
        }
        return check to invite
    }

    /** Runs a family action for the signed-in account and its known family. */
    private suspend fun <T> act(
        locked: Boolean = false,
        block: suspend (AccountUser, String) -> T,
    ): FamilyResult<T> {
        val signedIn = user.value ?: return FamilyResult.Failed(FamilyError.NOT_READY)
        return try {
            val run: suspend () -> FamilyResult<T> = {
                val family = published.value?.takeIf { it.uid == signedIn.uid }?.familyId
                if (family == null) FamilyResult.Failed(FamilyError.NOT_READY) else FamilyResult.Ok(block(signedIn, family))
            }
            if (locked) mutex.withLock { run() } else run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Family action failed", e)
            // Refused while signed in: most likely no longer a member (removed). Find out.
            if (e is CloudException && e.error == CloudError.OTHER) refresh()
            FamilyResult.Failed(e.toFamilyError())
        }
    }

    private fun newFamilyId(): String = urlSafe(randomBytes(FAMILY_ID_BYTES))

    /** 128 random bits, URL-safe base64 without padding: 22 characters (BABIES_AND_BACKUP §4.1). */
    private fun newCode(): String = urlSafe(randomBytes(INVITE_CODE_BYTES))

    companion object {
        private const val TAG = "Families"

        /** How long a failed resolution waits before it tries again. */
        const val RETRY_MILLIS = 60_000L

        /**
         * An invite's lifetime: 7 days, less a margin, because the rules refuse an expiry more than 7 days after the
         * server's time, and this phone's clock may run a little ahead of it.
         */
        const val INVITE_LIFETIME_MILLIS = 7 * 24 * 60 * 60 * 1000L - 10 * 60 * 1000L

        const val INVITE_CODE_BYTES = 16
        private const val FAMILY_ID_BYTES = 15
        private const val MAX_LEGACY_PAGES = 10_000
        private val CODE = Regex("[A-Za-z0-9_-]{22,64}")

        /** A code the rules could accept (`invites/{code}`): anything else is no invite and is never looked up. */
        fun isInviteCode(code: String): Boolean = CODE.matches(code)

        fun inviteLink(hostingDomain: String, code: String): String = "https://$hostingDomain/join/$code"

        fun urlSafe(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        private fun Exception.toFamilyError(): FamilyError = when ((this as? CloudException)?.error) {
            CloudError.OFFLINE -> FamilyError.NO_CONNECTION
            CloudError.SIGNED_OUT -> FamilyError.NOT_READY
            else -> FamilyError.OTHER
        }
    }
}

private val secureRandom by lazy { SecureRandom() }

private fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

/**
 * [FamilyCache] in shared preferences. Device-local: excluded from backup and device transfer
 * (res/xml/data_extraction_rules.xml); a new phone resolves the family from the server.
 */
class FamilyPrefs(context: Context) : FamilyCache {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun familyOf(uid: String): String? = prefs.getString("family.$uid", null)
    override fun setFamily(uid: String, familyId: String) = prefs.edit { putString("family.$uid", familyId) }

    override fun legacy(uid: String): LegacyState =
        prefs.getString("legacy.$uid", null)?.let { name -> LegacyState.entries.firstOrNull { it.name == name } } ?: LegacyState.UNKNOWN

    override fun legacyImportedAt(uid: String): Long = prefs.getLong("legacyAt.$uid", 0)

    override fun setLegacy(uid: String, state: LegacyState, atMillis: Long) = prefs.edit {
        putString("legacy.$uid", state.name)
        putLong("legacyAt.$uid", atMillis)
    }

    override fun heldForDeletion(uid: String): Boolean = prefs.getBoolean("held.$uid", false)
    override fun setHeldForDeletion(uid: String, held: Boolean) = prefs.edit { putBoolean("held.$uid", held) }

    internal companion object {
        const val FILE = "account_family"
    }
}
