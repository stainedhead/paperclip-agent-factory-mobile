package com.stainedhead.paperclipmobile.infrastructure.persistence

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.AppSettingsStore
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.App_settings
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.PaperclipDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [AppSettingsStore] over one SQLite row (AD-021). With no row yet it emits the defaults at once, so a first launch
 * never waits for a save. Nothing here is secret.
 */
class SqlAppSettingsStore internal constructor(
    private val db: PaperclipDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AppSettingsStore {
    private val queries get() = db.appSettingsQueries

    override fun observe(): Flow<AppSettings> =
        queries
            .select()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toSettings() ?: AppSettings(deviceName = AppSettings.DEFAULT_DEVICE_NAME) }

    override suspend fun save(settings: AppSettings) {
        withContext(dispatcher) {
            queries.upsert(
                settings.deviceName,
                if (settings.appLockEnabled) 1L else 0L,
                settings.idlePeriod.name,
                settings.selectedInstanceId?.value,
            )
        }
    }

    private fun App_settings.toSettings() =
        AppSettings(
            deviceName = device_name,
            appLockEnabled = app_lock_enabled != 0L,
            idlePeriod = enumOrDefault(idle_period, AppSettings(device_name).idlePeriod),
            selectedInstanceId = selected_instance_id?.let { InstanceId(it) },
        )
}
