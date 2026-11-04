package com.stainedhead.paperclipmobile.android.env

import android.content.Context
import com.stainedhead.paperclipmobile.android.BuildConfig
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.umbrella.AndroidGraphOpen
import com.stainedhead.paperclipmobile.umbrella.AndroidLiveGraph
import com.stainedhead.paperclipmobile.umbrella.AndroidLiveSettings
import com.stainedhead.paperclipmobile.umbrella.PaperclipCore

enum class GraphMode {
    Demo,
    Live,
}

/**
 * The one seam between the app and the shared graph. Screens and models never call `PaperclipCore` themselves; the
 * environment asks a provider, so switching from the demo to a real factory changes one type, not any screen.
 */
interface AppGraphProvider {
    val mode: GraphMode

    /**
     * Builds the graph. The real one can fail (the factory database is busy, the Keystore cannot be opened); the
     * environment then shows a notice and offers a retry, and never falls back to fakes.
     */
    fun makeGraph(): AndroidGraphOpen
}

/** The demo graph: the design mocks' data on shared fakes, no server. */
class DemoGraphProvider : AppGraphProvider {
    override val mode = GraphMode.Demo

    override fun makeGraph(): AndroidGraphOpen = AndroidGraphOpen.Opened(PaperclipCore.createDemoGraph())
}

/** Demo ports with no factory yet and a person who approves every pairing at once: the first-run flow on fakes. */
class FirstRunDemoGraphProvider : AppGraphProvider {
    override val mode = GraphMode.Demo

    override fun makeGraph(): AndroidGraphOpen = AndroidGraphOpen.Opened(PaperclipCore.createFirstRunDemoGraph())
}

/**
 * The real graph: the Paperclip REST API, the Keystore, `BiometricPrompt`, the system network monitor and the factory
 * database, through the shared live graph. Opening can fail; that is a value to show, not an abort.
 */
class LiveGraphProvider(
    private val context: Context,
    private val pairingBrowser: PairingBrowser,
    private val allowInsecureLocal: Boolean = BuildConfig.DEBUG,
) : AppGraphProvider {
    override val mode = GraphMode.Live

    override fun makeGraph(): AndroidGraphOpen =
        AndroidLiveGraph.openLiveGraph(
            context = context,
            allowInsecureLocal = allowInsecureLocal,
            pairingBrowser = pairingBrowser,
            settings =
                AndroidLiveSettings(
                    unlockReason = context.getString(R.string.biometric_unlock_reason),
                    confirmReason = context.getString(R.string.biometric_confirm_reason),
                    writeReason = context.getString(R.string.biometric_write_reason),
                ),
        )
}
