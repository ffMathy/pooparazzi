package com.ffmathy.pooparazzi.phone.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback

/** Start sleep / Woke up on a widget. */
class ToggleSleepAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetCommands.from(context).toggleSleep()
        WidgetUpdater.refresh(context)
    }
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
}

/** The 3×1 widget's provider (the former 2×1; the class name stays so placed widgets survive). */
class QuickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickWidget()

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.cancelIfNoneLeft(context)
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
}
