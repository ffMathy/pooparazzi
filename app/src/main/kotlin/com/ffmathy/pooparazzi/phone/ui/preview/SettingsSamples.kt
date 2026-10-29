package com.ffmathy.pooparazzi.phone.ui.preview

import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.phone.ui.settings.AccountSummary
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsUiState

/** Settings as the reference scenario has them, and with several babies. */
object SettingsSamples {
    fun state(
        appearance: Appearance = Appearance.AUTO,
        language: AppLanguage = AppLanguage.SYSTEM,
        sync: SyncStatus = SyncStatus.Synced(SampleData.SYNCED_1431),
        babies: List<Baby> = SampleData.settings.babies,
        account: AccountSummary = AccountSummary.SignedIn("parent@example.com", CloudStatus.Synced(SampleData.SYNCED_1431)),
    ): SettingsUiState = SettingsUiState(
        babies = babies,
        appearance = appearance,
        language = language,
        sync = sync,
        zone = SampleData.ZONE,
        versionName = "0.1.0",
        account = account,
    )

    /** Twins: Noa and Ida, plus a third baby not named yet (its field shows "Baby 3"). */
    fun babies(): SettingsUiState = state(babies = SampleData.twinSettings.babies + Baby("sample-baby-3"))
}
