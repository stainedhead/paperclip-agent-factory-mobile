package com.stainedhead.paperclipmobile.application.settings

import com.stainedhead.paperclipmobile.domain.AppLockPolicy
import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.IdlePeriod
import com.stainedhead.paperclipmobile.domain.port.AppSettingsStore
import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.time.Instant

class ObserveAppSettings(
    private val store: AppSettingsStore,
) {
    operator fun invoke(): Flow<AppSettings> = store.observe()
}

/**
 * The changes the settings screen can make (PRD FR-017). There is deliberately no field for destructive-action
 * authentication: it cannot be turned off.
 */
data class AppSettingsUpdate(
    val deviceName: String? = null,
    val appLockEnabled: Boolean? = null,
    val idlePeriod: IdlePeriod? = null,
)

sealed interface UpdateSettingsResult {
    data class Updated(
        val settings: AppSettings,
    ) : UpdateSettingsResult

    data object InvalidDeviceName : UpdateSettingsResult
}

class UpdateAppSettings(
    private val store: AppSettingsStore,
) {
    suspend operator fun invoke(update: AppSettingsUpdate): UpdateSettingsResult {
        val current = store.observe().first()
        val name = update.deviceName?.trim() ?: current.deviceName
        if (name.isEmpty() ||
            name.length > AppSettings.MAX_DEVICE_NAME_LENGTH
        ) {
            return UpdateSettingsResult.InvalidDeviceName
        }
        val updated =
            current.copy(
                deviceName = name,
                appLockEnabled = update.appLockEnabled ?: current.appLockEnabled,
                idlePeriod = update.idlePeriod ?: current.idlePeriod,
            )
        store.save(updated)
        return UpdateSettingsResult.Updated(updated)
    }
}

/** Asks the device owner to open the app (PRD FR-014). */
class UnlockApp(
    private val biometrics: Biometrics,
) {
    suspend operator fun invoke(): BiometricResult = biometrics.authenticate(AuthReason.UnlockApp)
}

/** Whether the app must ask for device authentication now (PRD FR-014). */
class ShouldLockApp(
    private val clock: Clock,
) {
    operator fun invoke(
        settings: AppSettings,
        backgroundedAt: Instant?,
    ): Boolean = AppLockPolicy.shouldLock(settings, backgroundedAt, clock.now())
}
