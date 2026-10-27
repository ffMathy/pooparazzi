package com.ffmathy.pooparazzi.core.data

/** Source of "now" in epoch milliseconds. Inject it everywhere so tests and screenshots can fix time. */
fun interface Clock {
    fun now(): Long
}

/** The wall clock ([System.currentTimeMillis]). */
object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}

/** A clock that stands still until moved; for tests, previews and screenshots. */
class FixedClock(nowMillis: Long) : Clock {
    @Volatile
    var nowMillis: Long = nowMillis
        private set

    override fun now(): Long = nowMillis

    fun set(nowMillis: Long) {
        this.nowMillis = nowMillis
    }

    fun advanceBy(millis: Long) {
        nowMillis += millis
    }
}
