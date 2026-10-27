package com.ffmathy.xaternity.phone.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.phone.R
import com.ffmathy.xaternity.phone.ui.components.XTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A bundled font and its licence text (`third_party/fonts`, packaged as assets under `fonts/`). */
data class FontLicence(val name: String, val text: String)

/** The fonts the phone bundles (DESIGN_SPEC §10.12), both under the SIL Open Font License 1.1. */
internal val BundledFonts = listOf(
    "Google Sans Flex" to "fonts/GoogleSansFlex-OFL.txt",
    "Atkinson Hyperlegible Next" to "fonts/AtkinsonHyperlegibleNext-OFL.txt",
)

internal fun readLicences(context: Context): List<FontLicence> = BundledFonts.map { (name, path) ->
    FontLicence(name, context.assets.open(path).bufferedReader().use { it.readText() }.trim())
}

/** Font licences: the full OFL text of each bundled font (read off the main thread). */
@Composable
fun LicencesRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val licences by produceState(emptyList<FontLicence>(), context) {
        value = withContext(Dispatchers.IO) { readLicences(context) }
    }
    LicencesScreen(licences, onBack)
}

@Composable
fun LicencesScreen(licences: List<FontLicence>, onBack: () -> Unit, windowInsets: WindowInsets = WindowInsets.safeDrawing) {
    val colors = MaterialTheme.colorScheme
    val bottom = with(LocalDensity.current) { windowInsets.getBottom(this).toDp() }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        XTopBar(
            title = stringResource(R.string.settings_font_licences),
            navigationIcon = XIcons.back,
            navigationLabel = stringResource(R.string.cd_back),
            onNavigate = onBack,
        )
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom + 24.dp),
        ) {
            items(licences, key = { it.name }) { licence ->
                Text(
                    licence.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 8.dp)
                        .semantics { heading() },
                )
                Text(licence.text, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
