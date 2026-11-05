package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.application.auth.SessionTokens
import com.stainedhead.paperclipmobile.application.refresh.RefreshSession
import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.application.refresh.RefreshVisibleScreen
import com.stainedhead.paperclipmobile.domain.port.AppLifecycle
import com.stainedhead.paperclipmobile.presentation.AppIntent
import com.stainedhead.paperclipmobile.presentation.AppStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
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
class AppLifecycleBridge internal constructor(
    private val tokens: SessionTokens,
) : AppLifecycle {
    private val foreground = MutableStateFlow(true)
    private val shell = MutableStateFlow<AppStateHolder?>(null)

    override val isForeground: StateFlow<Boolean> = foreground

    /** Called by the graph for the app shell it creates; a later holder replaces an earlier one. */
    internal fun attach(holder: AppStateHolder) {
        // A new shell starts locked (ADR-145): whatever token an earlier shell warmed does not outlive its lock.
        tokens.forgetAll()
        shell.value = holder
    }

    fun setForeground(isForeground: Boolean) {
        // The board token held for the unlock session ends with the foreground, before the lock is told (ADR-145), and
        // again when the app returns, for a read that began while it was away. A signal that changes nothing (the
        // scene turning active after a system sheet) must not drop it, or every prompt would empty the cache.
        val changed = foreground.getAndUpdate { isForeground } != isForeground
        if (changed) tokens.forgetAll()
        shell.value?.onIntent(if (isForeground) AppIntent.CameToForeground else AppIntent.WentToBackground)
    }

    fun enterBackground() = setForeground(false)

    fun enterForeground() = setForeground(true)
}
