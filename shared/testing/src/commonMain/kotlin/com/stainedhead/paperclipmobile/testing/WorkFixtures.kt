package com.stainedhead.paperclipmobile.testing

import com.stainedhead.paperclipmobile.domain.Agent
import com.stainedhead.paperclipmobile.domain.AgentCounts
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.AgentStatus
import com.stainedhead.paperclipmobile.domain.AskUserQuestions
import com.stainedhead.paperclipmobile.domain.Attachment
import com.stainedhead.paperclipmobile.domain.Comment
import com.stainedhead.paperclipmobile.domain.CommentAuthorType
import com.stainedhead.paperclipmobile.domain.DashboardSummary
import com.stainedhead.paperclipmobile.domain.HeartbeatRun
import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.InteractionId
import com.stainedhead.paperclipmobile.domain.InteractionMeta
import com.stainedhead.paperclipmobile.domain.InteractionOption
import com.stainedhead.paperclipmobile.domain.InteractionStatus
import com.stainedhead.paperclipmobile.domain.Issue
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssuePriority
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.PauseReason
import com.stainedhead.paperclipmobile.domain.PlanDocument
import com.stainedhead.paperclipmobile.domain.Question
import com.stainedhead.paperclipmobile.domain.RequestConfirmation
import com.stainedhead.paperclipmobile.domain.ResolverAudience
import com.stainedhead.paperclipmobile.domain.ResolverPolicy
import com.stainedhead.paperclipmobile.domain.ResolverPolicySource
import com.stainedhead.paperclipmobile.domain.RunEvent
import com.stainedhead.paperclipmobile.domain.RunEventLevel
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.RunStatus
import com.stainedhead.paperclipmobile.domain.RunStream
import com.stainedhead.paperclipmobile.domain.SpendRow
import com.stainedhead.paperclipmobile.domain.TaskCounts

/** Work, agent and run data that matches the design mocks (Acme Labs, PAP-1182, Engineer 1). */
object WorkFixtures {
    const val ME = "user-1"
    val pap1182 = IssueId("pap-1182")
    val engineer1 = AgentId("agent-eng-1")
    val engineer2 = AgentId("agent-eng-2")

    fun issue(
        id: String,
        title: String,
        status: IssueStatus = IssueStatus.Todo,
        priority: IssuePriority = IssuePriority.Medium,
        parentId: IssueId? = null,
    ) = Issue(
        id = IssueId(id),
        identifier = id.uppercase(),
        title = title,
        status = status,
        priority = priority,
        assigneeAgentId = null,
        assigneeUserId = null,
        projectId = "proj-billing",
        parentId = parentId,
        createdAt = TestData.epoch,
        updatedAt = TestData.epoch,
    )

    fun issues(): List<Issue> =
        listOf(
            issue("pap-1182", "Ship the billing migration", IssueStatus.InProgress, IssuePriority.High)
                .copy(assigneeAgentId = engineer1),
            issue("pap-1190", "Backfill invoices", IssueStatus.Todo, IssuePriority.Critical, pap1182)
                .copy(assigneeAgentId = engineer2),
            issue("pap-1191", "Update the runbook", IssueStatus.Backlog, IssuePriority.Low, pap1182),
            issue("pap-1150", "Review pricing page", IssueStatus.InReview, IssuePriority.Medium)
                .copy(assigneeUserId = ME),
            issue("pap-1160", "Waiting for vendor keys", IssueStatus.Blocked, IssuePriority.High)
                .copy(assigneeAgentId = engineer2),
            issue("pap-1100", "Old cleanup", IssueStatus.Done, IssuePriority.Low),
        )

    private const val SMALL_FILE_BYTES = 2_048L
    private const val IMAGE_BYTES = 90_000L

    fun plan() = PlanDocument("Plan", "1. Migrate the schema\n2. Backfill\n3. Switch traffic", TestData.epoch)

    fun comments(): List<Comment> =
        listOf(
            Comment("c1", "Starting the migration.", CommentAuthorType.Agent, engineer1, null, TestData.epoch),
            Comment("c2", "Go ahead.", CommentAuthorType.User, null, ME, TestData.epoch),
        )

    fun attachments() =
        listOf(
            Attachment(
                "att-1",
                "migration.sql",
                "text/plain",
                SMALL_FILE_BYTES,
                TestData.epoch,
                "/api/attachments/att-1/content",
            ),
            Attachment("att-2", null, "image/png", IMAGE_BYTES, TestData.epoch, "/api/attachments/att-2/content"),
        )

    fun meta(
        id: String,
        status: InteractionStatus = InteractionStatus.Pending,
        addresseeUserId: String? = null,
        blocked: Boolean = false,
    ) = InteractionMeta(
        id = InteractionId(id),
        status = status,
        title = null,
        summary = null,
        createdAt = TestData.epoch,
        audience =
            ResolverAudience(
                ResolverPolicy.Anyone,
                ResolverPolicy.Anyone,
                ResolverPolicySource.Requested,
                addresseeUserId = addresseeUserId,
            ),
        acceptanceBlocked = blocked,
    )

    fun interactions(): List<Interaction> =
        listOf(
            RequestConfirmation(
                meta("int-confirm"),
                "Switch traffic to the new billing service?",
                rejectRequiresReason = true,
            ),
            AskUserQuestions(
                meta("int-questions", InteractionStatus.Answered),
                listOf(
                    Question(
                        "q1",
                        "Which region first?",
                        multi = false,
                        required = true,
                        allowOther = false,
                        options = listOf(InteractionOption("eu", "EU"), InteractionOption("us", "US")),
                    ),
                ),
            ),
        )
}
