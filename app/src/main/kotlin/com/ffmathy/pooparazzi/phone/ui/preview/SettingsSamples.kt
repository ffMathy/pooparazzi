package com.ffmathy.pooparazzi.phone.ui.preview

import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.domain.ForecastKind
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.phone.ui.settings.AccountSummary
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsUiState
import java.time.LocalDate

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

    /** No watch at all: Settings › Watch says "No watch yet" and that Pooparazzi has a watch app. */
    fun noWatch(): SettingsUiState = state(sync = SyncStatus.Unavailable)

    /** A watch is connected but has no Pooparazzi: Settings › Watch offers "Install on your watch". */
    fun watchWithoutApp(): SettingsUiState = state(sync = SyncStatus.Unavailable).copy(watchWithoutApp = true)

    /** Noa's birth date given (14 Aug 2026), Settings › Babies with the date picker open on it. */
    fun birthDate(): SettingsUiState {
        val noa = SampleData.settings.babies.first().copy(birthDate = BIRTH_DATE.toString())
        return state(babies = listOf(noa)).copy(birthDateFor = noa)
    }

    /** The watch face shows the next feed and the next sleep, not the next poop (Settings › Watch). */
    fun predictions(): SettingsUiState = state().copy(watchForecasts = setOf(ForecastKind.FEED, ForecastKind.SLEEP))

    /** The prediction notifications' channels for [predictions]: the next poop turned off in Android's settings. */
    val predictionChannels: Map<ForecastKind, Boolean> = mapOf(ForecastKind.FEED to true, ForecastKind.POOP to false, ForecastKind.SLEEP to true)

    /** Noa's birth date in the samples. */
    val BIRTH_DATE: LocalDate = LocalDate.of(2026, 8, 14)

    /** "Remove Baby 3?" over Settings › Babies, signed in: the log goes for the whole family. */
    fun removeBaby(): SettingsUiState = babies().let { it.copy(removing = it.babies.last()) }
}
