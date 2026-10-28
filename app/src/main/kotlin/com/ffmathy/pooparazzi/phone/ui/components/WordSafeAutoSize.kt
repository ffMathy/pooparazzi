package com.ffmathy.pooparazzi.phone.ui.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Autosize that steps the font down from [max] to [min] until the text fits its lines **without breaking a word**:
 * every line must end at a space, a hyphen or a soft hyphen ("Modermælks­erstatning" breaks only at its soft hyphen),
 * never mid-word ("Modermæl / ks-"). At [min] it settles for whatever fits.
 */
internal class WordSafeAutoSize(private val min: TextUnit, private val max: TextUnit, private val step: TextUnit = 0.5.sp) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = max.value
        while (size > min.value) {
            val layout = performLayout(constraints, text, size.sp)
            if (!layout.hasVisualOverflow && !layout.breaksAWord(text.text)) return size.sp
            size -= step.value
        }
        return min
    }

    override fun equals(other: Any?): Boolean =
        other is WordSafeAutoSize && other.min == min && other.max == max && other.step == step

    override fun hashCode(): Int = (min.hashCode() * 31 + max.hashCode()) * 31 + step.hashCode()
}

/** True when a line (other than the last) ends inside a word. */
private fun TextLayoutResult.breaksAWord(text: String): Boolean = (0 until lineCount - 1).any { line ->
    val end = getLineEnd(line)
    end in 1 until text.length && text[end - 1] !in BREAKS && text[end] !in BREAKS
}

private val BREAKS = setOf(' ', ' ', '-', '­', '‐', '/', '\n')
