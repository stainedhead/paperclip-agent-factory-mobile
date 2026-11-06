package com.stainedhead.paperclipmobile.presentation

/** Something the whole app must say before it shows anything else. */
enum class StartupNotice {
    /**
     * The real graph could not be opened (the factory database or the Keychain is not available right now). Nothing
     * was changed or deleted; the app must not fall back to fakes, so it says so and offers a retry.
     */
    LiveGraphUnavailable,
    ;

    companion object {
        /** The notice for a launch, or null when the app can run: demo data needs no live graph. */
        fun forLaunch(
            demo: Boolean,
            liveOpened: Boolean,
        ): StartupNotice? = if (demo || liveOpened) null else LiveGraphUnavailable
    }
}
