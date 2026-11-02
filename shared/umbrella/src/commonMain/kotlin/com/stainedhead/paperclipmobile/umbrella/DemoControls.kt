package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.fakes.FakeBiometrics
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity

/**
 * What a shell may steer and read on the demo graph only (`PaperclipGraph.demoControls`; null on any other graph).
 * It lets UI tests script the device-owner prompt (Face ID cancelled, failed, approved) and assert how often the
 * phone asked, without a real device. It never exists in a graph backed by real adapters.
 */
class DemoControls internal constructor(
    private val biometrics: FakeBiometrics,
    private val connectivity: FakeConnectivity,
) {
    /** How many device-owner prompts the graph has shown so far. */
    val biometricPromptCount: Int get() = biometrics.calls

    /** The next prompts are answered with [result], one after another, before the default applies again. */
    fun scriptBiometrics(result: BiometricResult) = biometrics.script(result)

    /** The answer for every prompt that is not scripted. Approved until changed. */
    fun setDefaultBiometricResult(result: BiometricResult) {
        biometrics.result = result
    }

    /** Goes offline (false) or comes back online (true), so UI tests can see the offline states of a screen. */
    fun setOnline(online: Boolean) = connectivity.setOnline(online)
}
