package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.auth.LoadInstanceAccess
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyRef
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** F-1: a factory without a company ends in a definite outcome, never in silence (ADR-150). */
class ResolveActiveCompanyTest {
    private val noCompany = TestData.instance().copy(defaultCompanyId = null, lastCompanyId = null)
    private val id: InstanceId = noCompany.id
    private val globex = CompanyRef(TestData.globex, "Globex")
    private val acme = CompanyRef(TestData.acme, "Acme Labs")

    private fun Env.resolve() =
        ResolveActiveCompany(
            repository,
            LoadInstanceAccess(repository, auth, signedOut, clock, connectivity, locks),
            SwitchCompany(repository, locks),
        )

    @Test
    fun `a factory with no companies yields NoCompany after one read of the server`() =
        runTest {
            val env = Env(listOf(noCompany))
            env.auth.companiesResult = DomainResult.Success(emptyList())

            env.resolve()(id) shouldBe ActiveCompanyOutcome.NoCompany

            env.auth.count("listCompanies") shouldBe 1
        }

    @Test
    fun `exactly one company becomes the active company and is stored`() =
        runTest {
            val env = Env(listOf(noCompany))
            env.auth.companiesResult = DomainResult.Success(listOf(acme))

            env.resolve()(id) shouldBe ActiveCompanyOutcome.Ready(acme.id)

            env.repository.get(id)?.activeCompanyId shouldBe acme.id
        }

    @Test
    fun `several companies and none active are never guessed`() =
        runTest {
            val env = Env(listOf(noCompany))
            env.auth.companiesResult = DomainResult.Success(listOf(acme, globex))

            env.resolve()(id) shouldBe ActiveCompanyOutcome.ChooseCompany(2)

            env.repository.get(id)?.activeCompanyId shouldBe null
        }

    @Test
    fun `an active company that still exists stays as it is`() =
        runTest {
            val env = Env(listOf(noCompany.copy(lastCompanyId = globex.id)))
            env.auth.companiesResult = DomainResult.Success(listOf(acme, globex))

            env.resolve()(id) shouldBe ActiveCompanyOutcome.Ready(globex.id)

            env.repository.get(id)?.activeCompanyId shouldBe globex.id
        }

    @Test
    fun `an active company that no longer exists is replaced only when one company is left`() =
        runTest {
            val env = Env(listOf(noCompany.copy(lastCompanyId = CompanyId("gone"))))
            env.auth.companiesResult = DomainResult.Success(listOf(acme))

            env.resolve()(id) shouldBe ActiveCompanyOutcome.Ready(acme.id)
        }

    @Test
    fun `a failed read is reported with its error and changes nothing`() =
        runTest {
            val env = Env(listOf(noCompany))
            env.auth.companiesResult = DomainResult.Failure(DomainError.Forbidden)

            env.resolve()(id) shouldBe ActiveCompanyOutcome.Failed(DomainError.Forbidden)

            env.repository.get(id)?.activeCompanyId shouldBe null
        }

    @Test
    fun `an unknown factory is NotFound`() =
        runTest {
            val env = Env(emptyList())

            env.resolve()(InstanceId("nope")) shouldBe ActiveCompanyOutcome.Failed(DomainError.NotFound)
        }
}
