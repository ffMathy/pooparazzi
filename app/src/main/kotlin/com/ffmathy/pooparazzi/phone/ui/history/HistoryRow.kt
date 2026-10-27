package com.ffmathy.pooparazzi.phone.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.ui.icons.EventAvatar
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.type.PhoneType
import com.ffmathy.pooparazzi.phone.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.ffmathy.pooparazzi.core.R as CoreR

/** List row geometry (DESIGN_SPEC §3.3.3, §5.8). */
internal object HistoryRowDefaults {
    val MinHeight = 56.dp
    val Gap = 2.dp
    val OuterCorner = 20.dp
    val InnerCorner = 6.dp

    /** How far a row slides to reveal Delete. */
    val RevealWidth = 104.dp

    /** The group's outer corners are r 20 and the inner ones r 6. */
    fun shape(first: Boolean, last: Boolean) = RoundedCornerShape(
        topStart = if (first) OuterCorner else InnerCorner,
        topEnd = if (first) OuterCorner else InnerCorner,
        bottomEnd = if (last) OuterCorner else InnerCorner,
        bottomStart = if (last) OuterCorner else InnerCorner,
    )
}

private enum class RowSwipe { Closed, Open }

/**
 * One History row: a 56 dp button in surfaceContainer with the shape-coded avatar at 16 and two lines at 72 (title
 * titleMedium 18/22, supporting dataMedium 16/20). Tap edits. Swiping it towards the start reveals Delete
 * (`AnchoredDraggable`, DESIGN_SPEC §10.8); TalkBack gets the same as custom actions "Edit time" and "Delete".
 */
@Composable
internal fun HistoryRow(
    entry: Entry,
    title: String,
    supporting: String,
    spoken: String,
    first: Boolean,
    last: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = HistoryRowDefaults.shape(first, last)
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val revealPx = with(density) { HistoryRowDefaults.RevealWidth.toPx() }
    val swipe = remember { AnchoredDraggableState(initialValue = RowSwipe.Closed) }
    SideEffect {
        swipe.updateAnchors(
            DraggableAnchors {
                RowSwipe.Closed at 0f
                RowSwipe.Open at -revealPx
            },
        )
    }
    val revealed by remember { derivedStateOf { swipe.offset.let { !it.isNaN() && it < -0.5f } } }
    val scope = rememberCoroutineScope()
    val editLabel = stringResource(R.string.action_edit_time)
    val deleteLabel = stringResource(CoreR.string.action_delete)

    Box(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (revealed) {
            // Behind the row, at its end: Delete (TalkBack users get the row's "Delete" action without swiping).
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(colors.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    Modifier
                        .width(HistoryRowDefaults.RevealWidth)
                        .fillMaxHeight()
                        .clickable(role = Role.Button, onClick = onDelete),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    XIcon(XIcons.delete, contentDescription = null, tint = colors.onErrorContainer, size = 18.dp)
                    Text(deleteLabel, style = MaterialTheme.typography.labelLarge, color = colors.onErrorContainer, maxLines = 1)
                }
            }
        }
        Row(
            Modifier
                .offset { IntOffset(swipe.offset.takeUnless { it.isNaN() }?.roundToInt() ?: 0, 0) }
                .anchoredDraggable(swipe, reverseDirection = rtl, orientation = Orientation.Horizontal)
                .fillMaxWidth()
                .heightIn(min = HistoryRowDefaults.MinHeight)
                .clip(shape)
                .background(colors.surfaceContainer)
                .clickable(onClickLabel = stringResource(CoreR.string.action_edit)) {
                    if (swipe.currentValue == RowSwipe.Open) scope.launch { swipe.animateTo(RowSwipe.Closed) } else onEdit()
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken
                    customActions = listOf(
                        CustomAccessibilityAction(editLabel) { onEdit(); true },
                        CustomAccessibilityAction(deleteLabel) { onDelete(); true },
                    )
                }
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EventAvatar(entry, Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(lineHeight = 22.sp, hyphens = Hyphens.Auto),
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    supporting,
                    style = PhoneType.dataMedium.copy(lineHeight = 20.sp),
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
