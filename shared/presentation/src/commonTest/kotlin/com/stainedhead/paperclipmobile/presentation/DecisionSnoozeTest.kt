@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

private const val KEY = "attention:decision:dec-1"

private fun TestScope.snoozeEnv(role: Role = Role.Owner): Env {
    val env = Env(listOf(TestData.instance())) { testScheduler.currentTime }
    env.auth.meResult = DomainResult.Success(TestData.access(role))
    return env
}

private fun TestScope.snoozeHolder(
    env: Env,
    dismissalKey: String?,
) = DecisionStateHolder(
    env.decision,
    env.connectivity,
    env.clock,
    CoroutineScope(StandardTestDispatcher(testScheduler)),
    DecisionTarget(CompanyTarget("inst-1", TestData.acme.value), "dec-1", null, null, dismissalKey),
)

/** ADR-025: the Decision screen offers Snooze only when it was opened from an Inbox card (it knows the card's key). */
class DecisionSnoozeTest {
    @Test
    fun `FR-034 opened from an Inbox card the decision offers the snooze presets`() =
        runTest {
            val holder = snoozeHolder(snoozeEnv(), KEY)
            advanceUntilIdle()

            holder.state.value.canSnooze shouldBe true
            holder.state.value.snoozeOptions shouldBe SnoozePresetUi.entries
        }

    @Test
    fun `FR-034 opened without a card the Snooze button is hidden and the intent does nothing`() =
        runTest {
            val env = snoozeEnv()
            val holder = snoozeHolder(env, null)
            advanceUntilIdle()

            holder.state.value.canSnooze shouldBe false
            holder.onIntent(DecisionIntent.SnoozeRequested(SnoozePresetUi.OneHour))
            advanceUntilIdle()

            env.attention.snoozes shouldBe emptyList()
            holder.state.value.isSnoozed shouldBe false
        }

    @Test
    fun `FR-034 snoozing sends the card key and the time once and marks the screen snoozed`() =
        runTest {
            val env = snoozeEnv()
            val holder = snoozeHolder(env, KEY)
            advanceUntilIdle()

            holder.onIntent(DecisionIntent.SnoozeRequested(SnoozePresetUi.OneHour))
            advanceUntilIdle()

            env.attention.snoozes shouldBe listOf(KEY to env.clock.now() + 1.hours)
            holder.state.value.isSnoozed shouldBe true
            holder.state.value.canSnooze shouldBe false
        }

    @Test
    fun `a failed snooze is sent once shown as an error and does not mark the screen snoozed`() =
        runTest {
            val env = snoozeEnv()
            env.attention.failures["snooze"] = DomainError.Timeout
            val holder = snoozeHolder(env, KEY)
            advanceUntilIdle()

            holder.onIntent(DecisionIntent.SnoozeRequested(SnoozePresetUi.OneHour))
            advanceUntilIdle()

            env.attention.count("snooze") shouldBe 1
            holder.state.value.isSnoozed shouldBe false
            holder.state.value.feedback
                ?.kind shouldBe FeedbackKind.Failed
        }

    @Test
    fun `a viewer and an offline phone cannot snooze`() =
        runTest {
            val viewer = snoozeEnv(Role.Viewer)
            val viewerHolder = snoozeHolder(viewer, KEY)
            val offline = snoozeEnv()
            val offlineHolder = snoozeHolder(offline, KEY)
            advanceUntilIdle()
            offline.connectivity.setOnline(false)
            advanceUntilIdle()

            viewerHolder.state.value.canSnooze shouldBe false
            offlineHolder.state.value.canSnooze shouldBe false
            offlineHolder.onIntent(DecisionIntent.SnoozeRequested(SnoozePresetUi.OneHour))
            advanceUntilIdle()
            offline.attention.snoozes shouldBe emptyList()
        }
}
