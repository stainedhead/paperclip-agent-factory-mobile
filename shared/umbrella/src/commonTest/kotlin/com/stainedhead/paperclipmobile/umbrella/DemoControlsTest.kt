package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.presentation.AppIntent
import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue
import com.stainedhead.paperclipmobile.presentation.LockState
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

private suspend fun <S> StateFlow<S>.await(predicate: (S) -> Boolean): S =
    withContext(Dispatchers.Default) { withTimeout(60.seconds) { first(predicate) } }

/** The demo graph lets a shell (and its UI tests) script the device-owner prompt and count the prompts (UI-004). */
class DemoControlsTest {
    @Test
    fun `only the demo graph has demo controls`() {
        (PaperclipCore.createDemoGraph().demoControls != null) shouldBe true
        (PaperclipCore.createGraph(allowInsecureLocal = false).demoControls == null) shouldBe true
    }

    @Test
    fun `scripted biometric results answer the next prompts in order and are counted`() =
        runTest {
            val graph = PaperclipCore.createDemoGraph()
            val controls = checkNotNull(graph.demoControls)
            controls.scriptBiometrics(BiometricResult.Cancelled)
            controls.scriptBiometrics(BiometricResult.Approved)
            val app = graph.createAppStateHolder()
            app.state.await { it.isLoaded && it.lock == LockState.Locked }

            app.onIntent(AppIntent.UnlockRequested)
            val cancelled = app.state.await { it.unlockIssue != null }
            cancelled.lock shouldBe LockState.Locked
            cancelled.unlockIssue shouldBe DeviceAuthIssue.Cancelled
            controls.biometricPromptCount shouldBe 1

            app.onIntent(AppIntent.UnlockRequested)
            app.state.await { it.lock == LockState.Unlocked }
            controls.biometricPromptCount shouldBe 2
            app.close()
        }

    @Test
    fun `the default biometric result applies once the script is used up`() =
        runTest {
            val graph = PaperclipCore.createDemoGraph()
            val controls = checkNotNull(graph.demoControls)
            controls.setDefaultBiometricResult(BiometricResult.Failed)
            val app = graph.createAppStateHolder()
            app.state.await { it.isLoaded }

            app.onIntent(AppIntent.UnlockRequested)

            app.state.await { it.unlockIssue == DeviceAuthIssue.Failed }
            controls.biometricPromptCount shouldBe 1
            app.close()
        }

    @Test
    fun `the graph reports its clock so an Updated label is measured against the same now`() {
        PaperclipCore.createDemoGraph().nowEpochMillis shouldBe
            com.stainedhead.paperclipmobile.testing.demo.DemoFactories.now
                .toEpochMilliseconds()
    }
}
