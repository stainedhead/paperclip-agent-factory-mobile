package com.stainedhead.paperclipmobile.application.settings

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.IdlePeriod
import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class SettingsUseCasesTest {
    @Test
    fun `FR-017 device name app lock and idle period can be changed`() =
        runTest {
            val env = Env()
            val update = UpdateAppSettings(env.settings)

            val result =
                update(
                    AppSettingsUpdate(
                        deviceName = "  Pixel 9 ",
                        appLockEnabled = false,
                        idlePeriod = IdlePeriod.OneHour,
                    ),
                )

            (result as UpdateSettingsResult.Updated).settings shouldBe
                AppSettings("Pixel 9", appLockEnabled = false, IdlePeriod.OneHour)
            ObserveAppSettings(env.settings)().first().deviceName shouldBe "Pixel 9"
        }

    @Test
    fun `FR-017 a partial update keeps the other fields`() =
        runTest {
            val env = Env()

            UpdateAppSettings(env.settings)(AppSettingsUpdate(appLockEnabled = false))

            env.settings.current shouldBe AppSettings("Alex's iPhone", appLockEnabled = false)
        }

    @Test
    fun `FR-017 a blank or over-long device name is refused and nothing is saved`() =
        runTest {
            val env = Env()
            val update = UpdateAppSettings(env.settings)

            update(AppSettingsUpdate(deviceName = "   ")) shouldBe UpdateSettingsResult.InvalidDeviceName
            update(AppSettingsUpdate(deviceName = "x".repeat(AppSettings.MAX_DEVICE_NAME_LENGTH + 1))) shouldBe
                UpdateSettingsResult.InvalidDeviceName
            env.settings.saveCalls shouldBe 0
        }

    @Test
    fun `FR-014 turning the app lock off never weakens destructive action authentication`() =
        runTest {
            val env = Env()
            UpdateAppSettings(env.settings)(AppSettingsUpdate(appLockEnabled = false))

            env.deviceAuth(com.stainedhead.paperclipmobile.domain.Action.PauseAgent)

            env.biometrics.calls shouldBe 1
        }

    @Test
    fun `FR-014 unlock asks the device for the app lock and the lock follows the idle period`() =
        runTest {
            val env = Env()
            env.biometrics.result = BiometricResult.Cancelled

            UnlockApp(env.biometrics)() shouldBe BiometricResult.Cancelled
            env.biometrics.prompts shouldBe listOf(AuthReason.UnlockApp)

            val shouldLock = ShouldLockApp(env.clock)
            val settings = AppSettings("Phone", idlePeriod = IdlePeriod.FiveMinutes)
            val start = env.clock.now()
            shouldLock(settings, start) shouldBe false
            env.clock.advance(5.minutes)
            shouldLock(settings, start) shouldBe true
        }
}
