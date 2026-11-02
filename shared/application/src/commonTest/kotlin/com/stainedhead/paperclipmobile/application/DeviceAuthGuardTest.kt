package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DeviceAuthGuardTest {
    /** The six actions PRD FR-014 names, written out so the expectation does not read the policy under test. */
    private val destructive =
        setOf(
            Action.PauseAgent,
            Action.CancelRun,
            Action.RejectApproval,
            Action.DecideDestructiveOption,
            Action.RaiseBudgetAndResume,
            Action.RemoveFactory,
        )

    @Test
    fun `FR-014 every destructive action asks the device and every other action does not`() =
        runTest {
            Action.entries.forEach { action ->
                val env = Env()
                val result = env.deviceAuth(action)

                result shouldBe BiometricResult.Approved
                (action to env.biometrics.calls) shouldBe (action to if (action in destructive) 1 else 0)
            }
        }

    @Test
    fun `a destructive action is confirmed with the destructive reason and a refusal stops it`() =
        runTest {
            destructive.forEach { action ->
                val env = Env()
                env.biometrics.result = BiometricResult.Cancelled

                env.deviceAuth(action) shouldBe BiometricResult.Cancelled
                env.biometrics.prompts shouldBe listOf(AuthReason.ConfirmDestructiveAction)
            }
        }

    @Test
    fun `cancel failure and lockout are passed through`() =
        runTest {
            listOf(
                BiometricResult.Cancelled,
                BiometricResult.Failed,
                BiometricResult.LockedOut,
                BiometricResult.Unavailable,
            ).forEach { denied ->
                val env = Env()
                env.biometrics.result = denied

                env.deviceAuth(Action.PauseAgent) shouldBe denied
            }
        }
}
