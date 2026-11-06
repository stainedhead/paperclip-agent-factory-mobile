package com.stainedhead.paperclipmobile.application.settings

import com.stainedhead.paperclipmobile.application.auth.SignedOutMarker
import com.stainedhead.paperclipmobile.domain.AppLockPolicy
import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.AttestedReadResult
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IdlePeriod
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.port.AppSettingsStore
import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.SecretStore
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

/**
 * Opens the app lock for the active factory (PRD FR-014, ADR-146). With app lock on, a factory's board token is a
 * protected secret, so reading it already asks the device owner: when that read attests that it authenticated the
 * person, it IS the unlock and the person is asked once. Every other outcome falls back to the plain [UnlockApp]
 * prompt, so the lock never opens without the owner: no active factory, no token, an unprotected token (app lock was
 * turned on after pairing) and any read that is not an authentication (a cached token is never one). A refused key
 * prompt keeps the app locked. An invalidated key marks the factory for re-pairing and still asks the owner, so a new
 * fingerprint cannot lock the person out of the app they need to re-pair in.
 */
class UnlockSession(
    private val plain: UnlockApp,
    private val secrets: SecretStore,
    private val marker: SignedOutMarker,
) {
    suspend operator fun invoke(active: InstanceId?): BiometricResult {
        if (active == null) return plain()
        return when (val read = secrets.readAttested(SecretKey(active, SecretName.BoardToken), REASON)) {
            is AttestedReadResult.Authenticated -> {
                read.secret.wipe()
                BiometricResult.Approved
            }

            AttestedReadResult.Denied -> {
                BiometricResult.Cancelled
            }

            AttestedReadResult.Invalidated -> {
                marker.noteIfUnauthorized(active, DomainResult.Failure(DomainError.KeyInvalidated))
                plain()
            }

            AttestedReadResult.NotAttested -> {
                plain()
            }
        }
    }

    private companion object {
        const val REASON = "unlock paperclip"
    }
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
