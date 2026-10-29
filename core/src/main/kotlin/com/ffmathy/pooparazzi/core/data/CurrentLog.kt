package com.ffmathy.pooparazzi.core.data

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The settings with the log of their current baby ([entries]) and of every baby ([allEntries]), oldest first.
 *
 * Both logs are taken from the same settings value, so a switch of baby never pairs the new baby's label with the
 * previous baby's log, not even for one frame (as combining [EntryRepository.observeEntries], which follows the current
 * baby on its own, with the settings would).
 */
data class CurrentLog(val settings: Settings, val entries: List<Entry>, val allEntries: List<Entry>)

/** [CurrentLog] for every change of [settings] or of any entry. */
fun EntryRepository.observeCurrentLog(settings: Flow<Settings>): Flow<CurrentLog> =
    combine(settings, observeAllEntries()) { s, all ->
        val babyId = s.selectedBaby.id
        CurrentLog(s, all.filter { it.babyId == babyId }, all)
    }.distinctUntilChanged()
