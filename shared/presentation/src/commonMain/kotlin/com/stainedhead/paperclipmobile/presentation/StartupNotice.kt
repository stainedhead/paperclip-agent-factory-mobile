package com.stainedhead.paperclipmobile.presentation

/** Something the whole app must say before it shows anything else. */
enum class StartupNotice {
    /** This build cannot reach real factories yet; it must not pretend to, or ask for a pairing it cannot keep. */
    LiveFactoriesUnavailable,
    ;

    companion object {
        /** The notice for a launch, or null when the app can run: demo data needs no live adapters. */
        fun forLaunch(
            demo: Boolean,
            liveAvailable: Boolean,
        ): StartupNotice? = if (demo || liveAvailable) null else LiveFactoriesUnavailable
    }
}
