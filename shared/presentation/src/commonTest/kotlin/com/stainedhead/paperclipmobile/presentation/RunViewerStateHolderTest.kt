@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.RunEvent
import com.stainedhead.paperclipmobile.domain.RunEventLevel
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.RunStatus
import com.stainedhead.paperclipmobile.domain.RunStream
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.AgentFixtures
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private fun TestScope.runHolder(
    env: Env,
    runId: String = "run-live",
) = RunViewerStateHolder(
    env.runViewer,
    env.connectivity,
    env.clock,
    CoroutineScope(StandardTestDispatcher(testScheduler)),
    CompanyTarget("inst-1", TestData.acme.value),
    runId,
)

class RunViewerLoadTest {
    @Test
    fun `FR-075 a running run shows its header events and log and follows the end`() =
        runTest {
            val env = workEnv()
            val holder = runHolder(env)
            advanceUntilIdle()

            val state = holder.state.value
            state.phase shouldBe DetailPhase.Content
            state.run?.status shouldBe RunStatusUi.Running
            state.run?.agentName shouldBe "Engineer 1"
            state.run?.issueId shouldBe "pap-1182"
            state.isLive shouldBe true
            state.events.map { it.seq } shouldBe listOf(1L, 2L)
            state.events.first().message shouldBe "Run started"
            state.logText shouldBe AgentFixtures.LOG
            state.autoScroll shouldBe true
            state.refreshIntervalSeconds shouldBe 5
            state.updatedAtEpochMillis shouldBe env.clock.now().toEpochMilliseconds()
        }

    @Test
    fun `FR-090 refresh appends only new events and log text`() =
        runTest {
            val env = workEnv()
            val holder = runHolder(env)
            advanceUntilIdle()
            env.runs.appendLog(RunId("run-live"), "step 3\n")
            env.runs.eventStore +=
                RunEvent(3, "tool.call", RunStream.Stderr, RunEventLevel.Warn, "Slow query", TestData.epoch)

            holder.onIntent(RunViewerIntent.Refresh)
            advanceUntilIdle()

            holder.state.value.events
                .map { it.seq } shouldBe listOf(1L, 2L, 3L)
            holder.state.value.events
                .last()
                .level shouldBe RunLevelUi.Warn
            holder.state.value.events
                .last()
                .stream shouldBe RunStreamUi.Stderr
            holder.state.value.logText shouldBe AgentFixtures.LOG + "step 3\n"
        }

    @Test
    fun `scrolling up stops following and jumping to latest resumes it`() =
        runTest {
            val env = workEnv()
            val holder = runHolder(env)
            advanceUntilIdle()

            holder.onIntent(RunViewerIntent.UserScrolled(atBottom = false))
            env.runs.appendLog(RunId("run-live"), "more\n")
            holder.onIntent(RunViewerIntent.Refresh)
            advanceUntilIdle()
            holder.state.value.autoScroll shouldBe false
            holder.state.value.logText
                .endsWith("more\n") shouldBe true

            holder.onIntent(RunViewerIntent.UserScrolled(atBottom = true))
            advanceUntilIdle()
            holder.state.value.autoScroll shouldBe true
            holder.onIntent(RunViewerIntent.UserScrolled(atBottom = false))
            holder.onIntent(RunViewerIntent.JumpToLatest)
            advanceUntilIdle()
            holder.state.value.autoScroll shouldBe true
        }

    @Test
    fun `a finished run is not live and needs no refresh`() =
        runTest {
            val env = workEnv()
            val holder = runHolder(env, "run-done")
            advanceUntilIdle()

            holder.state.value.isLive shouldBe false
            holder.state.value.refreshIntervalSeconds shouldBe 0
            holder.state.value.canCancel shouldBe false
        }

    @Test
    fun `a missing run is a failed screen and offline makes no call`() =
        runTest {
            val env = workEnv()
            val missing = runHolder(env, "nope")
            advanceUntilIdle()
            missing.state.value.phase shouldBe DetailPhase.Failed
            missing.state.value.error shouldBe ErrorUi(ErrorKey.NotFound)

            val offline = workEnv()
            offline.connectivity.setOnline(false)
            val holder = runHolder(offline)
            advanceUntilIdle()
            holder.state.value.phase shouldBe DetailPhase.Failed
            holder.state.value.isOffline shouldBe true
            offline.runs.count("get") shouldBe 0
        }

    @Test
    fun `a failing log keeps the run and events`() =
        runTest {
            val env = workEnv()
            env.runs.failures["log"] = DomainError.ServerError
            val holder = runHolder(env)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Content
            holder.state.value.logText shouldBe ""
            holder.state.value.logUnavailable shouldBe true
        }

    @Test
    fun `failedFirstLoadKeepsRefreshInterval`() =
        runTest {
            val env = workEnv()
            env.runs.failures["get"] = DomainError.Unreachable
            val holder = runHolder(env)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Failed
            holder.state.value.refreshIntervalSeconds shouldBe 15
        }
}

class RunViewerCancelTest {
    @Test
    fun `FR-075 cancel asks for confirmation then device authentication then calls once`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = runHolder(env)
            advanceUntilIdle()
            holder.state.value.canCancel shouldBe true

            holder.onIntent(RunViewerIntent.CancelTapped)
            advanceUntilIdle()
            holder.state.value.confirmCancel shouldBe true
            env.runs.count("cancel") shouldBe 0
            env.biometrics.calls shouldBe 0

            holder.onIntent(RunViewerIntent.CancelConfirmed)
            advanceUntilIdle()

            env.biometrics.calls shouldBe 1
            env.runs.count("cancel") shouldBe 1
            holder.state.value.run
                ?.status shouldBe RunStatusUi.Cancelled
            holder.state.value.isLive shouldBe false
            holder.state.value.confirmCancel shouldBe false
            holder.state.value.canCancel shouldBe false
        }

    @Test
    fun `acceptance 9 every device authentication failure means the gateway is never called`() =
        runTest {
            listOf(
                BiometricResult.Cancelled,
                BiometricResult.Failed,
                BiometricResult.LockedOut,
                BiometricResult.Unavailable,
            ).forEach { outcome ->
                val env = workEnv()
                env.biometrics.result = outcome
                val holder = runHolder(env)
                advanceUntilIdle()

                holder.onIntent(RunViewerIntent.CancelTapped)
                holder.onIntent(RunViewerIntent.CancelConfirmed)
                advanceUntilIdle()

                env.runs.count("cancel") shouldBe 0
                holder.state.value.feedback
                    ?.kind shouldBe FeedbackKind.DeviceAuth
                holder.state.value.run
                    ?.status shouldBe RunStatusUi.Running
            }
        }

    @Test
    fun `acceptance 8 a viewer cannot cancel and never reaches biometrics or the gateway`() =
        runTest {
            val env = workEnv(Role.Viewer)
            val holder = runHolder(env)
            advanceUntilIdle()
            holder.state.value.canCancel shouldBe false
            holder.state.value.actionsDisabledReason shouldBe ActionsDisabledReason.ViewerRole

            holder.onIntent(RunViewerIntent.CancelTapped)
            holder.onIntent(RunViewerIntent.CancelConfirmed)
            advanceUntilIdle()

            env.runs.count("cancel") shouldBe 0
            env.biometrics.calls shouldBe 0
        }

    @Test
    fun `dismissing the confirmation does nothing and a second confirm in flight is ignored`() =
        runTest {
            val env = workEnv()
            val holder = runHolder(env)
            advanceUntilIdle()

            holder.onIntent(RunViewerIntent.CancelTapped)
            holder.onIntent(RunViewerIntent.CancelDismissed)
            holder.onIntent(RunViewerIntent.CancelConfirmed)
            advanceUntilIdle()
            env.runs.count("cancel") shouldBe 0

            holder.onIntent(RunViewerIntent.CancelTapped)
            holder.onIntent(RunViewerIntent.CancelConfirmed)
            holder.onIntent(RunViewerIntent.CancelConfirmed)
            advanceUntilIdle()
            env.runs.count("cancel") shouldBe 1
        }

    @Test
    fun `acceptance 10 a timeout is sent once and the run is shown as the server has it`() =
        runTest {
            val env = workEnv()
            env.runs.failures["cancel"] = DomainError.Timeout
            val holder = runHolder(env)
            advanceUntilIdle()

            holder.onIntent(RunViewerIntent.CancelTapped)
            holder.onIntent(RunViewerIntent.CancelConfirmed)
            advanceUntilIdle()

            env.runs.count("cancel") shouldBe 1
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Reconciled, error = ErrorUi(ErrorKey.Timeout))
            holder.state.value.run
                ?.status shouldBe RunStatusUi.Running
        }

    @Test
    fun `a run that finished while the person decided shows its final state`() =
        runTest {
            val env = workEnv()
            val holder = runHolder(env)
            advanceUntilIdle()
            env.runs.store[RunId("run-live")] =
                env.runs.store
                    .getValue(RunId("run-live"))
                    .copy(status = RunStatus.Succeeded)

            holder.onIntent(RunViewerIntent.CancelTapped)
            holder.onIntent(RunViewerIntent.CancelConfirmed)
            advanceUntilIdle()

            holder.state.value.run
                ?.status shouldBe RunStatusUi.Succeeded
            holder.state.value.feedback
                ?.kind shouldBe FeedbackKind.Reconciled
            holder.onIntent(RunViewerIntent.MessageDismissed)
            advanceUntilIdle()
            holder.state.value.feedback shouldBe null
        }
}
