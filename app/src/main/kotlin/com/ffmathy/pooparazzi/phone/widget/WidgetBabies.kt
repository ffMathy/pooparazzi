package com.ffmathy.pooparazzi.phone.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which baby each placed widget is for (BABIES_AND_BACKUP §2.4), by app widget id. A widget without a choice follows
 * the phone's current baby, as every widget did before; tapping the baby on a widget ([next]) gives it its own, so
 * twins can each have a widget. A choice whose baby was removed falls back to the current baby.
 */
interface WidgetBabies {
    /** Each widget's chosen baby id. */
    val chosen: StateFlow<Map<Int, String>>

    fun choose(widgetId: Int, babyId: String)

    /** Forgets removed widgets. */
    fun forget(widgetIds: IntArray)

    companion object {
        /** The baby [widgetId] shows and logs for, given [settings]. */
        fun babyOf(chosen: Map<Int, String>, widgetId: Int?, settings: Settings): Baby =
            widgetId?.let { chosen[it] }?.let { id -> settings.babies.firstOrNull { it.id == id } } ?: settings.selectedBaby

        /** The baby after [current] in [settings]' order, wrapping around (the widget's baby button). */
        fun next(current: Baby, settings: Settings): Baby {
            val babies = settings.babies
            val index = babies.indexOfFirst { it.id == current.id }
            return babies[(index + 1).mod(babies.size)]
        }
    }
}

/** [WidgetBabies] in SharedPreferences, mirrored in a [StateFlow] that the widget sessions observe. */
class PrefsWidgetBabies private constructor(private val prefs: SharedPreferences) : WidgetBabies {
    private val state = MutableStateFlow(read())
    override val chosen: StateFlow<Map<Int, String>> = state.asStateFlow()

    @Synchronized
    override fun choose(widgetId: Int, babyId: String) {
        prefs.edit(commit = true) { putString(widgetId.toString(), babyId) }
        state.value = read()
    }

    @Synchronized
    override fun forget(widgetIds: IntArray) {
        prefs.edit(commit = true) { widgetIds.forEach { remove(it.toString()) } }
        state.value = read()
    }

    private fun read(): Map<Int, String> =
        prefs.all.mapNotNull { (key, value) -> key.toIntOrNull()?.let { id -> (value as? String)?.let { id to it } } }.toMap()

    companion object {
        /** The preferences file; device-local (widget ids mean nothing on another phone). */
        internal const val FILE = "widget_babies"

        @Volatile
        private var instance: PrefsWidgetBabies? = null

        fun get(context: Context): PrefsWidgetBabies = instance ?: synchronized(this) {
            instance ?: PrefsWidgetBabies(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
                .also { instance = it }
        }
    }
}

/** A [WidgetBabies] in memory (tests). */
class InMemoryWidgetBabies(initial: Map<Int, String> = emptyMap()) : WidgetBabies {
    private val state = MutableStateFlow(initial)
    override val chosen: StateFlow<Map<Int, String>> = state.asStateFlow()

    override fun choose(widgetId: Int, babyId: String) {
        state.value = state.value + (widgetId to babyId)
    }

    override fun forget(widgetIds: IntArray) {
        state.value = state.value - widgetIds.toSet()
    }
}
