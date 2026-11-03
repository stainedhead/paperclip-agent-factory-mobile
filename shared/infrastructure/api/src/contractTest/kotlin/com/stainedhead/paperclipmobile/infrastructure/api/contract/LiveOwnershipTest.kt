package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.HeartbeatRun
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.InteractionId
import com.stainedhead.paperclipmobile.domain.InvokeOutcome
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.QuestionAnswer
import com.stainedhead.paperclipmobile.domain.RunId
import kotlinx.serialization.json.JsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * F-C-9, AD-014 and ADR-061 on the failure paths, against the real server. The board key spans every company of its
 * user, so the SERVER lets it read and write a second company's resources: it is the adapter that must refuse them
 * under the first company's scope. Each refusal is NotFound, and a raw read afterwards shows nothing was changed. A
 * positive control through the second company's own scope proves the resources exist and are reachable.
 */
class LiveOwnershipTest {
    private companion object {
        const val SLEEP_SECONDS = "120"
    }

    /** A second company with an issue, an approval, an agent with a live run, a question and an attachment. */
    private class Foreign(
        val scope: CompanyScope,
        val issue: IssueId,
        val approval: ApprovalId,
        val agent: AgentId,
        val run: RunId,
        val question: InteractionId,
    )

    private suspend fun LiveContext.foreign(): Foreign {
        val other = seeder.company("Contract Other Co")
        val otherScope = CompanyScope(scope.instanceId, CompanyId(other.id))
        val issue = seeder.issue(other.id, "Foreign issue")
        val approval = seeder.approval(other.id, "Foreign approval")
        val agent = AgentId(seeder.agent(other.id, "Foreign Sleeper", "sleep", listOf(SLEEP_SECONDS)).id)
        val question = seeder.question(issue.id)
        seeder.attachment(other.id, issue.id, "foreign.txt", "foreign")
        val started = agents.invokeHeartbeat(otherScope, agent).verified("AgentGateway.invokeHeartbeat", "(second)")
        val world =
            Foreign(
                otherScope,
                IssueId(issue.id),
                ApprovalId(approval.id),
                agent,
                (started as InvokeOutcome.Started).run.id,
                InteractionId(question),
            )
        // Positive control: the second company's own scope reaches all of it.
        issues.get(otherScope, world.issue).verified("IssueGateway.get", "(second company)")
        approvals.get(otherScope, world.approval).verified("ApprovalGateway.get", "(second company)")
        agents.get(otherScope, agent).verified("AgentGateway.get", "(second company)")
        runs.get(otherScope, world.run).verified("RunGateway.get", "(second company)")
        return world
    }

    @Test
    fun `reads of another company's resources are NotFound through the first company's scope`() =
        live {
            val other = foreign()
            issues.get(scope, other.issue).verifiedError("IssueGateway.get", DomainError.NotFound)
            issues.comments(scope, other.issue).verifiedError("IssueGateway.comments", DomainError.NotFound)
            issues.attachments(scope, other.issue).verifiedError("IssueGateway.attachments", DomainError.NotFound)
            interactions.list(scope, other.issue).verifiedError("InteractionGateway.list", DomainError.NotFound)
            approvals.get(scope, other.approval).verifiedError("ApprovalGateway.get", DomainError.NotFound)
            agents.get(scope, other.agent).verifiedError("AgentGateway.get", DomainError.NotFound)
            runs.get(scope, other.run).verifiedError("RunGateway.get", DomainError.NotFound)
            runs.events(scope, other.run, 0).verifiedError("RunGateway.events", DomainError.NotFound)
            // The list is company scoped in its path, so the server itself answers an empty list for a foreign agent:
            // nothing of the other company is returned (observed live in run 41531193390).
            assertEquals(DomainResult.Success(emptyList<HeartbeatRun>()), runs.forAgent(scope, other.agent))
            runs.cancel(other.scope, other.run).verified("RunGateway.cancel", "(second company, cleanup)")
        }

    @Test
    fun `writes to another company's resources are NotFound and change nothing`() =
        live {
            val other = foreign()
            issues
                .addComment(scope, other.issue, "must not land", CommentOptions())
                .verifiedError("IssueGateway.addComment", DomainError.NotFound)
            issues
                .setStatus(scope, other.issue, IssueStatus.Done)
                .verifiedError("IssueGateway.setStatus", DomainError.NotFound)
            approvals
                .approve(scope, other.approval, "must not land")
                .verifiedError("ApprovalGateway.approve", DomainError.NotFound)
            agents.pause(scope, other.agent).verifiedError("AgentGateway.pause", DomainError.NotFound)
            runs.cancel(scope, other.run).verifiedError("RunGateway.cancel", DomainError.NotFound)
            interactions
                .answer(
                    scope,
                    other.issue,
                    other.question,
                    InteractionAnswer.Respond(listOf(QuestionAnswer("go", listOf("yes")))),
                ).verifiedError("InteractionGateway.answer", DomainError.NotFound)

            val comments = raw.call("GET", "/api/issues/${other.issue.value}/comments", bearer = key)
            assertEquals(0, (comments.json as? JsonArray)?.size, "A comment landed: ${comments.describe()}")
            assertEquals("todo", raw.call("GET", "/api/issues/${other.issue.value}", bearer = key).string("status"))
            val approval = raw.call("GET", "/api/approvals/${other.approval.value}", bearer = key)
            assertEquals("pending", approval.string("status"))
            val agent = raw.call("GET", "/api/agents/${other.agent.value}", bearer = key)
            assertNotEquals("paused", agent.string("status"))
            val run = raw.call("GET", "/api/heartbeat-runs/${other.run.value}", bearer = key)
            assertNotEquals("cancelled", run.string("status"))
            val question = interactions.list(other.scope, other.issue).verified("InteractionGateway.list", "(second)")
            assertTrue(
                question
                    .single()
                    .meta.status.isOpen,
                "The foreign question was answered",
            )
            assertEquals(
                ApprovalStatus.Pending,
                approvals.get(other.scope, other.approval).verified("ApprovalGateway.get", "(after)").status,
            )
            runs.cancel(other.scope, other.run).verified("RunGateway.cancel", "(second company, cleanup)")
        }
}
