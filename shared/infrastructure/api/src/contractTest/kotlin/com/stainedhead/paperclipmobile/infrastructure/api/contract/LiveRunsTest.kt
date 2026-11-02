package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.InvokeOutcome
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.RunStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AD-050 (d) and F-C-2: heartbeat runs with content. A short `echo` run (started by assigning an issue to its agent)
 * leaves events and a log; a long `sleep` run is live, read and cancelled. Both are real processes of the server.
 */
class LiveRunsTest {
    private companion object {
        const val ECHO_TEXT = "contract-hello"
        const val SLEEP_SECONDS = "120"
        const val RUN_SECONDS = 90
        const val NOTE_CHARS = 160
        const val SHOWN_CHARS = 200
    }

    @Test
    fun `an assigned issue starts an echo run whose events, log and listings decode with content`() =
        live {
            val echo = seeder.agent(scope.companyId.value, "Contract Echo", "echo", listOf(ECHO_TEXT))
            val agent = AgentId(echo.id)
            val issue = issues.create(scope, NewIssue("Contract run issue", "Run echo")).verified("IssueGateway.create")
            issues.assign(scope, issue.id, Assignee.AgentAssignee(agent)).verified("IssueGateway.assign", "(wakes)")
            val run =
                eventually("a run for the assigned issue", RUN_SECONDS) {
                    runs.forIssue(scope, issue.id).verified("RunGateway.forIssue").firstOrNull()
                }
            assertEquals(agent, run.agentId)
            assertEquals(issue.id, run.issueId)
            val finished =
                eventually("the echo run to finish", RUN_SECONDS) {
                    runs.get(scope, run.id).verified("RunGateway.get", "(echo)").takeIf { !it.status.isActive }
                }
            LiveReport.note("echo run ended", "${finished.status} source=${finished.invocationSource}")
            assertEquals(agent, finished.agentId)
            val events =
                eventually("run events", RUN_SECONDS) {
                    runs.events(scope, run.id, 0).verified("RunGateway.events").takeIf { it.isNotEmpty() }
                }
            LiveReport.note("run events", "count=${events.size} first=${events.first().type}")
            assertTrue(events.all { it.seq > 0 }, "An event has no sequence number: $events")
            assertEquals(events.sortedBy { it.seq }.map { it.seq }, events.map { it.seq }.sorted())
            val chunk =
                eventually("run log text", RUN_SECONDS) {
                    runs.log(scope, run.id, 0).verified("RunGateway.log").takeIf { it.content.isNotBlank() }
                }
            LiveReport.note("run log", chunk.toString().take(NOTE_CHARS))
            assertTrue(
                chunk.content.contains(ECHO_TEXT),
                "The log lacks the echoed text: ${chunk.content.take(SHOWN_CHARS)}",
            )
            val forAgent = runs.forAgent(scope, agent).verified("RunGateway.forAgent")
            assertTrue(forAgent.any { it.id == run.id }, "The agent's run is missing from its list: $forAgent")
        }

    @Test
    fun `a woken sleep agent has a live run that is read and cancelled`() =
        live {
            val sleeper = seeder.agent(scope.companyId.value, "Contract Sleeper", "sleep", listOf(SLEEP_SECONDS))
            val agent = AgentId(sleeper.id)
            val outcome = agents.invokeHeartbeat(scope, agent).verified("AgentGateway.invokeHeartbeat")
            observe("invokeHeartbeat outcome") {
                check(outcome is InvokeOutcome.Started) { "A fresh agent should start a run, got $outcome" }
                outcome::class.simpleName.orEmpty()
            }
            val started = (outcome as InvokeOutcome.Started).run
            val read = runs.get(scope, started.id).verified("RunGateway.get")
            observe("run read") {
                check(read.agentId == agent) { "The run names another agent: ${read.agentId}" }
                "status=${read.status} source=${read.invocationSource}"
            }
            val live: List<RunId> =
                eventually("the run in the live list", RUN_SECONDS) {
                    runs
                        .live(scope)
                        .verified("RunGateway.live")
                        .map { it.id }
                        .takeIf { started.id in it }
                }
            assertTrue(started.id in live)
            val cancelled = runs.cancel(scope, started.id).verified("RunGateway.cancel")
            assertEquals(RunStatus.Cancelled, cancelled.status)
        }
}
