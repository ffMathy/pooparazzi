package com.ffmathy.pooparazzi.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.phone.ui.history.HistoryRowDefaults

/** A row of a grouped list: surfaceContainer, outer corners r 20, inner r 6 (as the History list). */
@Composable
internal fun GroupRow(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(HistoryRowDefaults.shape(first, last))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

/**
 * Single choice as grouped 56 dp radio rows (Settings' Language, Home's Switch baby): titleMedium [label] and a
 * trailing radio button; the whole row is the target ([Role.RadioButton] in a selectable group).
 */
@Composable
internal fun <T> RadioGroup(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(HistoryRowDefaults.Gap)) {
        options.forEachIndexed { index, option ->
            GroupRow(first = index == 0, last = index == options.lastIndex) {
                val isSelected = option == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.onSurface,
                            unselectedColor = MaterialTheme.colorScheme.outline,
                        ),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}
