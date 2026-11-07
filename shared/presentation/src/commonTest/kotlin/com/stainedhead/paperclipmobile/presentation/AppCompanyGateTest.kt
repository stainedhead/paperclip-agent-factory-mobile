@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.CompanyRef
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * F-1 (ADR-150): a factory without a company used to leave the company tabs on "Loading..." for good, because the app
 * made no request after pairing. The shell now settles the question and says so.
 */
class AppCompanyGateTest {
    private val acme = CompanyRef(TestData.acme, "Acme Labs")
    private val globex = CompanyRef(TestData.globex, "Globex")
    private val empty = TestData.instance("home", "Home factory").copy(defaultCompanyId = null, lastCompanyId = null)

    private fun TestScope.holder(env: Env): AppStateHolder {
        val useCases =
            AppUseCases(
                env.observeSettings,
                env.observeInstances,
                env.unlock,
                env.shouldLock,
                env.switchInstance,
                env.switchCompany,
                env.resolveActiveCompany,
            )
        return AppStateHolder(useCases, env.clock, CoroutineScope(StandardTestDispatcher(testScheduler)))
    }

    private fun TestScope.unlocked(env: Env): AppStateHolder {
        val holder = holder(env)
        advanceUntilIdle()
        holder.onIntent(AppIntent.UnlockRequested)
        advanceUntilIdle()
        return holder
    }

    private fun Env.noCompanies() {
        auth.companiesResult = DomainResult.Success(emptyList())
    }

    @Test
    fun `before the app is unlocked nothing is read and the gate is still checking`() =
        runTest {
            val env = Env(listOf(empty)).also { it.noCompanies() }

            val holder = holder(env)
            advanceUntilIdle()
            holder.onIntent(AppIntent.RecheckCompanies)
            advanceUntilIdle()

            env.auth.count("listCompanies") shouldBe 0
            holder.state.value.companyGate shouldBe CompanyGate.Checking
        }

    @Test
    fun `an unlocked app with no company reads the server once and says there is no company`() =
        runTest {
            val env = Env(listOf(empty)).also { it.noCompanies() }

            val holder = unlocked(env)

            env.auth.count("listCompanies") shouldBe 1
            holder.state.value.companyGate shouldBe CompanyGate.NoCompany
            holder.state.value.selectedCompanyId shouldBe null
            holder.state.value.companyError shouldBe null
        }

    @Test
    fun `a later change of the settings does not read the server again`() =
        runTest {
            val env = Env(listOf(empty)).also { it.noCompanies() }
            val holder = unlocked(env)

            holder.onIntent(AppIntent.WentToBackground)
            advanceUntilIdle()

            env.auth.count("listCompanies") shouldBe 1
        }

    @Test
    fun `checking again after a company was created selects it and opens the tabs`() =
        runTest {
            val env = Env(listOf(empty)).also { it.noCompanies() }
            val holder = unlocked(env)
            env.auth.companiesResult = DomainResult.Success(listOf(acme))

            holder.onIntent(AppIntent.RecheckCompanies)
            advanceUntilIdle()

            env.auth.count("listCompanies") shouldBe 2
            holder.state.value.selectedCompanyId shouldBe acme.id.value
            holder.state.value.companyGate shouldBe CompanyGate.Ready
        }

    @Test
    fun `coming back to the foreground checks again while there is still no company`() =
        runTest {
            val env = Env(listOf(empty)).also { it.noCompanies() }
            val holder = unlocked(env)
            env.auth.companiesResult = DomainResult.Success(listOf(acme))

            holder.onIntent(AppIntent.WentToBackground)
            holder.onIntent(AppIntent.CameToForeground)
            advanceUntilIdle()

            holder.state.value.selectedCompanyId shouldBe acme.id.value
            holder.state.value.companyGate shouldBe CompanyGate.Ready
        }

    @Test
    fun `several companies and none chosen asks the person to choose and picks nothing`() =
        runTest {
            val env = Env(listOf(empty))
            env.auth.companiesResult = DomainResult.Success(listOf(acme, globex))

            val holder = unlocked(env)

            holder.state.value.companyGate shouldBe CompanyGate.ChooseCompany
            holder.state.value.selectedCompanyId shouldBe null
        }

    @Test
    fun `a failed read shows the error and the next check recovers`() =
        runTest {
            val env = Env(listOf(empty))
            env.auth.companiesResult = DomainResult.Failure(DomainError.Forbidden)
            val holder = unlocked(env)

            holder.state.value.companyGate shouldBe CompanyGate.Failed
            holder.state.value.companyError shouldBe ErrorUi(ErrorKey.NoPermission)

            env.auth.companiesResult = DomainResult.Success(listOf(acme))
            holder.onIntent(AppIntent.RecheckCompanies)
            advanceUntilIdle()

            holder.state.value.companyGate shouldBe CompanyGate.Ready
            holder.state.value.companyError shouldBe null
        }

    @Test
    fun `a factory that has a company never reads its company list for the gate`() =
        runTest {
            val env = Env(listOf(TestData.instance("home", "Home factory")))

            val holder = unlocked(env)

            env.auth.count("listCompanies") shouldBe 0
            holder.state.value.companyGate shouldBe CompanyGate.Ready
        }

    @Test
    fun `choosing another factory with no company checks that factory`() =
        runTest {
            val other = TestData.instance("cloud", "Cloud", sortOrder = 1).copy(defaultCompanyId = null)
            val env = Env(listOf(TestData.instance("home", "Home factory"), other)).also { it.noCompanies() }
            val holder = unlocked(env)

            holder.onIntent(AppIntent.InstanceSelected("cloud"))
            advanceUntilIdle()

            env.auth.count("listCompanies") shouldBe 1
            holder.state.value.companyGate shouldBe CompanyGate.NoCompany
        }

    @Test
    fun `a factory that is not signed in yet is not asked until pairing has signed it in`() =
        runTest {
            val pairing = empty.copy(status = InstanceStatus.SignedOut)
            val env = Env(listOf(pairing)).also { it.noCompanies() }
            val holder = unlocked(env)

            env.auth.count("listCompanies") shouldBe 0
            holder.state.value.companyGate shouldBe CompanyGate.Checking

            env.repository.update(pairing.copy(status = InstanceStatus.SignedIn))
            advanceUntilIdle()

            env.auth.count("listCompanies") shouldBe 1
            holder.state.value.companyGate shouldBe CompanyGate.NoCompany
        }
}
