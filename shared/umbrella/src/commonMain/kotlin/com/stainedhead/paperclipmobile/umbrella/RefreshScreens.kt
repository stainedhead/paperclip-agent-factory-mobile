package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.application.refresh.RefreshSession
import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.application.refresh.RefreshVisibleScreen
import com.stainedhead.paperclipmobile.domain.port.AppLifecycle
import com.stainedhead.paperclipmobile.presentation.AppIntent
import com.stainedhead.paperclipmobile.presentation.AppStateHolder
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
 *
 * One call per scene change is enough: [setForeground] stops or resumes the refresh policy AND, for the app shell
 * made by [PaperclipGraph.createAppStateHolder], reports `WentToBackground` / `CameToForeground` to the app lock
 * (PRD FR-014, FR-090). [enterBackground] and [enterForeground] are the same calls spelled out. A shell that never
 * created an app state holder simply has no lock to tell.
 */
class AppLifecycleBridge : AppLifecycle {
    private val foreground = MutableStateFlow(true)
    private val shell = MutableStateFlow<AppStateHolder?>(null)

    override val isForeground: StateFlow<Boolean> = foreground

    /** Called by the graph for the app shell it creates; a later holder replaces an earlier one. */
    internal fun attach(holder: AppStateHolder) {
        shell.value = holder
    }

    fun setForeground(isForeground: Boolean) {
        foreground.value = isForeground
        shell.value?.onIntent(if (isForeground) AppIntent.CameToForeground else AppIntent.WentToBackground)
    }

    fun enterBackground() = setForeground(false)

    fun enterForeground() = setForeground(true)
}
