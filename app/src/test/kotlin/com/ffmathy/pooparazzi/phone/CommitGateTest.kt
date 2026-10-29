package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.cloud.CloudError
import com.ffmathy.pooparazzi.core.cloud.CloudException
import com.ffmathy.pooparazzi.phone.account.FirestoreCloudStore.CommitGate
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One Firestore commit in flight at a time (BABIES_AND_BACKUP §4.3): a commit that timed out keeps going in Firestore,
 * so the retries after it (the push after a write, the foreground, "Back up now") and deleting the backup wait for it
 * instead of queueing the same rows again. Tasks from [TaskCompletionSource], no Play Services.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CommitGateTest {
    private val timeout = 30_000L
    private var started = 0

    private fun start(task: Task<Unit>): () -> Task<Unit> = {
        started++
        task
    }

    private suspend fun offline(block: suspend () -> Unit) {
        val error = try {
            block()
            null
        } catch (e: CloudException) {
            e.error
        }
        assertEquals(CloudError.OFFLINE, error)
    }

    @Test
    fun aCommitThatTimedOutIsWaitedForInsteadOfQueuedAgain() = runTest {
        val gate = CommitGate(timeout)
        val queued = TaskCompletionSource<Unit>()
        offline { gate.commit(start(queued.task)) }
        assertEquals(1, started)

        // Still queued (offline, or the day's write quota used up): every retry waits for it and starts nothing new.
        repeat(3) { offline { gate.commit(start(TaskCompletionSource<Unit>().task)) } }
        offline { gate.drain() }
        assertEquals("one batch, not four", 1, started)

        // Once it has gone through, the next upload starts at once.
        queued.setResult(Unit)
        gate.commit(start(TaskCompletionSource<Unit>().apply { setResult(Unit) }.task))
        assertEquals(2, started)
        gate.drain()
    }

    @Test
    fun theNextCommitStartsAsSoonAsTheOutstandingOneEnds() = runTest {
        val gate = CommitGate(timeout)
        val slow = TaskCompletionSource<Unit>()
        offline { gate.commit(start(slow.task)) }

        val next = async { gate.commit(start(TaskCompletionSource<Unit>().apply { setResult(Unit) }.task)) }
        advanceTimeBy(timeout / 2)
        runCurrent()
        assertFalse(next.isCompleted)
        assertEquals(1, started)
        // It failed in the end (a refused write): that was its own caller's to hear; the next one goes ahead.
        slow.setException(IllegalStateException("refused"))
        runCurrent()
        assertTrue(next.isCompleted)
        assertEquals(2, started)
    }

    @Test
    fun deletingWaitsForAQueuedUpload() = runTest {
        val gate = CommitGate(timeout)
        val queued = TaskCompletionSource<Unit>()
        offline { gate.commit(start(queued.task)) }
        // The deletion must not start while the upload may still land after it.
        offline { gate.drain() }
        queued.setResult(Unit)
        gate.drain()
    }
}
