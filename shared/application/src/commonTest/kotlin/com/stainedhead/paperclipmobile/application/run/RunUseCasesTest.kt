package com.stainedhead.paperclipmobile.application.run

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.issue.LoadIssueRefs
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.LogTail
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.RunStatus
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.AgentFixtures
import com.stainedhead.paperclipmobile.testing.WorkFixtures
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private fun <T> DomainResult<T>.value(): T = (this as DomainResult.Success).value

private val live = RunId("run-live")

class LoadRunTest {
    @Test
    fun `FR-075 a run loads with its events and later reads only ask for newer events`() =
        runTest {
            val env = Env()

            val first = LoadRun(env.runs, LoadIssueRefs(env.issues))(env.scope, live, emptyList()).value()
            first.run.status shouldBe RunStatus.Running
            first.events.map { it.seq } shouldBe listOf(1L, 2L)

            val second = LoadRun(env.runs, LoadIssueRefs(env.issues))(env.scope, live, first.events).value()
            second.events.map { it.seq } shouldBe listOf(1L, 2L)
        }

    @Test
    fun `the run comes with the issue it works on and a missing issue is left out`() =
        runTest {
            val env = Env()

            LoadRun(
                env.runs,
                LoadIssueRefs(env.issues),
            )(env.scope, live, emptyList()).value().issue?.identifier shouldBe
                "PAP-1182"

            env.issues.failures["get"] = DomainError.ServerError
            LoadRun(env.runs, LoadIssueRefs(env.issues))(env.scope, live, emptyList()).value().issue shouldBe null
        }

    @Test
    fun `a run that is not found fails and a failing events read still shows the run`() =
        runTest {
            val env = Env()
            (
                LoadRun(
                    env.runs,
                    LoadIssueRefs(env.issues),
                )(env.scope, RunId("nope"), emptyList()) as DomainResult.Failure
            ).error shouldBe
                DomainError.NotFound

            env.runs.failures["events"] = DomainError.ServerError
            val view = LoadRun(env.runs, LoadIssueRefs(env.issues))(env.scope, live, emptyList()).value()
            view.run.id shouldBe live
            view.events shouldBe emptyList()
        }
}

class TailRunLogTest {
    @Test
    fun `FR-075 the tail catches up to the end and then waits for more`() =
        runTest {
            val env = Env()

            val tail = TailRunLog(env.runs)(env.scope, live, LogTail.Empty).value()
            tail.text shouldBe AgentFixtures.LOG
            tail.offset shouldBe AgentFixtures.LOG.length.toLong()

            env.runs.appendLog(live, "next line\n")
            val more = TailRunLog(env.runs)(env.scope, live, tail).value()
            more.text shouldBe AgentFixtures.LOG + "next line\n"
        }

    @Test
    fun `a long log is read in several chunks in one call`() =
        runTest {
            val env = Env()
            val small =
                com.stainedhead.paperclipmobile.testing.fakes
                    .FakeRunGateway(chunkSize = 10)

            val tail = TailRunLog(small)(env.scope, live, LogTail.Empty).value()

            tail.text shouldBe AgentFixtures.LOG
            small.count("log") shouldBe 6
        }

    @Test
    fun `a failure leaves the caller's tail untouched`() =
        runTest {
            val env = Env()
            env.runs.failures["log"] = DomainError.NotFound

            (TailRunLog(env.runs)(env.scope, live, LogTail.Empty) as DomainResult.Failure).error shouldBe
                DomainError.NotFound
        }
}

class CancelRunTest {
    private val run = AgentFixtures.runs().first()

    private fun Env.cancel() = CancelRun(runs, deviceAuth)

    @Test
    fun `FR-075 cancel asks for device authentication first and then calls the gateway once`() =
        runTest {
            val env = Env()

            val result = env.cancel()(env.context(Role.Operator), run)

            (result as MutationResult.Done).value.status shouldBe RunStatus.Cancelled
            env.biometrics.calls shouldBe 1
            env.runs.count("cancel") shouldBe 1
        }

    @Test
    fun `every device authentication failure means no gateway call`() =
        runTest {
            listOf(
                BiometricResult.Cancelled,
                BiometricResult.Failed,
                BiometricResult.LockedOut,
                BiometricResult.Unavailable,
            ).forEach { outcome ->
                val env = Env()
                env.biometrics.result = outcome

                env.cancel()(env.context(), run) shouldBe MutationResult.NotAuthorized(outcome)
                env.runs.count("cancel") shouldBe 0
            }
        }

    @Test
    fun `a viewer never reaches biometrics or the gateway and a finished run is refused without a prompt`() =
        runTest {
            val env = Env()

            env.cancel()(env.context(Role.Viewer), run) shouldBe MutationResult.Denied
            env.cancel()(env.context(), AgentFixtures.run("run-done", RunStatus.Succeeded)) shouldBe
                MutationResult.Invalid(InvalidInput.WrongStatus)
            env.biometrics.calls shouldBe 0
            env.runs.count("cancel") shouldBe 0
        }

    @Test
    fun `FR-096 a timeout is sent once and the run is read again`() =
        runTest {
            val env = Env()
            env.runs.failures["cancel"] = DomainError.Timeout

            val result = env.cancel()(env.context(), run)

            (result as MutationResult.Reconciled).current.id shouldBe live
            env.runs.count("cancel") shouldBe 1
            env.runs.count("get") shouldBe 1
        }

    @Test
    fun `a run that finished while the person decided is a conflict showing the final state`() =
        runTest {
            val env = Env()
            env.runs.store[live] =
                env.runs.store
                    .getValue(live)
                    .copy(status = RunStatus.Succeeded)

            val result = env.cancel()(env.context(), run)

            (result as MutationResult.Reconciled).error shouldBe DomainError.Conflict
            result.current.status shouldBe RunStatus.Succeeded
        }
}

class ListIssueRunsTest {
    @Test
    fun `FR-061 the runs of an issue are listed newest first as the server sends them`() =
        runTest {
            val env = Env()

            ListIssueRuns(env.runs)(env.scope, WorkFixtures.pap1182).value().map { it.id.value } shouldBe
                listOf("run-live", "run-done")
        }
}
