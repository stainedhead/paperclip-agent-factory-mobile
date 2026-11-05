package com.stainedhead.paperclipmobile.android.env

import com.stainedhead.paperclipmobile.android.diagnostics.AppLog
import com.stainedhead.paperclipmobile.android.diagnostics.DiagnosticsRecorder
import com.stainedhead.paperclipmobile.android.diagnostics.GraphOpenOutcome
import com.stainedhead.paperclipmobile.android.diagnostics.toGraphOpenOutcome
import com.stainedhead.paperclipmobile.presentation.StartupNotice
import com.stainedhead.paperclipmobile.umbrella.AndroidGraphOpen
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the shared graph and every long-lived holder for the running app (AND-010): the app shell, the first-run flow
 * and the Inbox that feeds the tab badge. [PaperclipApplication] creates one for the process, so a configuration
 * change never reopens the graph. Screens read [session]; nothing else closes the holders it owns.
 *
 * Opening can fail (a locked database, an unusable Keystore). The session then has no graph and no holders and carries
 * [StartupNotice.LiveGraphUnavailable]; [retryLaunch] opens it again, once per call. Nothing falls back to fakes.
 */
class AppEnvironment(
    provider: AppGraphProvider,
    private val scope: CoroutineScope,
    /** True in a debug build only: "Try the demo factory" is offered and demo launch extras are honoured. */
    val demoEntryAvailable: Boolean,
    /** What the Diagnostics screen shows; it outlasts a replaced graph. */
    val diagnostics: DiagnosticsRecorder = DiagnosticsRecorder(UNKNOWN_VERSION),
    private val log: AppLog = AppLog.Off,
    /** Where the graph is opened: the app gives a background dispatcher; null opens on the calling thread (tests). */
    private val openOn: CoroutineDispatcher? = null,
) {
    private val lock = Any()
    private var provider: AppGraphProvider = provider
    private var foreground = true
    private var nextId = 0
    private val mutableSession = MutableStateFlow(open())

    val session: StateFlow<GraphSession> = mutableSession

    /** "Try again" on the startup notice: opens the real graph again. */
    fun retryLaunch() = replace(provider)

    /** "Try the demo factory" on the first-run screen: the same app, now on the demo graph. */
    fun useDemo() {
        check(demoEntryAvailable) { "Demo mode is for debug builds" }
        replace(DemoGraphProvider())
    }

    /** The process came to the foreground (true) or went to the background (false). Refresh and the lock follow. */
    fun setForeground(isForeground: Boolean) {
        synchronized(lock) {
            foreground = isForeground
            mutableSession.value.graph
                ?.lifecycle
                ?.setForeground(isForeground)
        }
    }

    /** Closes every holder and the graph, once. Nothing may be used afterwards. */
    fun shutdown() {
        synchronized(lock) { closeSession(mutableSession.value) }
    }

    private fun replace(next: AppGraphProvider) {
        synchronized(lock) {
            closeSession(mutableSession.value)
            provider = next
            mutableSession.value = open()
        }
    }

    private fun closeSession(session: GraphSession) {
        session.close()
        session.graph?.close()
        // Nothing can finish on a closed graph: a deferred close would wait for ever (R-007).
        session.abandonDeferred()
    }

    /** Opens the provider's graph; whatever it throws is `Unavailable`. A foreground flag set earlier is re-applied. */
    private fun open(): GraphSession {
        val opened = runCatching { provider.makeGraph() }.getOrElse { AndroidGraphOpen.Unavailable(it) }
        val id = nextId++
        return when (opened) {
            is AndroidGraphOpen.Opened -> {
                opened.graph.lifecycle.setForeground(foreground)
                recordOpen(GraphOpenOutcome.Opened)
                GraphSession(id, provider.mode, opened.graph, null, scope, SessionOptions(diagnostics = diagnostics))
            }

            is AndroidGraphOpen.Unavailable -> {
                recordOpen(opened.cause.toGraphOpenOutcome())
                val notice = StartupNotice.forLaunch(demo = provider.mode == GraphMode.Demo, liveOpened = false)
                GraphSession(
                    id,
                    provider.mode,
                    null,
                    notice ?: StartupNotice.LiveGraphUnavailable,
                    scope,
                    SessionOptions(diagnostics = diagnostics),
                )
            }
        }
    }

    private fun recordOpen(outcome: GraphOpenOutcome) {
        diagnostics.recordGraphOpen(outcome)
        log.debug("graph", "open finished", outcome)
    }
}

private const val UNKNOWN_VERSION = "0.0.0"
