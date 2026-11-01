package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.IdlePeriod
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.AppSettingsStore
import kotlinx.coroutines.flow.first

/** Behaviour every [AppSettingsStore] must have (PRD FR-017). Real adapters subclass this in Phase 1b. */
abstract class AppSettingsStoreContract {
    abstract fun create(): AppSettingsStore

    suspend fun runAll() {
        savedSettingsAreObserved()
        theLatestSaveWins()
    }

    private suspend fun savedSettingsAreObserved() {
        val store = create()
        val settings = AppSettings("Pixel", appLockEnabled = false, IdlePeriod.OneHour, InstanceId("inst-1"))
        store.save(settings)
        check(store.observe().first() == settings) { "observe must emit what was saved" }
    }

    private suspend fun theLatestSaveWins() {
        val store = create()
        store.save(AppSettings("One"))
        store.save(AppSettings("Two"))
        check(store.observe().first().deviceName == "Two") { "the latest save must win" }
    }
}
