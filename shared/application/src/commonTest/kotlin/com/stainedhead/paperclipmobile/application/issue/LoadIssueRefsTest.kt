package com.stainedhead.paperclipmobile.application.issue

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IssueDetail
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.port.IssueGateway
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoadIssueRefsTest {
    @Test
    fun `known issues resolve once each to their identifier and title and the rest are left out`() =
        runTest {
            val env = Env()

            val refs =
                LoadIssueRefs(env.issues)(
                    env.scope,
                    listOf(IssueId("pap-1182"), null, IssueId("nope"), IssueId("pap-1182")),
                )

            refs.keys shouldBe setOf(IssueId("pap-1182"))
            refs.getValue(IssueId("pap-1182")).identifier shouldBe "PAP-1182"
            env.issues.count("get") shouldBe 2
        }

    @Test
    fun `a failing read leaves the ref out and never fails the screen`() =
        runTest {
            val env = Env()
            env.issues.failures["get"] = DomainError.ServerError

            LoadIssueRefs(env.issues)(env.scope, listOf(IssueId("pap-1182"))) shouldBe emptyMap()
        }

    @Test
    fun `nothing to resolve reads nothing`() =
        runTest {
            val env = Env()

            LoadIssueRefs(env.issues)(env.scope, emptyList()) shouldBe emptyMap()
            env.issues.count("get") shouldBe 0
        }

    /** Reads take time; counts how many are in flight at once. */
    private class Slow(
        private val inner: IssueGateway,
    ) : IssueGateway by inner {
        var inFlight = 0
        var peak = 0

        override suspend fun get(
            scope: CompanyScope,
            id: IssueId,
        ): DomainResult<IssueDetail> {
            inFlight++
            peak = maxOf(peak, inFlight)
            delay(100)
            inFlight--
            return inner.get(scope, id)
        }
    }

    @Test
    fun `A10 reads run side by side but never more than four at once`() =
        runTest {
            val env = Env()
            val slow = Slow(env.issues)
            val ids = listOf(IssueId("pap-1182")) + List(8) { IssueId("missing-$it") }

            LoadIssueRefs(slow)(env.scope, ids)

            slow.peak shouldBe 4
            currentTime shouldBe 300L
        }

    @Test
    fun `A10 at most 25 distinct issues are read and the rest are left out`() =
        runTest {
            val env = Env()
            val ids = List(40) { IssueId("missing-$it") }

            LoadIssueRefs(env.issues)(env.scope, ids)

            env.issues.count("get") shouldBe 25
        }
}
