@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** Rereview1b F-R-2: a key read that stays denied offers "here only"; a plain decline deletes nothing. */
class LocalOnlyRemovalStateTest {
    private val home = TestData.instance("home", "Home factory")

    private fun TestScope.manage(env: Env) =
        ManageFactoriesStateHolder(
            ManageFactoriesUseCases(env.observeInstances, env.reorder, env.remove, env.removeHereOnly),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )

    private fun TestScope.settings(env: Env): SettingsStateHolder {
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
                env.signOutHereOnly,
            )
        return SettingsStateHolder(useCases, env.connectivity, CoroutineScope(StandardTestDispatcher(testScheduler)))
    }

    private suspend fun deniedEnv(): Env =
        Env(listOf(home)).also {
            it.storeToken(home)
            it.auth.revokeCurrentResult = DomainResult.Failure(DomainError.DeviceAuthDeclined)
        }

    @Test
    fun `a denied key on remove changes nothing and offers removing here only`() =
        runTest {
            val env = deniedEnv()
            val holder = manage(env)
            advanceUntilIdle()

            holder.onIntent(ManageFactoriesIntent.RemoveRequested("home"))
            holder.onIntent(ManageFactoriesIntent.RemoveConfirmed)
            advanceUntilIdle()

            val state = holder.state.value
            state.error shouldBe ErrorUi(ErrorKey.AuthDeclined)
            state.isRemoving shouldBe false
            state.confirmation?.hereOnly shouldBe true
            state.confirmation?.effects shouldBe
                listOf(RemovalEffect.Configuration, RemovalEffect.CachedData, RemovalEffect.StoredKey)
            env.repository.deletedIds shouldBe emptyList()
            env.secrets.paths.isEmpty() shouldBe false
        }

    @Test
    fun `confirming remove here only deletes locally and reports the server key as unconfirmed`() =
        runTest {
            val env = deniedEnv()
            val holder = manage(env)
            advanceUntilIdle()
            holder.onIntent(ManageFactoriesIntent.RemoveRequested("home"))
            holder.onIntent(ManageFactoriesIntent.RemoveConfirmed)
            advanceUntilIdle()

            holder.onIntent(ManageFactoriesIntent.RemoveConfirmed)
            advanceUntilIdle()

            val state = holder.state.value
            state.confirmation shouldBe null
            state.error shouldBe null
            state.lastRemovedKey shouldBe ServerKeyOutcome.Unconfirmed
            env.repository.deletedIds shouldBe listOf(home.id)
            env.secrets.paths shouldBe emptySet()
            env.auth.count("revokeCurrentKey") shouldBe 1
        }

    @Test
    fun `declining remove here only deletes nothing`() =
        runTest {
            val env = deniedEnv()
            val holder = manage(env)
            advanceUntilIdle()
            holder.onIntent(ManageFactoriesIntent.RemoveRequested("home"))
            holder.onIntent(ManageFactoriesIntent.RemoveConfirmed)
            advanceUntilIdle()

            holder.onIntent(ManageFactoriesIntent.RemoveCancelled)

            holder.state.value.confirmation shouldBe null
            holder.state.value.error shouldBe null
            env.repository.deletedIds shouldBe emptyList()
            env.secrets.paths.isEmpty() shouldBe false
        }

    @Test
    fun `remove here only refused by the device owner changes nothing`() =
        runTest {
            val env = deniedEnv()
            val holder = manage(env)
            advanceUntilIdle()
            holder.onIntent(ManageFactoriesIntent.RemoveRequested("home"))
            holder.onIntent(ManageFactoriesIntent.RemoveConfirmed)
            advanceUntilIdle()
            env.biometrics.result = BiometricResult.Cancelled

            holder.onIntent(ManageFactoriesIntent.RemoveConfirmed)
            advanceUntilIdle()

            holder.state.value.deviceAuthIssue shouldBe DeviceAuthIssue.Cancelled
            holder.state.value.confirmation
                ?.hereOnly shouldBe true
            env.repository.deletedIds shouldBe emptyList()
        }

    @Test
    fun `a denied key on sign out offers signing out here only`() =
        runTest {
            val env = deniedEnv()
            val holder = settings(env)
            advanceUntilIdle()

            holder.onIntent(SettingsIntent.SignOutRequested("home"))
            holder.onIntent(SettingsIntent.ConfirmationAccepted)
            advanceUntilIdle()

            holder.state.value.error shouldBe ErrorUi(ErrorKey.AuthDeclined)
            holder.state.value.confirmation shouldBe SettingsConfirmation.SignOutHereOnly("home", "Home factory")
            env.secrets.paths.isEmpty() shouldBe false
            env.repository.get(home.id)?.status shouldBe InstanceStatus.SignedIn
        }

    @Test
    fun `confirming sign out here only drops the key and reports the server key as unconfirmed`() =
        runTest {
            val env = deniedEnv()
            val holder = settings(env)
            advanceUntilIdle()
            holder.onIntent(SettingsIntent.SignOutRequested("home"))
            holder.onIntent(SettingsIntent.ConfirmationAccepted)
            advanceUntilIdle()

            holder.onIntent(SettingsIntent.ConfirmationAccepted)
            advanceUntilIdle()

            holder.state.value.lastSignOutKey shouldBe ServerKeyOutcome.Unconfirmed
            holder.state.value.isBusy shouldBe false
            env.secrets.paths shouldBe emptySet()
            env.repository.get(home.id)?.status shouldBe InstanceStatus.SignedOut
        }

    @Test
    fun `declining sign out here only keeps the key`() =
        runTest {
            val env = deniedEnv()
            val holder = settings(env)
            advanceUntilIdle()
            holder.onIntent(SettingsIntent.SignOutRequested("home"))
            holder.onIntent(SettingsIntent.ConfirmationAccepted)
            advanceUntilIdle()

            holder.onIntent(SettingsIntent.ConfirmationDismissed)

            holder.state.value.confirmation shouldBe null
            env.secrets.paths.isEmpty() shouldBe false
            env.repository.get(home.id)?.status shouldBe InstanceStatus.SignedIn
        }

    @Test
    fun `sign out here only refused by the device owner keeps the key and says why`() =
        runTest {
            val env = deniedEnv()
            val holder = settings(env)
            advanceUntilIdle()
            holder.onIntent(SettingsIntent.SignOutRequested("home"))
            holder.onIntent(SettingsIntent.ConfirmationAccepted)
            advanceUntilIdle()
            env.biometrics.result = BiometricResult.LockedOut

            holder.onIntent(SettingsIntent.ConfirmationAccepted)
            advanceUntilIdle()

            holder.state.value.deviceAuthIssue shouldBe DeviceAuthIssue.LockedOut
            holder.state.value.isBusy shouldBe false
            env.secrets.paths.isEmpty() shouldBe false
        }

    private fun TestScope.edit(env: Env): EditInstanceStateHolder {
        val useCases =
            EditInstanceUseCases(
                env.validateAddress,
                env.get,
                env.probe,
                env.add,
                env.update,
                env.loadAccess,
                env.pair,
                env.signOut,
                env.remove,
                env.removeHereOnly,
                env.signOutHereOnly,
                env.expiry,
            )
        return EditInstanceStateHolder(
            "home",
            useCases,
            env.connectivity,
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
    }

    @Test
    fun `edit screen a denied key on sign out offers signing out here only and drops the key`() =
        runTest {
            val env = deniedEnv()
            val holder = edit(env)
            advanceUntilIdle()

            holder.onIntent(EditInstanceIntent.SignOutRequested)
            holder.onIntent(EditInstanceIntent.ConfirmationAccepted)
            advanceUntilIdle()
            holder.state.value.error shouldBe ErrorUi(ErrorKey.AuthDeclined)
            holder.state.value.confirmation shouldBe EditConfirmation.SignOutHereOnly
            env.secrets.paths.isEmpty() shouldBe false

            holder.onIntent(EditInstanceIntent.ConfirmationAccepted)
            advanceUntilIdle()

            holder.state.value.serverKeyOutcome shouldBe ServerKeyOutcome.Unconfirmed
            holder.state.value.isBusy shouldBe false
            env.secrets.paths shouldBe emptySet()
            env.repository.get(home.id)?.status shouldBe InstanceStatus.SignedOut
        }

    @Test
    fun `edit screen a denied key on remove offers removing here only and deletes locally`() =
        runTest {
            val env = deniedEnv()
            val holder = edit(env)
            advanceUntilIdle()

            holder.onIntent(EditInstanceIntent.RemoveRequested)
            holder.onIntent(EditInstanceIntent.ConfirmationAccepted)
            advanceUntilIdle()
            holder.state.value.error shouldBe ErrorUi(ErrorKey.AuthDeclined)
            holder.state.value.confirmation shouldBe EditConfirmation.RemoveHereOnly
            env.repository.deletedIds shouldBe emptyList()

            holder.onIntent(EditInstanceIntent.ConfirmationAccepted)
            advanceUntilIdle()

            holder.state.value.removed shouldBe true
            holder.state.value.serverKeyOutcome shouldBe ServerKeyOutcome.Unconfirmed
            env.repository.deletedIds shouldBe listOf(home.id)
            env.secrets.paths shouldBe emptySet()
        }

    @Test
    fun `edit screen declining the here only offer deletes nothing`() =
        runTest {
            val env = deniedEnv()
            val holder = edit(env)
            advanceUntilIdle()
            holder.onIntent(EditInstanceIntent.RemoveRequested)
            holder.onIntent(EditInstanceIntent.ConfirmationAccepted)
            advanceUntilIdle()

            holder.onIntent(EditInstanceIntent.ConfirmationDismissed)

            holder.state.value.confirmation shouldBe null
            env.repository.deletedIds shouldBe emptyList()
            env.secrets.paths.isEmpty() shouldBe false
        }
}
