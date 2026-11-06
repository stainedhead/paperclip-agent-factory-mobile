package com.stainedhead.paperclipmobile.android.env

import android.content.Intent
import com.stainedhead.paperclipmobile.domain.port.BiometricResult

/**
 * What the process was launched with (AND-010). Everything here is for demo mode and tests; none of it changes how a
 * real factory behaves.
 *
 * - `demo` (boolean extra): run on the demo graph, the design mocks' data on shared fakes.
 * - `demo_first_run` (boolean extra): fakes that start with no factory and approve every pairing (the first-run flow).
 * - `demo_biometrics` (string extra, `cancelled,failed,approved`): the device-owner prompt's answers on the demo
 *   graph, in order, before the default (approved) applies again.
 *
 * - `demo_inbox_cards` (int extra): how many generated cards the demo Inbox holds besides the mock's nine, for the
 *   on-demand frame-timing benchmark (T8.6). Only with `demo`.
 *
 * Start it with `adb shell am start -n com.stainedhead.paperclipmobile/.android.MainActivity --ez demo true`. A release
 * build ignores every extra, so a shipped app cannot be steered onto fakes (the iPhone app has the same rule).
 */
data class LaunchConfiguration(
    val useDemo: Boolean = false,
    val useFirstRunDemo: Boolean = false,
    val scriptedBiometrics: List<BiometricResult> = emptyList(),
    val inboxCards: Int = 0,
) {
    companion object {
        const val EXTRA_DEMO = "demo"
        const val EXTRA_DEMO_FIRST_RUN = "demo_first_run"
        const val EXTRA_DEMO_BIOMETRICS = "demo_biometrics"
        const val EXTRA_DEMO_INBOX_CARDS = "demo_inbox_cards"

        /**
         * The configuration for the boolean extras that are set to true and the [biometrics] extra; [allowsDemoControls]
         * is false in a release build.
         */
        fun from(
            trueExtras: Set<String>,
            allowsDemoControls: Boolean,
            biometrics: String? = null,
            inboxCards: Int = 0,
        ): LaunchConfiguration =
            if (allowsDemoControls) {
                LaunchConfiguration(
                    useDemo = EXTRA_DEMO in trueExtras,
                    useFirstRunDemo = EXTRA_DEMO_FIRST_RUN in trueExtras,
                    scriptedBiometrics = biometrics.orEmpty().split(',').mapNotNull(::biometricResult),
                    inboxCards = inboxCards.coerceAtLeast(0),
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
                intent?.getStringExtra(EXTRA_DEMO_BIOMETRICS),
                intent?.getIntExtra(EXTRA_DEMO_INBOX_CARDS, 0) ?: 0,
            )

        private fun biometricResult(word: String): BiometricResult? =
            when (word.trim().lowercase()) {
                "approved" -> BiometricResult.Approved
                "cancelled" -> BiometricResult.Cancelled
                "failed" -> BiometricResult.Failed
                "lockedout" -> BiometricResult.LockedOut
                "unavailable" -> BiometricResult.Unavailable
                else -> null
            }
    }
}
