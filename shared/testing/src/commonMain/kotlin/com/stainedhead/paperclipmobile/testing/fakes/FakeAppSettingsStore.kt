package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.port.AppSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppSettingsStore(
    initial: AppSettings = AppSettings(deviceName = "Test phone"),
) : AppSettingsStore {
    private val settings = MutableStateFlow(initial)

    var saveCalls = 0
        private set

    val current: AppSettings get() = settings.value

    override fun observe(): Flow<AppSettings> = settings

    override suspend fun save(settings: AppSettings) {
        saveCalls += 1
        this.settings.value = settings
    }
}
