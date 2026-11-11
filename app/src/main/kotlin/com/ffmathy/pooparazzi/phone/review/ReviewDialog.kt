package com.ffmathy.pooparazzi.phone.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme

/**
 * "Enjoying Pooparazzi?" (DESIGN_SPEC §3.3.1): asks for a review on Google Play once the app is in real use
 * ([ReviewRule]). A filled "Rate on Google Play" over "Not now" and "Don't ask again", stacked full width so the long
 * Danish labels fit. Back or a tap outside counts as "Not now".
 */
@Composable
fun ReviewDialog(onRate: () -> Unit, onLater: () -> Unit, onNever: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.review_title)) },
        text = { Text(stringResource(R.string.review_body)) },
        confirmButton = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(onClick = onRate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.review_rate))
                }
                TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.review_later), color = colors.onSurface)
                }
                TextButton(onClick = onNever, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.review_never), color = colors.onSurfaceVariant)
                }
            }
        },
        containerColor = colors.surfaceContainerHigh,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
    )
}

@Preview(name = "Review · Day")
@Composable
private fun ReviewDialogPreview() {
    NightLightTheme(night = false) { ReviewDialog({}, {}, {}) }
}
