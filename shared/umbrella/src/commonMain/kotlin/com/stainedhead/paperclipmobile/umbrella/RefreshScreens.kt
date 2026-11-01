package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.application.refresh.RefreshSession
import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.application.refresh.RefreshVisibleScreen
import com.stainedhead.paperclipmobile.domain.port.AppLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.Koin

/**
 * Starts the refresh policy for a visible screen (PRD FR-090, FR-091). Pass a state holder's `refreshTarget`; call
 * `stop()` on the session when the screen goes away. Nothing refreshes in the background or offline.
 */
class RefreshScreens internal constructor(
    private val koin: Koin,
) {
    fun start(target: RefreshTarget): RefreshSession = koin.get<RefreshVisibleScreen>()(newScreenScope(), target)
}

/**
 * The app's foreground state, reported by the platform shell (scene phase on iOS, process lifecycle on Android).
 * Call [enterBackground] and [enterForeground] next to the app lock's `WentToBackground` and `CameToForeground`.
 */
class AppLifecycleBridge : AppLifecycle {
    private val foreground = MutableStateFlow(true)

    override val isForeground: StateFlow<Boolean> = foreground

    fun enterBackground() {
        foreground.value = false
    }

    fun enterForeground() {
        foreground.value = true
    }
}
