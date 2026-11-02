package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.port.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * A clock that only moves when told. Pass [elapsedMillis] (for example `{ testScheduler.currentTime }`) to tie it to
 * the coroutine test scheduler so `delay()` and `now()` agree.
 */
class FakeClock(
    private val start: Instant = Instant.fromEpochSeconds(START_EPOCH_SECONDS),
    private val elapsedMillis: () -> Long = { 0L },
) : Clock {
    private var offset: Duration = Duration.ZERO

    override fun now(): Instant = start + elapsedMillis().milliseconds + offset

    fun advance(duration: Duration) {
        offset += duration
    }

    companion object {
        /** 2026-11-01T12:00:00Z. */
        const val START_EPOCH_SECONDS = 1_793_534_400L
    }
}
