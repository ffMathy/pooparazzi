package com.ffmathy.pooparazzi.phone

import com.ffmathy.pooparazzi.phone.account.FirebaseEmulators
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The debug builds' switch to the local Firebase emulators (docs/BACKEND.md › Running the app against the emulators). */
class FirebaseEmulatorsTest {
    @After
    fun clear() {
        System.clearProperty(FirebaseEmulators.PROPERTY)
    }

    @Test
    fun aHostTakesFirebaseJsonsPorts() {
        val emulators = FirebaseEmulators.parse("10.0.2.2")!!
        assertEquals("10.0.2.2", emulators.host)
        assertEquals(9099, emulators.authPort)
        assertEquals(8080, emulators.firestorePort)
    }

    @Test
    fun portsCanBeGiven() {
        val emulators = FirebaseEmulators.parse("127.0.0.1:9199:8180")!!
        assertEquals(9199, emulators.authPort)
        assertEquals(8180, emulators.firestorePort)
    }

    @Test
    fun nothingSetMeansARealProjectOrNone() {
        assertNull(FirebaseEmulators.parse(""))
        // The unit tests build without -Ppooparazzi.firebase.emulators, and set no property.
        assertNull(FirebaseEmulators.configured())
        System.setProperty(FirebaseEmulators.PROPERTY, "127.0.0.1")
        assertEquals("127.0.0.1", FirebaseEmulators.configured()?.host)
    }
}
