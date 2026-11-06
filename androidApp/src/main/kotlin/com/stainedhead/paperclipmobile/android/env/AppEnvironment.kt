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
import kotlinx.coroutines.launch

/**
 * Owns the shared graph and every long-lived holder for the running app (AND-010): the app shell, the first-run flow
 * and the Inbox that feeds the tab badge. [PaperclipApplication] creates one for the process, so a configuration
 * change never reopens the graph. Screens read [session]; nothing else closes the holders it owns.
 *
 * Opening can fail (a locked database, an unusable Keystore). The session then has no graph and no holders and carries
 * [StartupNotice.LiveGraphUnavailable]; [retryLaunch] opens it again, once per call. Nothing falls back to fakes.
 */
class GraphOpening(
    /** Where the graph is opened: a background dispatcher, or null for the calling thread. */
    val dispatcher: CoroutineDispatcher? = null,
    /** Runs on every session that opened a graph, before the screens see it (the debug launch's scripted answers). */
    val onOpened: (GraphSession) -> Unit = {},
)

class AppEnvironment(
    provider: AppGraphProvider,
    private val scope: CoroutineScope,
    /** True in a debug build only: "Try the demo factory" is offered and demo launch extras are honoured. */
    val demoEntryAvailable: Boolean,
    /** What the Diagnostics screen shows; it outlasts a replaced graph. */
    val diagnostics: DiagnosticsRecorder = DiagnosticsRecorder(UNKNOWN_VERSION),
    private val log: AppLog = AppLog.Off,
    /** How the graph is opened: the app opens it in the background; the default opens on the calling thread (tests). */
    opening: GraphOpening = GraphOpening(),
) {
    private val openOn = opening.dispatcher
    private val onOpened = opening.onOpened
    private val lock = Any()
    private var provider: AppGraphProvider = provider
    private var foreground = true
    private var nextId = 0
    private var generation = 0
    private var isShutDown = false
    private val mutableSession = MutableStateFlow(begin())

    /**
     * The session of the running app. While the graph is being opened in the background it is a session with no graph
     * and no notice ([GraphSession.isOpening]); the screen shows Loading. It then becomes the opened graph, or the
     * notice that says why it could not be (R-018).
     */
    val session: StateFlow<GraphSession> = mutableSession

    init {
        if (openOn != null) openInBackground(generation, this.provider, mutableSession.value.id)
    }

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
        synchronized(lock) {
            isShutDown = true
            closeSession(mutableSession.value)
        }
    }

    private fun replace(next: AppGraphProvider) {
        synchronized(lock) {
            closeSession(mutableSession.value)
            provider = next
            generation++
            mutableSession.value = begin()
            if (openOn != null) openInBackground(generation, next, mutableSession.value.id)
        }
    }

    /** The first session: the opened graph at once when there is no background dispatcher, else the opening one. */
    private fun begin(): GraphSession =
        if (openOn == null) {
            build(provider, nextId++).also { it.graph?.lifecycle?.setForeground(foreground) }
        } else {
            GraphSession(nextId++, provider.mode, null, null, scope, SessionOptions(diagnostics = diagnostics))
        }

    /**
     * Opens [using] on [openOn], off the main thread, then swaps it in for the opening session of [pendingId]. A result
     * that arrives after a newer open began, or after the shutdown, is closed instead of shown.
     */
    private fun openInBackground(
        generationAtStart: Int,
        using: AppGraphProvider,
        pendingId: Int,
    ) {
        val dispatcher = checkNotNull(openOn)
        scope.launch(dispatcher) {
            val id = synchronized(lock) { nextId++ }
            val built = build(using, id)
            synchronized(lock) {
                if (isShutDown || generationAtStart != generation || mutableSession.value.id != pendingId) {
                    closeSession(built)
                } else {
                    built.graph?.lifecycle?.setForeground(foreground)
                    closeSession(mutableSession.value)
                    mutableSession.value = built
                }
            }
        }
    }

    private fun closeSession(session: GraphSession) {
        session.close()
        session.graph?.close()
        // Nothing can finish on a closed graph: a deferred close would wait for ever (R-007).
        session.abandonDeferred()
    }

    /** Opens the graph of [using]; whatever it throws is `Unavailable`. */
    private fun build(
        using: AppGraphProvider,
        id: Int,
    ): GraphSession {
        val opened = runCatching { using.makeGraph() }.getOrElse { AndroidGraphOpen.Unavailable(it) }
        return when (opened) {
            is AndroidGraphOpen.Opened -> {
                recordOpen(GraphOpenOutcome.Opened)
                GraphSession(id, using.mode, opened.graph, null, scope, SessionOptions(diagnostics = diagnostics))
                    .also(onOpened)
            }

            is AndroidGraphOpen.Unavailable -> {
                recordOpen(opened.cause.toGraphOpenOutcome())
                val notice = StartupNotice.forLaunch(demo = using.mode == GraphMode.Demo, liveOpened = false)
                GraphSession(
                    id,
                    using.mode,
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
