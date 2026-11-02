package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.domain.Invalidation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What a state holder tells the refresh policy about its screen. It reads the interval from the screen's own state
 * (so a run that starts or finishes changes it), refreshes through the holder's `Refresh` intent, and answers which
 * live events make the screen stale (PRD FR-091). The platform passes it to `graph.refresh.start`.
 */
internal class ScreenRefreshTarget<S>(
    state: StateFlow<S>,
    interval: (S) -> Int,
    private val refresh: () -> Unit,
    private val affectedBy: (Invalidation) -> Boolean,
) : RefreshTarget {
    override val refreshIntervalSeconds: Flow<Int> = state.map(interval).distinctUntilChanged()

    override fun refreshNow() = refresh()

    override fun isAffectedBy(invalidation: Invalidation) = affectedBy(invalidation)
}
