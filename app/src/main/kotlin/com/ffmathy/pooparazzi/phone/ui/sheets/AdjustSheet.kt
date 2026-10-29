package com.ffmathy.pooparazzi.phone.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import com.ffmathy.pooparazzi.phone.ui.home.titleWithTime
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/** What the Adjust start sheet can ask for. */
internal class AdjustSheetCallbacks(
    val onOffset: (Int) -> Unit,
    val onEdit: (String) -> Unit,
    val onSave: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * The Adjust start sheet (DESIGN_SPEC §3.3.1), from the running Lamp: "Adjust start · 01:50", the title showing the
 * start Save writes; −15m · −10m · −5m · +5m from the start when the sheet opened (re-tapping the selected chip returns
 * to it), then "Edit" (the full edit sheet) at the start and Save at the end. It has no timeout: it stays open until
 * Save, Edit or Cancel.
 */
@Composable
internal fun AdjustSheetContent(sheet: HomeSheetUi.Adjust, zone: ZoneId, formatters: Formatters, callbacks: AdjustSheetCallbacks) {
    val readiness = rememberPickerReadiness()
    val title = titleWithTime(CoreR.string.action_adjust_start, formatters.clock.format(sheet.time, zone))
    SheetColumn(title) {
        SheetHeader(title, callbacks.onCancel)
        Spacer(Modifier.height(12.dp))
        SheetChipRow(sheet.chips, formatters, readiness, callbacks.onOffset)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EditButton(enabled = readiness.ready, onClick = { callbacks.onEdit(sheet.entryId) })
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            SaveButton(enabled = readiness.ready, onClick = callbacks.onSave)
        }
    }
}

/** "Edit": 56 tall, r 28, 1 dp outline, `edit` 20 + 8 + labelLarge onSurface; opens the edit sheet for the entry. */
@Composable
internal fun EditButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
    ) {
        XIcon(XIcons.edit, contentDescription = null, tint = colors.onSurface, size = 20.dp)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(CoreR.string.action_edit), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
