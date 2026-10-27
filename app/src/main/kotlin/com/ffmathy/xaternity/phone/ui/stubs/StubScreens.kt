package com.ffmathy.xaternity.phone.ui.stubs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ffmathy.xaternity.core.ui.icons.XIcon
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.phone.R
import com.ffmathy.xaternity.phone.ui.theme.NightLightTheme
import com.ffmathy.xaternity.core.R as CoreR

/** History (P3). The next stage builds the ribbon, stats and list here. */
@Composable
fun HistoryStubScreen(onBack: () -> Unit, windowInsets: WindowInsets = WindowInsets.safeDrawing) {
    StubScaffold(stringResource(CoreR.string.nav_history), XIcons.back, stringResource(R.string.cd_back), onBack, windowInsets)
}

/** Settings. The next stage builds Appearance, Language and the baby's name here. */
@Composable
fun SettingsStubScreen(onBack: () -> Unit, windowInsets: WindowInsets = WindowInsets.safeDrawing) {
    StubScaffold(stringResource(CoreR.string.nav_settings), XIcons.back, stringResource(R.string.cd_back), onBack, windowInsets)
}

/** The edit sheet (P4) entry point for [entryId]. The next stage replaces it with the sheet. */
@Composable
fun EditStubScreen(
    @Suppress("UNUSED_PARAMETER") entryId: String,
    onClose: () -> Unit,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    StubScaffold(stringResource(CoreR.string.action_edit), XIcons.close, stringResource(R.string.cd_close), onClose, windowInsets)
}

@Composable
private fun StubScaffold(
    title: String,
    navigationIcon: ImageVector,
    navigationLabel: String,
    onNavigate: () -> Unit,
    windowInsets: WindowInsets,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(windowInsets),
    ) {
        Row(
            Modifier
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onNavigate) {
                XIcon(navigationIcon, contentDescription = navigationLabel, tint = colors.onSurface)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            stringResource(R.string.stub_coming_next),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

@Preview(widthDp = 390, heightDp = 400)
@Composable
private fun HistoryStubPreview() {
    NightLightTheme(night = true) { HistoryStubScreen(onBack = {}) }
}
