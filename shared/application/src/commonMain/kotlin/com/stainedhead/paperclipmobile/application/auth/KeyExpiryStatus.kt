package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.domain.port.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

enum class KeyExpiryState {
    /** The key never expires. */
    NoExpiry,
    Valid,

    /** Five days or fewer remain (PRD FR-023). */
    ExpiringSoon,
    Expired,
}

data class KeyExpiry(
    val state: KeyExpiryState,
    /** Whole days left, rounded up; null when there is no expiry, 0 once expired. */
    val daysLeft: Int?,
)

private const val WARN_DAYS = 5

/** Tells when to warn that a board key is close to expiring (PRD FR-023). */
class KeyExpiryStatus(
    private val clock: Clock,
) {
    operator fun invoke(expiresAt: Instant?): KeyExpiry {
        if (expiresAt == null) return KeyExpiry(KeyExpiryState.NoExpiry, null)
        val remaining = expiresAt - clock.now()
        return when {
            remaining <= Duration.ZERO -> KeyExpiry(KeyExpiryState.Expired, 0)
            remaining <= WARN_DAYS.days -> KeyExpiry(KeyExpiryState.ExpiringSoon, daysLeft(remaining))
            else -> KeyExpiry(KeyExpiryState.Valid, daysLeft(remaining))
        }
    }

    private fun daysLeft(remaining: Duration): Int {
        val whole = remaining.inWholeDays.toInt()
        return if (remaining > whole.days) whole + 1 else whole
    }
}
