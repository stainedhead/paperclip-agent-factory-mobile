package com.stainedhead.paperclipmobile.umbrella

import android.content.Context
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser

/** What opening the Android live graph came to (AND-004): the graph, or why there is none. */
sealed interface AndroidGraphOpen {
    /** The graph is open; the app closes it when it is done. */
    data class Opened(
        val graph: PaperclipGraph,
    ) : AndroidGraphOpen

    /**
     * No graph could be built (the database is not available right now, the Keystore cannot be opened). Nothing is
     * left open and nothing was changed; the app shows `StartupNotice.LiveGraphUnavailable` with a Try again, and
     * never falls back to fakes (ADR-084, ADR-120).
     */
    data class Unavailable(
        val cause: Throwable,
    ) : AndroidGraphOpen
}

/**
 * The Android entry point to the live graph, the counterpart of `LiveIosGraph`. It reports a failure to build the graph
 * as a value, so a locked database or an unusable Keystore shows a retry screen instead of aborting every launch.
 */
object AndroidLiveGraph {
    /**
     * Builds the graph on the phone's real device services: the Keystore, the system authentication prompt (shown in
     * the activity registered with [AndroidAuthHost]), the system network monitor and the database in
     * `noBackupFilesDir`.
     *
     * @param allowInsecureLocal true only in debug builds; lets `http://localhost` and `http://10.0.2.2` be used.
     */
    fun openLiveGraph(
        context: Context,
        allowInsecureLocal: Boolean,
        pairingBrowser: PairingBrowser,
        settings: AndroidLiveSettings,
    ): AndroidGraphOpen =
        open(allowInsecureLocal, pairingBrowser) { androidLivePlatform(context, settings, AndroidAuthHost) }

    /** The seam tests use: the platform comes from [platform], and whatever it or the graph throws is `Unavailable`. */
    internal fun open(
        allowInsecureLocal: Boolean,
        pairingBrowser: PairingBrowser,
        platform: () -> LivePlatform,
    ): AndroidGraphOpen =
        runCatching { PaperclipCore.createLiveGraph(allowInsecureLocal, pairingBrowser, platform()) }
            .fold(
                onSuccess = { AndroidGraphOpen.Opened(it) },
                onFailure = { AndroidGraphOpen.Unavailable(it) },
            )
}
