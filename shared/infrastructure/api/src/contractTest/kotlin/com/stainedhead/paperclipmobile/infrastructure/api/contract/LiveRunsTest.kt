package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.AgentId
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
    /** Like [eventually] but gives up quietly, so one run reports every missing piece (the verdict then fails). */
    private suspend fun <T : Any> patiently(
        what: String,
        step: suspend () -> T?,
    ): T? = runCatching { eventually(what, PATIENCE_SECONDS, step) }.getOrNull()

    private companion object {
        const val PATIENCE_SECONDS = 20
        const val ECHO_TEXT = "contract-hello"
        const val SLEEP_SECONDS = "120"
        const val RUN_SECONDS = 90
        const val NOTE_CHARS = 160
        const val SHOWN_CHARS = 200
    }

    @Test
    fun `an echo run linked to an issue has events, a log and listings that decode with content`() =
        live {
            val echo = seeder.agent(scope.companyId.value, "Contract Echo", "echo", listOf(ECHO_TEXT))
            val agent = AgentId(echo.id)
            val issue = issues.create(scope, NewIssue("Contract run issue", "Run echo")).verified("IssueGateway.create")
            val runId = RunId(seeder.wake(echo.id, issue.id.value))
            val linked =
                eventually("the run in the issue's run list", RUN_SECONDS) {
                    runs.forIssue(scope, issue.id).verified("RunGateway.forIssue").firstOrNull { it.id == runId }
                }
            assertEquals(agent, linked.agentId)
            val finished =
                eventually("the echo run to end", RUN_SECONDS) {
                    runs.get(scope, runId).verified("RunGateway.get", "(echo)").takeIf { !it.status.isActive }
                }
            LiveReport.note(
                "echo run ended",
                "${finished.status} source=${finished.invocationSource} error=${finished.error}",
            )
            val events =
                patiently("events") { runs.events(scope, runId, 0).verified("RunGateway.events").ifEmpty { null } }
            LiveReport.note("run events", events?.let { "count=${it.size} first=${it.first().type}" } ?: "none arrived")
            val chunk = patiently("log") { runs.log(scope, runId, 0).verified("RunGateway.log").takeIf { hasData(it) } }
            LiveReport.note("run log", chunk?.toString()?.take(NOTE_CHARS) ?: "none arrived")
            runs.forAgent(scope, agent).verified("RunGateway.forAgent").also {
                assertTrue(it.any { run -> run.id == runId }, "The agent's run is missing from its list: $it")
            }
            assertTrue(events != null && events.all { it.seq > 0 }, "No run events arrived for the echo run")
            assertTrue(chunk?.content?.contains(ECHO_TEXT) == true, "The log lacks the echoed text: $chunk")
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
