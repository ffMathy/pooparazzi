package com.ffmathy.pooparazzi.phone.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import com.ffmathy.pooparazzi.core.Core

/** Start sleep / Woke up on a widget, for that widget's baby. */
class ToggleSleepAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetCommands.from(context).toggleSleep(widgetBaby(context, glanceId))
        WidgetUpdater.refresh(context)
    }
}

/**
 * The baby button on a widget (two or more babies): the widget moves on to the next baby, wrapping around (Noa → Ida →
 * Noa). It is the widget's own choice: the app's current baby and the other widgets stay as they are.
 */
class NextBabyAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val widgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }.getOrNull() ?: return
        val babies = PrefsWidgetBabies.get(context)
        val settings = Core.ensureInitialized(context).settings.current()
        val current = WidgetBabies.babyOf(babies.chosen.value, widgetId, settings)
        babies.choose(widgetId, WidgetBabies.next(current, settings).id)
        WidgetUpdater.refresh(context)
    }
}

/** The id of the baby the widget [glanceId] shows (its own choice, else the phone's current baby). */
private suspend fun widgetBaby(context: Context, glanceId: GlanceId): String {
    val settings = Core.ensureInitialized(context).settings.current()
    val widgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }.getOrNull()
    return WidgetBabies.babyOf(PrefsWidgetBabies.get(context).chosen.value, widgetId, settings).id
}

/** Undo in the receipt row, for that log's entry (no app launch). */
class UndoWidgetLogAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entryId = parameters[EntryId] ?: return
        WidgetCommands.from(context).undo(entryId)
        WidgetUpdater.refresh(context)
    }

    companion object {
        val EntryId = ActionParameters.Key<String>("entry_id")
    }
}

/** The 4×2 widget's provider. */
class LogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LogWidget()

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.cancelIfNoneLeft(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        PrefsWidgetBabies.get(context).forget(appWidgetIds)
    }
}

/** The 3×1 widget's provider (the former 2×1; the class name stays so placed widgets survive). */
class QuickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickWidget()

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.cancelIfNoneLeft(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        PrefsWidgetBabies.get(context).forget(appWidgetIds)
    }
}

/**
 * The 4×2 widget on the cover screen (Flex Window) of Samsung's flip phones ([CoverWidget]), under a provider that
 * only Samsung's cover screen lists (`keyguard` category, `sub_screen` display), so other launchers never show it.
 */
class CoverWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CoverWidget()

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.cancelIfNoneLeft(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        PrefsWidgetBabies.get(context).forget(appWidgetIds)
    }
}
