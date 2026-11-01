package com.stainedhead.paperclipmobile.application.refresh

import com.stainedhead.paperclipmobile.domain.Invalidation
import com.stainedhead.paperclipmobile.domain.InvalidationRules
import com.stainedhead.paperclipmobile.domain.port.AppLifecycle
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import com.stainedhead.paperclipmobile.domain.port.LiveEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** A screen the policy can refresh: it names how often (0 for never), how to refresh, and what makes it stale. */
interface RefreshTarget {
    /** Seconds between refreshes while the screen is visible; 0 means no timer (a finished run). Changes restart it. */
    val refreshIntervalSeconds: Flow<Int>

    fun refreshNow()

    /** True when [invalidation] means this screen's data is out of date (PRD FR-091). */
    fun isAffectedBy(invalidation: Invalidation): Boolean
}

/** A running refresh policy. Call [stop] when the screen goes away. */
class RefreshSession internal constructor(
    private val job: Job,
) {
    fun stop() {
        job.cancel()
    }
}

/**
 * Drives refresh of the visible screen (PRD FR-090, FR-091, ADR-027): every [RefreshTarget.refreshIntervalSeconds]
 * (15 s, 5 s while a run is running, none for a finished run), once at once when the app returns to the foreground or
 * the phone comes back online, and when a live event invalidates the screen. It does nothing in the background or
 * offline. Timers use coroutine time so tests run in virtual time; the `Clock` port is for timestamps only.
 */
class RefreshVisibleScreen(
    private val connectivity: Connectivity,
    private val lifecycle: AppLifecycle,
    private val liveEvents: LiveEvents,
) {
    operator fun invoke(
        scope: CoroutineScope,
        target: RefreshTarget,
    ): RefreshSession {
        val active =
            combine(connectivity.isOnline, lifecycle.isForeground) { online, foreground -> online && foreground }
                .distinctUntilChanged()
        val job =
            scope.launch {
                launch { refreshOnTimer(active, target) }
                launch { refreshOnEvents(active, target) }
            }
        return RefreshSession(job)
    }

    private suspend fun refreshOnTimer(
        active: Flow<Boolean>,
        target: RefreshTarget,
    ) {
        var seen = false
        active.collectLatest { isActive ->
            val resumed = seen && isActive
            seen = true
            if (!isActive) return@collectLatest
            if (resumed) target.refreshNow()
            target.refreshIntervalSeconds.distinctUntilChanged().collectLatest { seconds ->
                while (seconds > 0) {
                    delay(seconds.seconds)
                    target.refreshNow()
                }
            }
        }
    }

    private suspend fun refreshOnEvents(
        active: Flow<Boolean>,
        target: RefreshTarget,
    ) {
        active.collectLatest { isActive ->
            if (!isActive) return@collectLatest
            liveEvents.events
                .transform { event -> InvalidationRules.invalidationsFor(event).forEach { emit(it) } }
                .filter { target.isAffectedBy(it) }
                .conflate()
                .collect {
                    target.refreshNow()
                    delay(EVENT_THROTTLE)
                }
        }
    }

    private companion object {
        val EVENT_THROTTLE = 1.seconds
    }
}
