package com.ffmathy.xaternity.phone.ui.preview

import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.core.settings.AppLanguage
import com.ffmathy.xaternity.core.settings.Appearance
import com.ffmathy.xaternity.core.sync.SyncStatus
import com.ffmathy.xaternity.phone.ui.settings.SettingsUiState

/** Settings as the reference scenario has them. */
object SettingsSamples {
    fun state(
        appearance: Appearance = Appearance.AUTO,
        language: AppLanguage = AppLanguage.SYSTEM,
        sync: SyncStatus = SyncStatus.Synced(SampleData.SYNCED_1431),
    ): SettingsUiState = SettingsUiState(
        babyName = SampleData.BABY_NAME,
        appearance = appearance,
        language = language,
        sync = sync,
        zone = SampleData.ZONE,
        versionName = "0.1.0",
    )
}
