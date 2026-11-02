@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.application.approval.LoadApproval
import com.stainedhead.paperclipmobile.application.issue.LoadIssue
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.Comment
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IssueDetail
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.port.IssueGateway
import com.stainedhead.paperclipmobile.testing.WorkFixtures
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** F-A-11: retries stop when offline, never repeat a certificate failure, and are bounded per use case. */
class ReadRetryBudgetTest {
    @Test
    fun `load_approval_offline_fails_without_backoff`() =
        runTest {
            val env = Env()
            env.connectivity.setOnline(false)
            env.approvals.failures["get"] = DomainError.Unreachable

            val result = LoadApproval(env.approvals, env.connectivity)(env.scope, ApprovalId("ap-1"))

            result shouldBe DomainResult.Failure(DomainError.Unreachable)
            testScheduler.currentTime shouldBe 0
            env.approvals.count("get") shouldBe 1
        }

    @Test
    fun `certificate_failure_is_not_retried`() =
        runTest {
            val env = Env()
            env.approvals.failures["get"] = DomainError.CertificateInvalid

            val result = LoadApproval(env.approvals, env.connectivity)(env.scope, ApprovalId("ap-1"))

            result shouldBe DomainResult.Failure(DomainError.CertificateInvalid)
            env.approvals.count("get") shouldBe 1
            testScheduler.currentTime shouldBe 0
        }

    /** The issue read recovers on its third try; the comments never answer. */
    private class SlowThread(
        private val inner: IssueGateway,
    ) : IssueGateway by inner {
        var getCalls = 0

        override suspend fun get(
            scope: CompanyScope,
            id: IssueId,
        ): DomainResult<IssueDetail> {
            getCalls += 1
            return if (getCalls < 3) DomainResult.Failure(DomainError.Timeout) else inner.get(scope, id)
        }

        override suspend fun comments(
            scope: CompanyScope,
            id: IssueId,
        ): DomainResult<List<Comment>> = DomainResult.Failure(DomainError.Timeout)
    }

    @Test
    fun `multi_read_use_case_total_wait_is_bounded`() =
        runTest {
            val env = Env()

            val result =
                LoadIssue(SlowThread(env.issues), env.agents, env.connectivity)(env.scope, WorkFixtures.pap1182)

            (result is DomainResult.Failure) shouldBe true
            (testScheduler.currentTime <= 4_000) shouldBe true
        }

    @Test
    fun `a single read still retries with backoff when it is online`() =
        runTest {
            val env = Env()
            env.approvals.failures["get"] = DomainError.Timeout

            LoadApproval(env.approvals, env.connectivity)(env.scope, ApprovalId("ap-1"))

            env.approvals.count("get") shouldBe 4
            testScheduler.currentTime shouldBe 3_500
        }
}
