package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DeviceAuthGuardTest {
    @Test
    fun `FR-014 every destructive action asks the device and every other action does not`() =
        runTest {
            Action.entries.forEach { action ->
                val env = Env()
                val result = env.deviceAuth(action)

                result shouldBe BiometricResult.Approved
                env.biometrics.calls shouldBe
                    (
                        if (com.stainedhead.paperclipmobile.domain.DeviceAuthPolicy
                                .requires(action)
                        ) {
                            1
                        } else {
                            0
                        }
                    )
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
