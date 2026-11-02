@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IdlePeriod
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class SettingsStateHolderTest {
    private val home = TestData.instance("home", "Home factory")
    private val cloud = TestData.instance("cloud", "Cloud", sortOrder = 1)

    private fun TestScope.holder(env: Env): SettingsStateHolder {
        val useCases =
            SettingsUseCases(
                env.observeSettings,
                env.updateSettings,
                env.observeInstances,
                env.expiry,
                env.loadAccess,
                env.listKeys,
                env.revokeKey,
                env.signOut,
                env.signOutEverywhere,
            )
        return SettingsStateHolder(useCases, env.connectivity, CoroutineScope(StandardTestDispatcher(testScheduler)))
    }

    private fun TestScope.ready(env: Env = Env(listOf(home, cloud))): SettingsStateHolder {
        val holder = holder(env)
        advanceUntilIdle()
        return holder
    }

    @Test
    fun `FR-017 settings show the device name app lock idle period and every factory`() =
        runTest {
            val state = ready().state.value

            state.deviceName shouldBe "Alex's iPhone"
            state.appLockEnabled shouldBe true
            state.idlePeriod shouldBe IdlePeriod.FiveMinutes
            state.idleOptions shouldBe IdlePeriod.entries
            state.destructiveActionAuthAlwaysOn shouldBe true
            state.factories.map { it.name } shouldBe listOf("Home factory", "Cloud")
        }

    @Test
    fun `FR-017 changes are saved a bad device name is flagged and destructive auth stays on`() =
        runTest {
            val env = Env(listOf(home))
            val holder = ready(env)

            holder.onIntent(SettingsIntent.DeviceNameCommitted("Pixel 9"))
            holder.onIntent(SettingsIntent.AppLockToggled(false))
            holder.onIntent(SettingsIntent.IdlePeriodChosen(IdlePeriod.OneHour))
            advanceUntilIdle()

            holder.state.value.deviceName shouldBe "Pixel 9"
            holder.state.value.appLockEnabled shouldBe false
            holder.state.value.idlePeriod shouldBe IdlePeriod.OneHour
            holder.state.value.destructiveActionAuthAlwaysOn shouldBe true

            holder.onIntent(SettingsIntent.DeviceNameCommitted("  "))
            advanceUntilIdle()
            holder.state.value.deviceNameInvalid shouldBe true
            holder.state.value.deviceName shouldBe "Pixel 9"
            holder.onIntent(SettingsIntent.MessageDismissed)
            holder.state.value.deviceNameInvalid shouldBe false
        }

    @Test
    fun `FR-025 paired devices mark this phone and the profile is shown read-only`() =
        runTest {
            val env = Env(listOf(home))
            env.storeToken(home)
            val holder = ready(env)

            holder.onIntent(SettingsIntent.PairedDevicesRequested("home"))
            advanceUntilIdle()

            val devices = holder.state.value.pairedDevices!!
            devices.status shouldBe PairedDevicesStatus.Loaded
            devices.keys.map { it.id to it.isCurrentDevice } shouldBe
                listOf(TestData.KEY_ID to true, "key-laptop" to false)
            holder.state.value.profileName shouldBe "Alex Operator"
            holder.state.value.profileEmail shouldBe "alex@example.com"
        }

    @Test
    fun `FR-025 a failed key list is shown and offline makes no call`() =
        runTest {
            val env = Env(listOf(home))
            val holder = ready(env)
            env.auth.keysResult = DomainResult.Failure(DomainError.Forbidden)

            holder.onIntent(SettingsIntent.PairedDevicesRequested("home"))
            advanceUntilIdle()
            holder.state.value.pairedDevices
                ?.status shouldBe PairedDevicesStatus.Failed
            holder.state.value.error shouldBe ErrorUi(ErrorKey.NoPermission)

            env.connectivity.setOnline(false)
            advanceUntilIdle()
            val calls = env.auth.count("listBoardKeys")
            holder.onIntent(SettingsIntent.PairedDevicesRequested("home"))
            advanceUntilIdle()
            holder.state.value.isOffline shouldBe true
            env.auth.count("listBoardKeys") shouldBe calls
        }

    @Test
    fun `FR-025 revoking another device asks first calls the server once and reloads the list`() =
        runTest {
            val env = Env(listOf(home))
            env.storeToken(home)
            val holder = ready(env)
            holder.onIntent(SettingsIntent.PairedDevicesRequested("home"))
            advanceUntilIdle()

            holder.onIntent(SettingsIntent.RevokeKeyRequested("home", "key-laptop"))
            holder.state.value.confirmation shouldBe
                SettingsConfirmation.RevokeKey("home", "key-laptop", "paperclipai cli", false)
            env.auth.count("revokeBoardKey") shouldBe 0

            holder.onIntent(SettingsIntent.ConfirmationAccepted)
            holder.onIntent(SettingsIntent.ConfirmationAccepted)
            advanceUntilIdle()

            env.auth.count("revokeBoardKey") shouldBe 1
            env.auth.count("listBoardKeys") shouldBe 2
            holder.state.value.isBusy shouldBe false
        }

    @Test
    fun `FR-025 revoking this phone's key signs it out and a failure is shown`() =
        runTest {
            val env = Env(listOf(home))
            env.storeToken(home)
            val holder = ready(env)
            holder.onIntent(SettingsIntent.PairedDevicesRequested("home"))
            advanceUntilIdle()

            env.auth.revokeKeyResult = DomainResult.Failure(DomainError.Timeout)
            holder.onIntent(SettingsIntent.RevokeKeyRequested("home", "key-laptop"))
            holder.onIntent(SettingsIntent.ConfirmationAccepted)
            advanceUntilIdle()
            holder.state.value.error shouldBe ErrorUi(ErrorKey.Timeout)
            env.auth.count("revokeBoardKey") shouldBe 1

            holder.onIntent(SettingsIntent.RevokeKeyRequested("home", TestData.KEY_ID))
            holder.state.value.confirmation shouldBe
                SettingsConfirmation.RevokeKey("home", TestData.KEY_ID, "Alex's iPhone", true)
            holder.onIntent(SettingsIntent.ConfirmationAccepted)
            advanceUntilIdle()

            holder.state.value.lastSignOutKey shouldBe ServerKeyOutcome.Revoked
            holder.state.value.factories
                .first()
                .status shouldBe FactoryStatusUi.SignedOut
            env.secrets.paths shouldBe emptySet()
        }

    @Test
    fun `FR-024 sign out asks first and reports whether the server confirmed`() =
        runTest {
            val env = Env(listOf(home))
            env.storeToken(home)
            val holder = ready(env)
            env.auth.revokeCurrentResult = DomainResult.Failure(DomainError.Unreachable)

            holder.onIntent(SettingsIntent.SignOutRequested("zzz"))
            holder.state.value.confirmation shouldBe null
            holder.onIntent(SettingsIntent.SignOutRequested("home"))
            holder.state.value.confirmation shouldBe SettingsConfirmation.SignOut("home", "Home factory")
            holder.onIntent(SettingsIntent.ConfirmationDismissed)
            env.auth.count("revokeCurrentKey") shouldBe 0

            holder.onIntent(SettingsIntent.SignOutRequested("home"))
            holder.onIntent(SettingsIntent.ConfirmationAccepted)
            advanceUntilIdle()

            holder.state.value.lastSignOutKey shouldBe ServerKeyOutcome.Unconfirmed
            holder.state.value.factories
                .single()
                .status shouldBe FactoryStatusUi.SignedOut
            env.secrets.paths shouldBe emptySet()
        }

    @Test
    fun `FR-024 sign out everywhere reports a factory it could not sign out and signs out the rest`() =
        runTest {
            val env = Env(listOf(home, cloud))
            env.secrets.failNamespaceDeletes += cloud.id
            val holder = ready(env)

            holder.onIntent(SettingsIntent.SignOutEverywhereRequested)
            holder.onIntent(SettingsIntent.ConfirmationAccepted)
            advanceUntilIdle()

            holder.state.value.signOutFailures shouldBe 1
            env.repository.get(home.id)?.status shouldBe InstanceStatus.SignedOut
            holder.close()
        }

    @Test
    fun `FR-024 sign out everywhere asks first and counts unconfirmed revokes`() =
        runTest {
            val env = Env(listOf(home, cloud))
            val holder = ready(env)
            env.auth.revokeCurrentResult = DomainResult.Failure(DomainError.Timeout)

            holder.onIntent(SettingsIntent.SignOutEverywhereRequested)
            holder.state.value.confirmation shouldBe SettingsConfirmation.SignOutEverywhere
            holder.onIntent(SettingsIntent.ConfirmationAccepted)
            advanceUntilIdle()

            holder.state.value.unconfirmedRevokes shouldBe 2
            holder.state.value.signOutFailures shouldBe 0
            holder.state.value.factories
                .all { it.status == FactoryStatusUi.SignedOut } shouldBe true
            holder.onIntent(SettingsIntent.ConfirmationAccepted)
            holder.onIntent(SettingsIntent.RevokeKeyRequested("home", "unknown"))
            holder.state.value.confirmation shouldBe null
            holder.close()
        }

    @Test
    fun `slowLoadForPreviousFactoryDoesNotOverwriteCurrent`() =
        runTest {
            val cloudElsewhere = TestData.instance("cloud", "Cloud", uri = "https://cloud.example.com", sortOrder = 1)
            val env = Env(listOf(home, cloudElsewhere))
            val holder = ready(env)
            env.auth.onCall = { call -> if (call.host == home.baseUri) delay(5_000) }

            holder.onIntent(SettingsIntent.PairedDevicesRequested("home"))
            runCurrent()
            holder.onIntent(SettingsIntent.PairedDevicesRequested("cloud"))
            advanceTimeBy(100)
            holder.state.value.pairedDevices
                ?.instanceId shouldBe "cloud"

            advanceUntilIdle()

            holder.state.value.pairedDevices
                ?.instanceId shouldBe "cloud"
            holder.state.value.pairedDevices
                ?.status shouldBe PairedDevicesStatus.Loaded
        }
}

class AppStateHolderTest {
    private val home = TestData.instance("home", "Home factory")
    private val cloud = TestData.instance("cloud", "Cloud", sortOrder = 1)

    private fun TestScope.holder(env: Env): AppStateHolder {
        val useCases =
            AppUseCases(
                env.observeSettings,
                env.observeInstances,
                env.unlock,
                env.shouldLock,
                env.switchInstance,
                env.switchCompany,
            )
        return AppStateHolder(useCases, env.clock, CoroutineScope(StandardTestDispatcher(testScheduler)))
    }

    @Test
    fun `the app starts locked and unloaded then shows the first factory`() =
        runTest {
            val env = Env(listOf(home, cloud))
            val holder = holder(env)
            holder.state.value shouldBe AppState()

            advanceUntilIdle()

            val state = holder.state.value
            state.isLoaded shouldBe true
            state.lock shouldBe LockState.Locked
            state.hasFactories shouldBe true
            state.selectedInstanceId shouldBe "home"
            state.selectedInstanceName shouldBe "Home factory"
            state.selectedCompanyId shouldBe TestData.acme.value
        }

    @Test
    fun `FR-014 unlocking asks the device and a failure keeps the app locked`() =
        runTest {
            val env = Env(listOf(home))
            val holder = holder(env)
            advanceUntilIdle()
            env.biometrics.result = BiometricResult.LockedOut

            holder.onIntent(AppIntent.UnlockRequested)
            advanceUntilIdle()
            holder.state.value.lock shouldBe LockState.Locked
            holder.state.value.unlockIssue shouldBe DeviceAuthIssue.LockedOut

            env.biometrics.result = BiometricResult.Approved
            holder.onIntent(AppIntent.UnlockRequested)
            holder.onIntent(AppIntent.UnlockRequested)
            advanceUntilIdle()
            holder.state.value.lock shouldBe LockState.Unlocked
            holder.state.value.unlockIssue shouldBe null
            env.biometrics.calls shouldBe 2
            holder.onIntent(AppIntent.UnlockRequested)
            advanceUntilIdle()
            env.biometrics.calls shouldBe 2
        }

    @Test
    fun `FR-014 the app relocks only after the idle period in the background`() =
        runTest {
            val env = Env(listOf(home))
            val holder = holder(env)
            advanceUntilIdle()
            holder.onIntent(AppIntent.UnlockRequested)
            advanceUntilIdle()

            holder.onIntent(AppIntent.WentToBackground)
            env.clock.advance(4.minutes)
            holder.onIntent(AppIntent.CameToForeground)
            holder.state.value.lock shouldBe LockState.Unlocked

            holder.onIntent(AppIntent.WentToBackground)
            env.clock.advance(5.minutes)
            holder.onIntent(AppIntent.CameToForeground)
            holder.state.value.lock shouldBe LockState.Locked

            holder.onIntent(AppIntent.CameToForeground)
            holder.state.value.lock shouldBe LockState.Locked
        }

    @Test
    fun `FR-014 with the app lock off the app opens unlocked`() =
        runTest {
            val env = Env(listOf(home))
            env.updateSettings(
                com.stainedhead.paperclipmobile.application.settings
                    .AppSettingsUpdate(appLockEnabled = false),
            )
            val holder = holder(env)
            advanceUntilIdle()

            holder.state.value.lock shouldBe LockState.Unlocked
        }

    @Test
    fun `an app that is foregrounded before settings load does nothing`() =
        runTest {
            val env = Env(listOf(home))
            val holder = holder(env)

            holder.onIntent(AppIntent.WentToBackground)
            holder.onIntent(AppIntent.CameToForeground)

            holder.state.value.lock shouldBe LockState.Locked
        }

    @Test
    fun `FR-007 the selected factory and company are restored and can be switched`() =
        runTest {
            val env = Env(listOf(home, cloud.copy(lastCompanyId = TestData.globex)))
            env.switchInstance(cloud.id)
            val holder = holder(env)
            advanceUntilIdle()

            holder.state.value.selectedInstanceId shouldBe "cloud"
            holder.state.value.selectedCompanyId shouldBe TestData.globex.value

            holder.onIntent(AppIntent.CompanySelected("home", TestData.globex.value))
            advanceUntilIdle()
            holder.state.value.selectedInstanceId shouldBe "home"
            holder.state.value.selectedCompanyId shouldBe TestData.globex.value

            holder.onIntent(AppIntent.InstanceSelected("cloud"))
            advanceUntilIdle()
            holder.state.value.selectedInstanceId shouldBe "cloud"
            holder.close()
        }

    @Test
    fun `with no factories the shell shows setup`() =
        runTest {
            val holder = holder(Env())
            advanceUntilIdle()

            holder.state.value.hasFactories shouldBe false
            holder.state.value.selectedInstanceId shouldBe null
            InstanceStatus.entries.size shouldBe 3
        }
}
