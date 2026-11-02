package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.application.auth.LoadInstanceAccess
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ResolveCompanyContextTest {
    private val instance = TestData.instance()

    private fun Env.resolve() =
        ResolveCompanyContext(LoadInstanceAccess(repository, auth, signedOut, clock, connectivity))

    @Test
    fun `the person's role and user id in the chosen company become the context`() =
        runTest {
            val env = Env(listOf(instance))
            env.auth.meResult = DomainResult.Success(TestData.access(Role.Operator))

            val result = env.resolve()(env.scope)

            result shouldBe DomainResult.Success(CompanyContext(env.scope, Role.Operator, "user-1"))
        }

    @Test
    fun `a company the key cannot open is forbidden`() =
        runTest {
            val env = Env(listOf(instance))

            val result = env.resolve()(env.scope.copy(companyId = CompanyId("someone-elses")))

            (result as DomainResult.Failure).error shouldBe DomainError.Forbidden
        }

    @Test
    fun `a failed access read is passed on`() =
        runTest {
            val env = Env(listOf(instance))
            env.auth.meResult = DomainResult.Failure(DomainError.Unauthorized)

            (env.resolve()(env.scope) as DomainResult.Failure).error shouldBe DomainError.Unauthorized
        }
}
