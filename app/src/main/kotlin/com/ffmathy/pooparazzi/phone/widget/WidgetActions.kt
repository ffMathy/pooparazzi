package com.ffmathy.pooparazzi.phone.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import com.ffmathy.pooparazzi.core.model.DiaperKind

/** Pee, Poop or Both on a widget: logs in place (DESIGN_SPEC §3.3.6). */
class LogDiaperAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val kind = parameters[Kind]?.let { name -> DiaperKind.entries.firstOrNull { it.name == name } } ?: return
        WidgetCommands.from(context).logDiaper(kind)
        WidgetUpdater.refresh(context)
    }

    companion object {
        val Kind = ActionParameters.Key<String>("diaper_kind")
    }
}

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

/** The 2×1 widget's provider. */
class QuickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickWidget()

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.cancelIfNoneLeft(context)
    }
}
