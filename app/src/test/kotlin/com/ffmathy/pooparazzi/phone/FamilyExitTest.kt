package com.ffmathy.pooparazzi.phone

import com.ffmathy.pooparazzi.phone.account.ExitStore
import com.ffmathy.pooparazzi.phone.account.FamilyExit
import com.ffmathy.pooparazzi.phone.account.StoredMember
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Taking an account out of its family (account deletion, leaving the old family after joining another): handing the
 * family over, the last member's wipe, and the wipe stopping when someone joins meanwhile (the rules cannot count
 * members, so the app must look again before each step). The family document is never deleted.
 */
class FamilyExitTest {

    /** A family in memory, as the rules let its member [uid] see and change it. [onDelete] runs before each page goes. */
    private class Store(
        val creator: String,
        vararg members: StoredMember,
        val pages: MutableMap<String, Int> = mutableMapOf("entries" to 3, "babies" to 1),
    ) : ExitStore {
        val members = members.associateBy { it.uid }.toMutableMap()
        var createdBy = creator
        val calls = mutableListOf<String>()
        var onDelete: (Int) -> Unit = {}
        var outside = setOf<String>()
        private var deletes = 0

        override suspend fun membersOrNull(familyId: String): List<StoredMember>? {
            calls += "members"
            return members.values.toList().takeIf { members.keys.any { it !in outside } }
        }

        override suspend fun creatorOf(familyId: String): String = createdBy.also { calls += "creator" }

        override suspend fun deletePage(familyId: String, collection: String): Int {
            onDelete(++deletes)
            val left = pages.getValue(collection)
            calls += "delete:$collection"
            if (left == 0) return 0
            pages[collection] = left - 1
            return 400
        }

        override suspend fun leave(familyId: String, uid: String, handOverTo: String?) {
            calls += "leave:$uid" + (handOverTo?.let { ":handOverTo=$it" } ?: "")
            if (handOverTo != null) {
                check(createdBy == uid && handOverTo in members) { "the rules refuse this hand-over" }
                createdBy = handOverTo
            }
            members.remove(uid)
        }
    }

    private fun member(uid: String, joined: Long) = StoredMember(uid, "$uid@example.com", joined)

    @Test
    fun theLastMemberDeletesTheEntriesAndBabiesThenLeavesAndTheFamilyStays() = runTest {
        val store = Store("anna", member("anna", 1))
        FamilyExit(store).leave("fam", "anna")
        assertEquals(mapOf("entries" to 0, "babies" to 0), store.pages)
        assertEquals("leave:anna", store.calls.last())
        assertTrue(store.members.isEmpty())
        assertEquals("the family document (its creator) is left as it was", "anna", store.createdBy)
        assertEquals(
            "the members are read again before every page and before the member document goes",
            store.calls.count { it.startsWith("delete:") } + 2,
            store.calls.count { it == "members" },
        )
    }

    @Test
    fun withOthersOnlyTheMemberDocumentGoes() = runTest {
        val store = Store("bo", member("bo", 1), member("anna", 2))
        FamilyExit(store).leave("fam", "anna")
        assertEquals(listOf("members", "creator", "leave:anna"), store.calls)
        assertEquals(mapOf("entries" to 3, "babies" to 1), store.pages)
        assertEquals("bo", store.createdBy)
    }

    @Test
    fun theCreatorHandsTheFamilyToTheLongestStandingMember() = runTest {
        val store = Store("anna", member("anna", 1), member("cai", 3), member("bo", 2))
        FamilyExit(store).leave("fam", "anna")
        assertEquals("leave:anna:handOverTo=bo", store.calls.last())
        assertEquals("bo", store.createdBy)
        assertEquals(setOf("cai", "bo"), store.members.keys)
    }

    @Test
    fun someoneJoiningDuringTheWipeStopsItAndTheAccountJustLeaves() = runTest {
        val store = Store("anna", member("anna", 1))
        // Bo joins with an invite after the first page is deleted.
        store.onDelete = { n -> if (n == 2) store.members["bo"] = member("bo", 5) }
        FamilyExit(store).leave("fam", "anna")
        assertEquals("the wipe stops at the next look", mapOf("entries" to 1, "babies" to 1), store.pages)
        assertEquals("leave:anna:handOverTo=bo", store.calls.last())
        assertEquals("bo", store.createdBy)
        assertEquals(setOf("bo"), store.members.keys)
    }

    @Test
    fun someoneJoiningAfterTheLastPageKeepsTheFamilyToo() = runTest {
        val store = Store("anna", member("anna", 1), pages = mutableMapOf("entries" to 1, "babies" to 0))
        store.onDelete = { n -> if (n == 3) store.members["bo"] = member("bo", 5) }
        FamilyExit(store).leave("fam", "anna")
        assertEquals("leave:anna:handOverTo=bo", store.calls.last())
        assertEquals(setOf("bo"), store.members.keys)
    }

    @Test
    fun aMemberWhoJoinsWhileANonCreatorWipesGetsNothingHandedOver() = runTest {
        // A family everyone else left: its creator is gone, so nobody can hand it over.
        val store = Store("zed", member("anna", 1))
        store.onDelete = { n -> if (n == 1) store.members["bo"] = member("bo", 5) }
        FamilyExit(store).leave("fam", "anna")
        assertEquals("leave:anna", store.calls.last())
        assertEquals("zed", store.createdBy)
    }

    @Test
    fun notAMemberAnyMoreNothingHappens() = runTest {
        val removed = Store("bo", member("bo", 1)).apply { outside = setOf("bo", "anna") }
        FamilyExit(removed).leave("fam", "anna")
        assertEquals(listOf("members"), removed.calls)

        val notListed = Store("bo", member("bo", 1))
        FamilyExit(notListed).leave("fam", "anna")
        assertEquals(listOf("members"), notListed.calls)
    }

    @Test
    fun removedDuringTheWipeItStops() = runTest {
        val store = Store("anna", member("anna", 1))
        store.onDelete = { n -> if (n == 2) store.members.remove("anna") }
        FamilyExit(store).leave("fam", "anna")
        assertTrue(store.calls.none { it.startsWith("leave:") })
    }

    @Test
    fun theSuccessorIsWhoJoinedFirst() {
        val members = listOf(member("anna", 1), member("cai", 2), member("bo", 2))
        assertEquals("bo", FamilyExit.successor("anna", members))
        assertEquals("anna", FamilyExit.successor("bo", members))
        assertNull(FamilyExit.successor("anna", listOf(member("anna", 1))))
    }
}
