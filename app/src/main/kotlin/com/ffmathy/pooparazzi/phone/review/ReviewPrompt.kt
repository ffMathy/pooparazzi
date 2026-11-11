package com.ffmathy.pooparazzi.phone.review

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.data.Clock
import com.ffmathy.pooparazzi.core.data.EntryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

/** Where the review prompt's [ReviewMemory] is kept. */
interface ReviewStore {
    val memory: StateFlow<ReviewMemory>
    fun save(memory: ReviewMemory)
}

/**
 * The review prompt's memory, on this phone. It travels with a backup or a transfer to a new phone, so a parent who
 * rated or said "Don't ask again" is not asked there either.
 */
class ReviewPrefs(context: Context) : ReviewStore {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    override val memory: StateFlow<ReviewMemory> = state.asStateFlow()

    override fun save(memory: ReviewMemory) {
        prefs.edit {
            memory.firstSeenMillis?.let { putLong(KEY_FIRST_SEEN, it) } ?: remove(KEY_FIRST_SEEN)
            putBoolean(KEY_DONE, memory.done)
            putInt(KEY_TIMES_ASKED, memory.timesAsked)
            putLong(KEY_ASK_AGAIN_AT, memory.askAgainAtMillis)
        }
        state.value = memory
    }

    private fun read() = ReviewMemory(
        firstSeenMillis = if (prefs.contains(KEY_FIRST_SEEN)) prefs.getLong(KEY_FIRST_SEEN, 0L) else null,
        done = prefs.getBoolean(KEY_DONE, false),
        timesAsked = prefs.getInt(KEY_TIMES_ASKED, 0),
        askAgainAtMillis = prefs.getLong(KEY_ASK_AGAIN_AT, 0L),
    )

    internal companion object {
        const val FILE = "review_prompt"
        private const val KEY_FIRST_SEEN = "first_seen"
        private const val KEY_DONE = "done"
        private const val KEY_TIMES_ASKED = "times_asked"
        private const val KEY_ASK_AGAIN_AT = "ask_again_at"
    }
}

/**
 * The "Enjoying Pooparazzi?" prompt over Home ([ReviewRule]). [due] says whether it may show; Home decides when (an
 * idle Home by day). Whatever the parent answers closes it.
 */
class ReviewViewModel(
    repository: EntryRepository,
    private val store: ReviewStore,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    val due: StateFlow<Boolean> = combine(repository.observeAllEntries(), store.memory) { entries, memory ->
        ReviewRule.shouldAsk(memory, entries, clock.now(), zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Home is shown: the first time starts the clock of [ReviewRule.MIN_DAYS]. */
    fun onHomeShown() {
        val memory = store.memory.value
        if (memory.firstSeenMillis == null) store.save(memory.copy(firstSeenMillis = clock.now()))
    }

    /** "Rate on Google Play": never asked again (the store page opens). */
    fun onRate() = store.save(store.memory.value.copy(done = true))

    /** "Not now", Back or a tap outside: asked once more, a month later at the earliest. */
    fun onLater() = store.save(ReviewRule.later(store.memory.value, clock.now()))

    /** "Don't ask again". */
    fun onNever() = store.save(store.memory.value.copy(done = true))

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val app = context.applicationContext
            return viewModelFactory {
                initializer { ReviewViewModel(Core.repository, ReviewPrefs(app), Core.clock) }
            }
        }

        private const val TAG = "ReviewPrompt"

        /** Opens Pooparazzi's page in the Play Store app, or on the web without it. False when neither opens. */
        fun openStore(context: Context): Boolean {
            val id = context.packageName
            val intents = listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).setPackage("com.android.vending"),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")),
            )
            for (intent in intents) {
                try {
                    if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return true
                } catch (e: ActivityNotFoundException) {
                    Log.i(TAG, "No app for $intent")
                }
            }
            return false
        }
    }
}
