package com.ffmathy.pooparazzi.phone

import com.ffmathy.pooparazzi.core.cloud.CloudError
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.account.AccountUser
import com.ffmathy.pooparazzi.phone.account.FamilyError
import com.ffmathy.pooparazzi.phone.account.FamilyResult
import com.ffmathy.pooparazzi.phone.account.FamilyService
import com.ffmathy.pooparazzi.phone.account.InviteCheck
import com.ffmathy.pooparazzi.phone.account.JoinResult
import com.ffmathy.pooparazzi.phone.account.LegacyState
import com.ffmathy.pooparazzi.phone.account.MemoryConsent
import com.ffmathy.pooparazzi.phone.account.StoredInvite
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The family layer (BABIES_AND_BACKUP §3.4–3.5, §4.1) on an in-memory store: resolving the account's family (creating
 * one the first time, a new one after being removed), the cached family at once, the move of the backup from before
 * families, invites, joining, leaving and removing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class FamilyServiceTest {
    private val anna = AccountUser("anna", "anna@example.com", google = true)
    private val bo = AccountUser("bo", "bo@example.com", google = false)

    private class Rig(
        val service: FamilyService,
        val store: FakeFamilyStore,
        val cache: MemoryFamilyCache,
        val user: MutableStateFlow<AccountUser?>,
        val status: MutableStateFlow<CloudStatus>,
        val imported: MutableList<List<Entry>>,
        val clock: LongArray,
        val consent: MemoryConsent,
    ) {
        val familyId: String? get() = service.account.value?.familyId
    }

    private fun test(
        signedIn: AccountUser? = anna,
        store: FakeFamilyStore = FakeFamilyStore(),
        cache: MemoryFamilyCache = MemoryFamilyCache(),
        start: Boolean = true,
        consent: MemoryConsent = MemoryConsent(setOf("anna", "bo")),
        block: suspend TestScope.(Rig) -> Unit,
    ) = runTest {
        val user = MutableStateFlow(signedIn)
        val status = MutableStateFlow<CloudStatus>(CloudStatus.Off)
        val imported = mutableListOf<List<Entry>>()
        val clock = longArrayOf(NOW)
        var seed = 0
        val service = FamilyService(
            store, user, cache, backgroundScope, "demo-pooparazzi.web.app",
            now = { clock[0] },
            randomBytes = { size -> ByteArray(size) { (seed++ % 64).toByte() } },
            retryMillis = 60_000,
            consent = consent,
        )
        val rig = Rig(service, store, cache, user, status, imported, clock, consent)
        if (start) {
            service.start(status) { entries -> imported += entries }
            runCurrent()
        }
        block(rig)
    }

    @Test
    fun aNewAccountCreatesItsOwnFamilyInOneBatch() = test { rig ->
        val created = rig.store.calls.single { it.startsWith("createFamily:") }
        val familyId = rig.familyId!!
        assertEquals("createFamily:$familyId:anna:anna@example.com", created)
        assertEquals(familyId, rig.store.users["anna"])
        assertEquals("anna", rig.store.creators[familyId])
        assertEquals(familyId, rig.cache.families["anna"])
        assertEquals("nothing from before families", LegacyState.DONE, rig.cache.legacy("anna"))
    }

    @Test
    fun anAccountWithAFamilyUsesIt() = test(store = FakeFamilyStore().apply { seedFamily("fam-1", "anna") }) { rig ->
        assertEquals("fam-1", rig.familyId)
        assertTrue(rig.store.calls.none { it.startsWith("createFamily") })
    }

    @Test
    fun theCachedFamilyIsPublishedAtOnceAndConfirmedLater() {
        val cache = MemoryFamilyCache().apply {
            setFamily("anna", "fam-1")
            setLegacy("anna", LegacyState.DONE)
        }
        val store = FakeFamilyStore().apply { seedFamily("fam-1", "anna") }
        test(store = store, cache = cache, start = false) { rig ->
            assertEquals("before any server answer", "fam-1", rig.familyId)
            assertEquals(emptyList<String>(), rig.store.calls)
            rig.service.start(rig.status) {}
            runCurrent()
            assertEquals("fam-1", rig.familyId)
            assertTrue(rig.store.calls.contains("familyOf:anna"))
        }
    }

    @Test
    fun aRemovedMemberGetsAFamilyOfTheirOwn() {
        val store = FakeFamilyStore().apply {
            seedFamily("fam-1", "bo")
            users["anna"] = "fam-1" // removed by Bo: the users document still names the family
        }
        test(store = store) { rig ->
            val familyId = rig.familyId!!
            assertTrue(familyId != "fam-1")
            assertEquals(familyId, rig.store.users["anna"])
            assertTrue(rig.store.calls.contains("isMember:fam-1:anna"))
        }
    }

    @Test
    fun offlineTheFamilyStaysUnknownAndIsTriedAgain() = test(store = FakeFamilyStore().apply { failWith = CloudError.OFFLINE }) { rig ->
        assertNull("nothing synced yet: the backup says it is on its way", rig.familyId)
        rig.store.failWith = null
        advanceTimeBy(60_001)
        runCurrent()
        assertEquals(rig.store.users["anna"], rig.familyId)
    }

    @Test
    fun signingOutAndInFollowsTheAccount() = test { rig ->
        val annas = rig.familyId!!
        rig.user.value = null
        runCurrent()
        assertNull(rig.service.account.value)
        assertFalse("signing out withdraws the consent", "anna" in rig.consent.agreed.value)
        rig.user.value = bo
        runCurrent()
        val bos = rig.familyId!!
        assertTrue(bos != annas)
        assertEquals("bo", rig.service.account.value?.uid)
    }

    @Test
    fun theBackupFromBeforeFamiliesIsImportedFirstAndDeletedAfterTheFirstFullUpload() {
        val entries = SampleData.scenario().take(3)
        val store = FakeFamilyStore().apply { legacy["anna"] = entries.toMutableList() }
        test(store = store, start = false) { rig ->
            var familyWhenImported: String? = "not imported"
            rig.service.start(rig.status) { page ->
                familyWhenImported = rig.familyId
                rig.imported += page
            }
            runCurrent()
            assertEquals(listOf(entries), rig.imported)
            assertNull("imported before the family is published, so its first sync uploads them", familyWhenImported)
            assertEquals(LegacyState.IMPORTED, rig.cache.legacy("anna"))
            assertTrue(rig.store.calls.none { it.startsWith("deleteLegacyEntries") })

            // A sync status from before the import says nothing about the upload.
            rig.status.value = CloudStatus.Synced(NOW - 1)
            runCurrent()
            assertTrue(rig.store.calls.none { it.startsWith("deleteLegacyEntries") })

            rig.status.value = CloudStatus.Synced(NOW + 5_000)
            runCurrent()
            assertTrue(rig.store.calls.contains("deleteLegacyEntries:anna"))
            assertEquals(LegacyState.DONE, rig.cache.legacy("anna"))
        }
    }

    @Test
    fun anInviteIsA128BitCodeForSixDaysInTheMembersName() = test { rig ->
        val result = rig.service.createInvite()
        val link = (result as FamilyResult.Ok).value
        val code = link.removePrefix("https://demo-pooparazzi.web.app/join/")
        assertTrue(link, link.startsWith("https://demo-pooparazzi.web.app/join/"))
        assertEquals(22, code.length)
        assertTrue(code, code.matches(Regex("[A-Za-z0-9_-]+")))
        val invite = rig.store.invites.getValue(code)
        assertEquals(StoredInvite(rig.familyId!!, "anna", NOW + FamilyService.INVITE_LIFETIME_MILLIS, "anna@example.com"), invite)
        assertEquals("a day short of the rules' 7, for a clock ahead of the server's", 6 * DAY, invite.expiresAtMillis - NOW)
        assertEquals("remembered, to take it back later", mapOf(code to invite.expiresAtMillis), rig.cache.invites("anna"))
    }

    @Test
    fun aRefusedInviteSaysWhy() = test { rig ->
        rig.store.failWith = CloudError.OTHER
        assertEquals(FamilyResult.Failed(FamilyError.INVITE_REFUSED), rig.service.createInvite())
        rig.store.failWith = CloudError.OFFLINE
        assertEquals(FamilyResult.Failed(FamilyError.NO_CONNECTION), rig.service.createInvite())
        assertTrue(rig.cache.invites("anna").isEmpty())
    }

    @Test
    fun anInviteSaysWhatItIs() {
        val store = FakeFamilyStore().apply {
            seedFamily("fam-bo", "bo", creatorEmail = "bo@example.com")
            seedFamily("fam-anna", "anna")
            invites[CODE] = StoredInvite("fam-bo", "bo", NOW + DAY, "bo@example.com")
            invites[OTHER_CODE] = StoredInvite("fam-bo", "bo", NOW - 1, "bo@example.com")
            invites[OWN_CODE] = StoredInvite("fam-anna", "anna", NOW + DAY, "anna@example.com")
        }
        test(store = store) { rig ->
            assertEquals(InviteCheck.Valid("bo@example.com"), rig.service.checkInvite(CODE))
            assertEquals(InviteCheck.Expired, rig.service.checkInvite(OTHER_CODE))
            assertEquals("used up or never made", InviteCheck.Expired, rig.service.checkInvite("ZyXwVuTsRqPoNmLkJiHg-_"))
            assertEquals(InviteCheck.AlreadyMember, rig.service.checkInvite(OWN_CODE))
            assertEquals(InviteCheck.Invalid, rig.service.checkInvite("short"))
            assertEquals(InviteCheck.Invalid, rig.service.checkInvite("../users/anna/aaaaaaaaaaaaaaaaaaaaaa"))
            assertTrue("an invalid code is never looked up", rig.store.calls.none { it.startsWith("invite:short") })
        }
    }

    @Test
    fun joiningSwitchesFamilyUsesTheInviteUpAndLeavesTheOldFamily() {
        val store = FakeFamilyStore().apply {
            seedFamily("fam-bo", "bo", creatorEmail = "bo@example.com")
            seedFamily("fam-anna", "anna")
            invites[CODE] = StoredInvite("fam-bo", "bo", NOW + DAY, "bo@example.com")
        }
        test(store = store) { rig ->
            assertEquals("fam-anna", rig.familyId)
            assertEquals(JoinResult.Joined, rig.service.join(CODE))
            runCurrent()
            assertEquals("the backup follows the new family", "fam-bo", rig.familyId)
            assertEquals("fam-bo", rig.cache.families["anna"])
            assertTrue(rig.store.calls.contains("join:$CODE:fam-bo:anna"))
            assertFalse("single use", CODE in rig.store.invites)
            assertTrue("the old family is left (its last member gone, its data deleted)", rig.store.calls.contains("leave:fam-anna:anna"))
            assertTrue("the family document stays, as a tombstone", rig.store.members.getValue("fam-anna").isEmpty())
            assertEquals("anna", rig.store.creators["fam-anna"])
            assertEquals(setOf("bo", "anna"), rig.store.members.getValue("fam-bo").keys)

            // The used invite is gone: it reads as expired or used.
            assertEquals(JoinResult.Refused(InviteCheck.Expired), rig.service.join(CODE))
        }
    }

    @Test
    fun anExpiredInviteJoinsNothing() {
        val store = FakeFamilyStore().apply {
            seedFamily("fam-bo", "bo")
            seedFamily("fam-anna", "anna")
            invites[CODE] = StoredInvite("fam-bo", "bo", NOW - 1, null)
        }
        test(store = store) { rig ->
            assertEquals(JoinResult.Refused(InviteCheck.Expired), rig.service.join(CODE))
            assertEquals("fam-anna", rig.familyId)
            assertTrue(rig.store.calls.none { it.startsWith("join:") })
        }
    }

    @Test
    fun leavingStartsANewFamilyOfYourOwnInOneBatch() {
        val store = FakeFamilyStore().apply { seedFamily("fam-bo", "bo", "anna" to "anna@example.com") }
        test(store = store) { rig ->
            assertEquals("fam-bo", rig.familyId)
            assertEquals(FamilyResult.Ok(Unit), rig.service.leave())
            val fresh = rig.familyId!!
            assertTrue(fresh != "fam-bo")
            assertTrue(rig.store.calls.contains("createFamily:$fresh:anna:anna@example.com:leaving=fam-bo"))
            assertEquals(setOf("bo"), rig.store.members.getValue("fam-bo").keys)
        }
    }

    @Test
    fun theCreatorLeavingHandsTheFamilyToTheMemberWhoJoinedFirst() {
        val store = FakeFamilyStore().apply {
            seedFamily("fam-anna", "anna", "cai" to "cai@example.com", "bo" to "bo@example.com")
        }
        test(store = store) { rig ->
            assertEquals(FamilyResult.Ok(Unit), rig.service.leave())
            val fresh = rig.familyId!!
            assertTrue(rig.store.calls.contains("createFamily:$fresh:anna:anna@example.com:leaving=fam-anna:handOverTo=cai"))
            assertEquals("cai", rig.store.creators["fam-anna"])
            assertEquals(setOf("cai", "bo"), rig.store.members.getValue("fam-anna").keys)
        }
    }

    @Test
    fun leavingTakesBackTheInvitesStillOutstanding() {
        val store = FakeFamilyStore().apply { seedFamily("fam-bo", "bo", "anna" to "anna@example.com") }
        test(store = store) { rig ->
            val first = (rig.service.createInvite() as FamilyResult.Ok).value.substringAfterLast('/')
            val used = (rig.service.createInvite() as FamilyResult.Ok).value.substringAfterLast('/')
            rig.store.invites.remove(used) // someone joined with it
            assertEquals(FamilyResult.Ok(Unit), rig.service.leave())
            assertTrue(rig.store.calls.contains("deleteInvite:$first"))
            assertTrue(rig.store.invites.isEmpty())
            assertTrue("forgotten, used or not", rig.cache.invites("anna").isEmpty())
        }
    }

    @Test
    fun anInviteThatCannotBeTakenBackOfflineIsTriedAgain() {
        val store = FakeFamilyStore().apply { seedFamily("fam-bo", "bo", "anna" to "anna@example.com") }
        test(store = store) { rig ->
            val code = (rig.service.createInvite() as FamilyResult.Ok).value.substringAfterLast('/')
            val expired = "ExpiredExpiredExpired0"
            rig.cache.addInvite("anna", expired, NOW - 1)
            rig.store.failWith = CloudError.OFFLINE
            assertTrue("the deletion of the account fails offline", runCatching { rig.service.deleteAccountData("anna") }.isFailure)
            rig.store.failWith = null
            assertEquals("kept for the next try; the expired one forgotten", setOf(code), rig.cache.invites("anna").keys)
            assertTrue(rig.store.calls.none { it == "deleteInvite:$expired" })
        }
    }

    @Test
    fun deletingTheAccountTakesBackInvitesAndDeletesTheOldBackupBeforeTheUsersDocument() {
        val store = FakeFamilyStore().apply {
            seedFamily("fam-anna", "anna")
            legacy["anna"] = SampleData.scenario().take(2).toMutableList()
        }
        test(store = store, cache = MemoryFamilyCache().apply { setLegacy("anna", LegacyState.IMPORTED) }) { rig ->
            val code = (rig.service.createInvite() as FamilyResult.Ok).value.substringAfterLast('/')
            rig.store.calls.clear()
            rig.service.deleteAccountData("anna")
            assertEquals(listOf("deleteInvite:$code", "deleteLegacyEntries:anna", "deleteUser:anna"), rig.store.calls)
            assertFalse("anna" in rig.store.legacy)
            assertFalse("anna" in rig.store.users)
        }
    }

    @Test
    fun withoutConsentNothingIsReadOrWrittenAndNothingPublished() = test(consent = MemoryConsent()) { rig ->
        assertNull(rig.service.account.value)
        assertEquals(emptyList<String>(), rig.store.calls)
        assertEquals(JoinResult.Refused(InviteCheck.Failed(FamilyError.NOT_READY)), rig.service.join(CODE))
        assertEquals(InviteCheck.Failed(FamilyError.NOT_READY), rig.service.checkInvite(CODE))
        assertEquals(FamilyResult.Failed(FamilyError.NOT_READY), rig.service.createInvite())
        rig.service.refresh()
        rig.service.holdForDeletion(false) // "Back up now" resolves the family too
        runCurrent()
        assertEquals("not even the family is looked up", emptyList<String>(), rig.store.calls)

        rig.consent.agree("anna")
        runCurrent()
        val familyId = rig.familyId!!
        assertTrue(rig.store.calls.contains("createFamily:$familyId:anna:anna@example.com"))
    }

    @Test
    fun aCachedFamilyWaitsForTheConsentToo() {
        val cache = MemoryFamilyCache().apply {
            setFamily("anna", "fam-1")
            setLegacy("anna", LegacyState.DONE)
        }
        test(cache = cache, consent = MemoryConsent(), start = false) { rig ->
            assertNull(rig.service.account.value)
            rig.consent.agree("anna")
            runCurrent()
            assertEquals("fam-1", rig.familyId)
        }
    }

    @Test
    fun aloneThereIsNothingToLeave() = test { rig ->
        val own = rig.familyId
        val creates = rig.store.calls.count { it.startsWith("createFamily") }
        assertEquals(FamilyResult.Ok(Unit), rig.service.leave())
        assertEquals(own, rig.familyId)
        assertEquals(creates, rig.store.calls.count { it.startsWith("createFamily") })
    }

    @Test
    fun onlyTheCreatorRemovesOthers() {
        val store = FakeFamilyStore().apply {
            seedFamily("fam-bo", "bo", "anna" to "anna@example.com", "cai" to "cai@example.com")
        }
        test(store = store) { rig ->
            val family = (rig.service.load() as FamilyResult.Ok).value
            assertEquals(listOf("anna", "bo", "cai"), family.members.map { it.uid })
            assertTrue(family.members.first().you)
            assertTrue(family.members.single { it.uid == "bo" }.creator)
            assertFalse(family.youAreCreator)

            assertEquals(FamilyResult.Failed(FamilyError.OTHER), rig.service.remove("cai"))
            assertTrue(rig.store.calls.none { it.startsWith("removeMember") })

            rig.user.value = bo
            runCurrent()
            assertEquals(FamilyResult.Failed(FamilyError.OTHER), rig.service.remove("bo"))
            assertEquals(FamilyResult.Ok(Unit), rig.service.remove("cai"))
            assertEquals(setOf("bo", "anna"), rig.store.members.getValue("fam-bo").keys)
        }
    }

    @Test
    fun whileTheBackupIsDeletedNoNewFamilyIsMade() = test { rig ->
        val own = rig.familyId!!
        rig.service.holdForDeletion(true)
        // The backup deletion took the account out of its family, and the users document went too.
        rig.store.leave(own, "anna")
        rig.store.users.remove("anna")
        val creates = rig.store.calls.count { it.startsWith("createFamily") }
        rig.service.refresh()
        runCurrent()
        assertEquals("the deleted family stays, so the backup says it was deleted", own, rig.familyId)
        assertEquals(creates, rig.store.calls.count { it.startsWith("createFamily") })

        // "Back up now": a family again.
        rig.service.holdForDeletion(false)
        runCurrent()
        assertTrue(rig.familyId != own)
        assertEquals(creates + 1, rig.store.calls.count { it.startsWith("createFamily") })
    }

    @Test
    fun signingOutEndsTheHold() = test { rig ->
        rig.service.holdForDeletion(true)
        assertTrue(rig.cache.heldForDeletion("anna"))
        rig.user.value = null
        runCurrent()
        assertFalse(rig.cache.heldForDeletion("anna"))
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val DAY = 24 * 60 * 60 * 1000L
        const val CODE = "AbCdEfGhIjKlMnOpQrSt_-"
        const val OTHER_CODE = "ZyXwVuTsRqPoNmLkJiHg_-"
        const val OWN_CODE = "OwnOwnOwnOwnOwnOwnOwn0"
    }
}
