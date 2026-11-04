package com.stainedhead.paperclipmobile.android.env

import android.content.Intent

/**
 * What the process was launched with (AND-010). Everything here is for demo mode and tests; none of it changes how a
 * real factory behaves.
 *
 * - `demo` (boolean extra): run on the demo graph, the design mocks' data on shared fakes.
 * - `demo_first_run` (boolean extra): fakes that start with no factory and approve every pairing (the first-run flow).
 *
 * Start it with `adb shell am start -n com.stainedhead.paperclipmobile/.MainActivity --ez demo true`. A release build
 * ignores both extras, so a shipped app cannot be steered onto fakes (the iPhone app has the same rule).
 */
data class LaunchConfiguration(
    val useDemo: Boolean = false,
    val useFirstRunDemo: Boolean = false,
) {
    companion object {
        const val EXTRA_DEMO = "demo"
        const val EXTRA_DEMO_FIRST_RUN = "demo_first_run"

        /** The configuration for the extras that are set to true; [allowsDemoControls] is false in a release build. */
        fun from(
            trueExtras: Set<String>,
            allowsDemoControls: Boolean,
        ): LaunchConfiguration =
            if (allowsDemoControls) {
                LaunchConfiguration(
                    useDemo = EXTRA_DEMO in trueExtras,
                    useFirstRunDemo = EXTRA_DEMO_FIRST_RUN in trueExtras,
                )
            } else {
                LaunchConfiguration()
            }

        fun from(
            intent: Intent?,
            allowsDemoControls: Boolean,
        ): LaunchConfiguration =
            from(
                setOf(EXTRA_DEMO, EXTRA_DEMO_FIRST_RUN).filterTo(mutableSetOf()) {
                    intent?.getBooleanExtra(it, false) == true
                },
                allowsDemoControls,
            )
    }
}
