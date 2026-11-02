package com.stainedhead.paperclipmobile.application.issue

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.IssueId
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

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
}
