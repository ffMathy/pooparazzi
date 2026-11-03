package com.ffmathy.pooparazzi.phone.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.account.AppAccounts
import com.ffmathy.pooparazzi.phone.ui.account.PrimaryButton
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The first run's "Your baby" screen wired to the settings: Continue saves what was given (the name and the birth
 * date, each only when given) for the first baby, in the app's scope, and [onDone]; Skip and Back save nothing and
 * [onDone]. Either way it is not asked again on this phone.
 */
@Composable
fun BabyIntroRoute(onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val signedIn = AppAccounts.accounts.user.value != null
    BackHandler { done() }
    BabyIntroScreen(
        signedIn = signedIn,
        onContinue = { name, born ->
            val clean = Settings.sanitizeName(name)
            if (clean.isNotEmpty() || born != null) {
                Core.graph.scope.launch {
                    val baby = Core.settings.current().babies.first()
                    if (clean.isNotEmpty()) Core.settings.renameBaby(baby.id, clean)
                    if (born != null) Core.settings.setBabyBirthDate(baby.id, born)
                }
            }
            done()
        },
        onSkip = { done() },
    )
}

/**
 * "Your baby" (BABIES_AND_BACKUP §2.4), after the first run's Welcome: the baby's name and birth date, both optional
 * and said to be so ("You can skip both, and add them later in Settings."). Under the birth date, why it helps: the
 * predictions and estimates then follow the baby's age. Then where names and birth dates go ([signedIn]: the family
 * and the backup; otherwise this phone and the watch), Continue, and Skip.
 */
@Composable
fun BabyIntroScreen(
    signedIn: Boolean,
    onContinue: (name: String, birthDate: LocalDate?) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    initialName: String = "",
    initialBirthDate: LocalDate? = null,
) {
    val colors = MaterialTheme.colorScheme
    val bottomInset = with(LocalDensity.current) { windowInsets.getBottom(this).toDp() }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var born by rememberSaveable { mutableStateOf(initialBirthDate?.toString()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val date = born?.let { LocalDate.parse(it) }
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 32.dp, bottom = 24.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.baby_intro_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.baby_intro_optional), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        NameField(
            name = name,
            placeholder = stringResource(com.ffmathy.pooparazzi.core.R.string.baby_name_hint),
            onChange = { name = it.take(Settings.MAX_BABY_NAME_LENGTH) },
            onDone = {},
        )
        BirthDatePill(date, onClick = { picking = true })
        Text(
            stringResource(R.string.settings_add_baby_birth_date_why),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
        )
        Text(
            stringResource(if (signedIn) R.string.settings_babies_hint_family else R.string.settings_babies_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton(stringResource(R.string.baby_intro_continue), onClick = { onContinue(name, date) }, enabled = true)
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.baby_intro_skip), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
        }
    }
    if (picking) {
        BirthDateDialog(
            initial = date,
            onPick = {
                born = it?.toString()
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
