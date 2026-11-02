package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.application.ValidateInstanceAddress
import com.stainedhead.paperclipmobile.presentation.FirstRunStateHolder
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** The composition root. Platform apps create one graph and ask it for screen state holders. */
class PaperclipGraph internal constructor(
    private val koin: Koin,
) {
    /** Creates a new state holder. The screen that shows it owns it and keeps the same instance while it is visible. */
    fun newFirstRunStateHolder(): FirstRunStateHolder = koin.get()
}

object PaperclipCore {
    /**
     * @param allowInsecureLocal true only in debug builds; lets `http://localhost` and `http://10.0.2.2` be added.
     */
    fun createGraph(allowInsecureLocal: Boolean): PaperclipGraph {
        // An isolated Koin application, not the global one, so graphs never share state (and tests stay independent).
        val application = koinApplication { modules(coreModule(allowInsecureLocal)) }
        return PaperclipGraph(application.koin)
    }

    private fun coreModule(allowInsecureLocal: Boolean) =
        module {
            single { ValidateInstanceAddress(allowInsecureLocal) }
            factory { FirstRunStateHolder(get()) }
        }
}
