package com.stainedhead.paperclipmobile.testing.demo

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.AskUserQuestions
import com.stainedhead.paperclipmobile.domain.Attachment
import com.stainedhead.paperclipmobile.domain.Comment
import com.stainedhead.paperclipmobile.domain.CommentAuthorType
import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.InteractionId
import com.stainedhead.paperclipmobile.domain.InteractionMeta
import com.stainedhead.paperclipmobile.domain.InteractionOption
import com.stainedhead.paperclipmobile.domain.InteractionStatus
import com.stainedhead.paperclipmobile.domain.Issue
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssuePriority
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.PlanDocument
import com.stainedhead.paperclipmobile.domain.Question
import com.stainedhead.paperclipmobile.domain.RequestConfirmation
import com.stainedhead.paperclipmobile.domain.ResolverAudience
import com.stainedhead.paperclipmobile.domain.ResolverPolicy
import com.stainedhead.paperclipmobile.domain.ResolverPolicySource
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** Work of the mocks: Billing v2 (PAP-1182) with four subtasks, and PAP-1204 "Add retries to webhook ingest". */
object DemoWork {
    private val now = DemoFactories.now
    private const val SMALL_FILE_BYTES = 4_096L
    private const val LOG_FILE_BYTES = 32_768L
    private val billing = IssueId(DemoIds.ISSUE_BILLING)
    private val retries = IssueId(DemoIds.ISSUE_RETRIES)

    private fun issue(
        id: String,
        title: String,
        status: IssueStatus,
        priority: IssuePriority,
        assignee: String?,
    ) = Issue(
        id = IssueId(id),
        identifier = id.uppercase(),
        title = title,
        status = status,
        priority = priority,
        assigneeAgentId = assignee?.let(::AgentId),
        assigneeUserId = null,
        projectId = "proj-billing",
        parentId = null,
        createdAt = now - 5.days,
        updatedAt = now - 1.hours,
    )

    private fun subtask(
        id: String,
        title: String,
        status: IssueStatus,
        priority: IssuePriority,
        assignee: String?,
    ) = issue(id, title, status, priority, assignee).copy(parentId = billing)

    fun issues(): List<Issue> =
        listOf(
            issue(DemoIds.ISSUE_BILLING, "Billing v2", IssueStatus.InProgress, IssuePriority.High, DemoIds.AGENT_CTO),
            subtask("pap-1183", "Migrate the invoices table", IssueStatus.InProgress, IssuePriority.High, null),
            subtask("pap-1184", "Backfill payments", IssueStatus.Todo, IssuePriority.Medium, DemoIds.AGENT_ENGINEER_2),
            subtask("pap-1185", "Switch the read path", IssueStatus.Backlog, IssuePriority.Medium, null),
            subtask("pap-1186", "Remove the legacy tables", IssueStatus.Backlog, IssuePriority.Low, null),
            issue(
                DemoIds.ISSUE_REFUNDS,
                "Refund path regression",
                IssueStatus.Todo,
                IssuePriority.High,
                DemoIds.AGENT_QA,
            ),
            issue(
                DemoIds.ISSUE_RETRIES,
                "Add retries to webhook ingest",
                IssueStatus.Blocked,
                IssuePriority.High,
                DemoIds.AGENT_ENGINEER_1,
            ),
            issue(
                DemoIds.ISSUE_EMAIL_DESIGN,
                "Refresh the invoice email template",
                IssueStatus.InProgress,
                IssuePriority.Medium,
                DemoIds.AGENT_DESIGNER,
            ),
            issue(
                "pap-1210",
                "Update the onboarding checklist",
                IssueStatus.InReview,
                IssuePriority.Low,
                DemoIds.AGENT_DESIGNER,
            ),
        )

    fun comments(): Map<IssueId, List<Comment>> =
        mapOf(
            retries to
                listOf(
                    Comment(
                        "c-1204-1",
                        "Ingest drops events when the receiver answers 503. I propose retrying with backoff.",
                        CommentAuthorType.Agent,
                        AgentId(DemoIds.AGENT_ENGINEER_1),
                        null,
                        now - 5.hours,
                    ),
                    Comment(
                        "c-1204-2",
                        "Sounds right. Tell me which policy you want.",
                        CommentAuthorType.User,
                        null,
                        DemoFactories.USER_ID,
                        now - 4.hours,
                    ),
                ),
        )

    fun plans(): Map<IssueId, PlanDocument> =
        mapOf(
            retries to
                PlanDocument(
                    "Retry plan",
                    "1. Add a retry queue\n2. Back off exponentially\n3. Dead-letter",
                    now - 4.hours,
                ),
            billing to PlanDocument("Billing v2 plan", "1. Migrate\n2. Backfill\n3. Switch traffic", now - 2.days),
        )

    fun attachments(): Map<IssueId, List<Attachment>> =
        mapOf(
            retries to
                listOf(
                    Attachment(
                        "att-1",
                        "retry-policy.md",
                        "text/markdown",
                        SMALL_FILE_BYTES,
                        now - 4.hours,
                        "/attachments/att-1",
                    ),
                    Attachment(
                        "att-2",
                        "ingest-errors.log",
                        "text/plain",
                        LOG_FILE_BYTES,
                        now - 3.hours,
                        "/attachments/att-2",
                    ),
                ),
        )

    private fun meta(
        id: String,
        title: String,
    ) = InteractionMeta(
        id = InteractionId(id),
        status = InteractionStatus.Pending,
        title = title,
        summary = null,
        createdAt = now - 3.hours,
        audience = ResolverAudience(ResolverPolicy.Anyone, ResolverPolicy.Anyone, ResolverPolicySource.Requested),
    )

    /** The two questions Engineer 1 asked on PAP-1204 (the retry policy question of the Inbox mock). */
    fun questions(): Interaction =
        AskUserQuestions(
            meta("int-1204-questions", "Engineer 1 asked 2 questions"),
            listOf(
                Question(
                    "q-policy",
                    "Which retry policy should webhook ingest use?",
                    multi = false,
                    required = true,
                    allowOther = false,
                    options =
                        listOf(
                            InteractionOption("exponential", "Exponential backoff"),
                            InteractionOption("fixed", "Fixed delay"),
                            InteractionOption("none", "No retries"),
                        ),
                ),
                Question(
                    "q-attempts",
                    "How many attempts before dead-lettering?",
                    multi = false,
                    required = true,
                    allowOther = true,
                    options = listOf(InteractionOption("3", "3"), InteractionOption("5", "5")),
                ),
            ),
        )

    fun confirmation(): Interaction =
        RequestConfirmation(
            meta("int-1182-confirm", "Switch traffic to the new billing service?"),
            "Switch traffic to the new billing service?",
        )

    fun interactionsByIssue(): Map<IssueId, List<Interaction>> = mapOf(retries to listOf(questions()))
}
