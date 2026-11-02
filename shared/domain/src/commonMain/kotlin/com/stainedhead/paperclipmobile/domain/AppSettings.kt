package com.stainedhead.paperclipmobile.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val ONE_MINUTE = 1
private const val FIVE_MINUTES = 5
private const val FIFTEEN_MINUTES = 15
private const val THIRTY_MINUTES = 30
private const val ONE_HOUR = 60

/** How long the app may sit in the background before it asks for device authentication again (PRD FR-014). */
enum class IdlePeriod(
    val minutes: Int,
) {
    Immediately(0),
    OneMinute(ONE_MINUTE),
    FiveMinutes(FIVE_MINUTES),
    FifteenMinutes(FIFTEEN_MINUTES),
    ThirtyMinutes(THIRTY_MINUTES),
    OneHour(ONE_HOUR),
    ;

    val duration: Duration get() = minutes.minutes
}

/**
 * Preferences that apply across factories on this phone (PRD FR-017). Destructive-action authentication is not a
 * field: it cannot be turned off.
 */
data class AppSettings(
    val deviceName: String,
    val appLockEnabled: Boolean = true,
    val idlePeriod: IdlePeriod = IdlePeriod.FiveMinutes,
    val selectedInstanceId: InstanceId? = null,
) {
    companion object {
        const val MAX_DEVICE_NAME_LENGTH = 60
        const val DEFAULT_DEVICE_NAME = "My phone"
    }
}

/** When the app asks for device authentication to open (PRD FR-014). */
object AppLockPolicy {
    /** [backgroundedAt] is null on a cold launch, which always locks when the app lock is on. */
    fun shouldLock(
        settings: AppSettings,
        backgroundedAt: Instant?,
        now: Instant,
    ): Boolean =
        when {
            !settings.appLockEnabled -> false
            backgroundedAt == null -> true
            else -> now - backgroundedAt >= settings.idlePeriod.duration
        }
}
