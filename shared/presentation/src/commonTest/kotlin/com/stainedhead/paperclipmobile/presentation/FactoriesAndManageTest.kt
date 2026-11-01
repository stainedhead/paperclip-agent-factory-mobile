@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.auth.RevokeOutcome
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.Role
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
import kotlin.time.Duration.Companion.days

private fun TestScope.testScope() = CoroutineScope(StandardTestDispatcher(testScheduler))

class FactoriesStateHolderTest {
    private val home = TestData.instance("home", "Home factory")
    private val cloud = TestData.instance("cloud", "Cloud", sortOrder = 1, status = InstanceStatus.SignedOut)

    private fun TestScope.holder(env: Env): FactoriesStateHolder {
        val useCases =
            FactoriesUseCases(
                env.observeInstances,
                env.observeSettings,
                env.checkCredentials,
                env.loadAccess,
                env.switchInstance,
                env.switchCompany,
                env.expiry,
            )
        return FactoriesStateHolder(useCases, env.connectivity, env.clock, testScope())
    }

    @Test
    fun `no factories means first run`() =
        runTest {
            val holder = holder(Env())
            advanceUntilIdle()

            holder.state.value.isEmpty shouldBe true
            holder.state.value.rows shouldBe emptyList()
            holder.state.value.canRefresh shouldBe false
        }

    @Test
    fun `FR-001 rows show every factory in order with status and the first one selected`() =
        runTest {
            val env = Env(listOf(cloud, home))
            val holder = holder(env)
            advanceUntilIdle()

            val state = holder.state.value
            state.rows.map { it.id } shouldBe listOf("home", "cloud")
            state.rows.map { it.status } shouldBe listOf(FactoryStatusUi.SignedIn, FactoryStatusUi.SignedOut)
            state.selectedInstanceId shouldBe "home"
            state.rows.first().isSelected shouldBe true
            state.rows.first().selectedCompanyId shouldBe TestData.acme.value
            state.rows.first().address shouldBe "https://home.example.com"
        }

    @Test
    fun `FR-022 refresh loads companies with roles for signed in factories only and stamps the time`() =
        runTest {
            val env = Env(listOf(home, cloud))
            env.storeToken(home)
            val holder = holder(env)
            advanceUntilIdle()

            holder.onIntent(FactoriesIntent.Refresh)
            advanceUntilIdle()

            val rows = holder.state.value.rows
            rows.first().companies.map { it.name to it.role } shouldBe
                listOf("Acme Labs" to RoleUi.Owner, "Globex" to RoleUi.Viewer)
            rows.last().companies shouldBe emptyList()
            env.auth.count("me") shouldBe 1
            holder.state.value.updatedAtEpochMillis shouldBe env.clock.now().toEpochMilliseconds()
            holder.state.value.isRefreshing shouldBe false
        }

    @Test
    fun `FR-095 offline shows the cached companies, disables refresh and makes no call`() =
        runTest {
            val env = Env(listOf(home))
            env.storeToken(home)
            val holder = holder(env)
            advanceUntilIdle()
            holder.onIntent(FactoriesIntent.Refresh)
            advanceUntilIdle()

            env.connectivity.setOnline(false)
            advanceUntilIdle()
            holder.onIntent(FactoriesIntent.Refresh)
            advanceUntilIdle()

            holder.state.value.isOffline shouldBe true
            holder.state.value.canRefresh shouldBe false
            holder.state.value.rows
                .first()
                .companies.size shouldBe 2
            env.auth.count("me") shouldBe 1
        }

    @Test
    fun `FR-023 a 401 during refresh shows the factory as signed out and keeps it`() =
        runTest {
            val env = Env(listOf(home))
            env.storeToken(home)
            env.auth.meResult = DomainResult.Failure(DomainError.Unauthorized)
            val holder = holder(env)
            advanceUntilIdle()

            holder.onIntent(FactoriesIntent.Refresh)
            advanceUntilIdle()

            holder.state.value.rows
                .single()
                .status shouldBe FactoryStatusUi.SignedOut
            holder.state.value.rows
                .single()
                .error shouldBe ErrorUi(ErrorKey.SignedOut)
            holder.state.value.updatedAtEpochMillis shouldBe null
        }

    @Test
    fun `FR-015 an invalidated device key shows needs re-pairing without calling the server`() =
        runTest {
            val env = Env(listOf(home))
            env.secrets.invalidated = true
            val holder = holder(env)
            advanceUntilIdle()

            holder.onIntent(FactoriesIntent.Refresh)
            advanceUntilIdle()

            holder.state.value.rows
                .single()
                .status shouldBe FactoryStatusUi.NeedsRePairing
            env.auth.count("me") shouldBe 0
        }

    @Test
    fun `FR-023 the key expiry shows from five days before`() =
        runTest {
            val env = Env(listOf(home.copy(keyExpiresAt = TestData.epoch + 4.days)))
            val holder = holder(env)
            advanceUntilIdle()

            holder.state.value.rows
                .single()
                .expiry shouldBe ExpiryUi(KeyExpiryUi.ExpiringSoon, 4)
        }

    @Test
    fun `FR-007 selecting a factory and a company is remembered`() =
        runTest {
            val env = Env(listOf(home, cloud))
            val holder = holder(env)
            advanceUntilIdle()

            holder.onIntent(FactoriesIntent.InstanceSelected("cloud"))
            advanceUntilIdle()
            holder.state.value.selectedInstanceId shouldBe "cloud"

            holder.onIntent(FactoriesIntent.CompanySelected("home", TestData.globex.value))
            advanceUntilIdle()

            holder.state.value.selectedInstanceId shouldBe "home"
            holder.state.value.rows
                .first { it.id == "home" }
                .selectedCompanyId shouldBe TestData.globex.value
            holder.close()
        }

    @Test
    fun `a company list failure is shown on the row`() =
        runTest {
            val env = Env(listOf(home))
            env.storeToken(home)
            env.auth.companiesResult = DomainResult.Failure(DomainError.Rejected("nope"))
            val holder = holder(env)
            advanceUntilIdle()

            holder.onIntent(FactoriesIntent.Refresh)
            advanceUntilIdle()

            holder.state.value.rows
                .single()
                .error shouldBe ErrorUi(ErrorKey.Rejected, "nope")
        }
}

class ManageFactoriesStateHolderTest {
    private val a = TestData.instance("a", "Home factory")
    private val b = TestData.instance("b", "Cloud", sortOrder = 1, status = InstanceStatus.SignedOut)

    private fun TestScope.holder(env: Env) =
        ManageFactoriesStateHolder(ManageFactoriesUseCases(env.observeInstances, env.reorder, env.remove), testScope())

    @Test
    fun `FR-006 rows follow the saved order and a reorder is saved`() =
        runTest {
            val env = Env(listOf(a, b))
            val holder = holder(env)
            advanceUntilIdle()
            holder.state.value.rows
                .map { it.id } shouldBe listOf("a", "b")

            holder.onIntent(ManageFactoriesIntent.Reordered(listOf("b", "a")))
            advanceUntilIdle()

            holder.state.value.rows
                .map { it.id } shouldBe listOf("b", "a")
            holder.state.value.reorderRejected shouldBe false
        }

    @Test
    fun `a reorder that does not match the factories is rejected`() =
        runTest {
            val env = Env(listOf(a, b))
            val holder = holder(env)
            advanceUntilIdle()

            holder.onIntent(ManageFactoriesIntent.Reordered(listOf("a")))
            advanceUntilIdle()

            holder.state.value.reorderRejected shouldBe true
            holder.onIntent(ManageFactoriesIntent.MessageDismissed)
            holder.state.value.reorderRejected shouldBe false
        }

    @Test
    fun `FR-006 removal states what is deleted, asks the device, and removes`() =
        runTest {
            val env = Env(listOf(a, b))
            env.storeToken(a)
            val holder = holder(env)
            advanceUntilIdle()

            holder.onIntent(ManageFactoriesIntent.RemoveRequested("a"))
            holder.state.value.confirmation shouldBe
                RemovalConfirmationUi(
                    "a",
                    "Home factory",
                    listOf(
                        RemovalEffect.Configuration,
                        RemovalEffect.CachedData,
                        RemovalEffect.StoredKey,
                        RemovalEffect.ServerKey,
                    ),
                )
            env.biometrics.calls shouldBe 0

            holder.onIntent(ManageFactoriesIntent.RemoveConfirmed)
            advanceUntilIdle()

            env.biometrics.calls shouldBe 1
            holder.state.value.rows
                .map { it.id } shouldBe listOf("b")
            holder.state.value.confirmation shouldBe null
            holder.state.value.lastRemovedKey shouldBe ServerKeyOutcome.Revoked
            env.secrets.paths shouldBe emptySet()
        }

    @Test
    fun `a signed out factory does not offer a server key revoke`() =
        runTest {
            val env = Env(listOf(a, b))
            val holder = holder(env)
            advanceUntilIdle()

            holder.onIntent(ManageFactoriesIntent.RemoveRequested("b"))

            holder.state.value.confirmation
                ?.effects shouldBe
                listOf(RemovalEffect.Configuration, RemovalEffect.CachedData, RemovalEffect.StoredKey)
        }

    @Test
    fun `FR-014 each way device authentication can fail leaves the factory untouched and calls no gateway`() =
        runTest {
            val cases =
                mapOf(
                    BiometricResult.Cancelled to DeviceAuthIssue.Cancelled,
                    BiometricResult.Failed to DeviceAuthIssue.Failed,
                    BiometricResult.LockedOut to DeviceAuthIssue.LockedOut,
                    BiometricResult.Unavailable to DeviceAuthIssue.Unavailable,
                )
            cases.forEach { (result, issue) ->
                val env = Env(listOf(a))
                env.biometrics.result = result
                val holder = holder(env)
                advanceUntilIdle()

                holder.onIntent(ManageFactoriesIntent.RemoveRequested("a"))
                holder.onIntent(ManageFactoriesIntent.RemoveConfirmed)
                advanceUntilIdle()

                holder.state.value.deviceAuthIssue shouldBe issue
                holder.state.value.rows.size shouldBe 1
                holder.state.value.isRemoving shouldBe false
                env.auth.calls shouldBe emptyMap()
                env.repository.deletedIds shouldBe emptyList()
            }
        }

    @Test
    fun `cancelling the confirmation and double taps do nothing harmful`() =
        runTest {
            val env = Env(listOf(a))
            val holder = holder(env)
            advanceUntilIdle()

            holder.onIntent(ManageFactoriesIntent.RemoveConfirmed)
            holder.onIntent(ManageFactoriesIntent.RemoveRequested("zzz"))
            holder.state.value.confirmation shouldBe null

            holder.onIntent(ManageFactoriesIntent.RemoveRequested("a"))
            holder.onIntent(ManageFactoriesIntent.RemoveCancelled)
            holder.state.value.confirmation shouldBe null

            holder.onIntent(ManageFactoriesIntent.RemoveRequested("a"))
            holder.onIntent(ManageFactoriesIntent.RemoveConfirmed)
            holder.onIntent(ManageFactoriesIntent.RemoveConfirmed)
            advanceUntilIdle()
            env.biometrics.calls shouldBe 1
            holder.close()
        }

    @Test
    fun `removing a factory that vanished closes the confirmation`() =
        runTest {
            val env = Env(listOf(a))
            val holder = holder(env)
            advanceUntilIdle()
            holder.onIntent(ManageFactoriesIntent.RemoveRequested("a"))
            env.repository.delete(InstanceId("a"))

            holder.onIntent(ManageFactoriesIntent.RemoveConfirmed)
            advanceUntilIdle()

            holder.state.value.confirmation shouldBe null
            holder.state.value.isRemoving shouldBe false
        }

    @Test
    fun `every server key outcome has a key`() {
        RevokeOutcome.entries.map { it.toUi() } shouldBe ServerKeyOutcome.entries
        Role.entries.map { it.toUi() } shouldBe RoleUi.entries
        Action.entries.isNotEmpty() shouldBe true
    }
}
